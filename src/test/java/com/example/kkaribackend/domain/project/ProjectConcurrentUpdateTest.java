package com.example.kkaribackend.domain.project;

import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.dto.IntakeProfile;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectConcurrentUpdateTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ChatMessageRepository messages;
    @Autowired private AnalysisJobRepository jobs;
    @MockitoSpyBean private ProjectRepository projects;
    @MockitoBean private AiServerClient ai;

    private Project project;
    private RequestPostProcessor owner;
    private final AtomicBoolean aiResponded = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        User user = users.saveAndFlush(User.builder().email("concurrent-project@a.com").password("hash")
            .name("엔지니어").companyName("MSP").role(Role.MSP_ENGINEER).build());
        project = projects.saveAndFlush(Project.builder().user(user).companyName("이전 고객사").contactName("담당")
            .requirements(Requirements.builder().content("이전 요구사항").build()).build());
        var principal = new CustomUserDetails(user.getId(), user.getRole());
        owner = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(ai.intake(any())).thenAnswer(invocation -> {
            aiResponded.set(true);
            return IntakeResponseDto.builder().reply("응답").quickReplies(List.of()).missing(List.of())
                .missingFields(List.of()).profile(IntakeProfile.builder().companyName("새 고객사").build()).build();
        });
    }

    @AfterEach
    void cleanup() {
        messages.deleteAll();
        jobs.deleteAll();
        projects.deleteAll();
        users.deleteAll();
    }

    @Test
    void 상담이_프로젝트를_읽은_뒤_완료된_요구사항_저장을_보존한다() throws Exception {
        CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
        pauseAfterRead("chat-after-read", true, read, resume);
        var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "chat-after-read"));
        try {
            var chat = executor.submit(() -> sendChat());
            assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
            saveRequirements();
            assertThat(projects.findById(project.getId()).orElseThrow().getRequirements().content()).isEqualTo("새 요구사항");
            resume.countDown();
            assertThat(chat.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            assertBothChangesSurvive();
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 요구사항이_프로젝트를_읽은_뒤_완료된_고객사명_저장을_보존한다() throws Exception {
        CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
        pauseAfterRead("requirements-after-read", false, read, resume);
        var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "requirements-after-read"));
        try {
            var requirements = executor.submit(() -> { saveRequirements(); return true; });
            assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(sendChat()).isEqualTo(200);
            assertThat(projects.findById(project.getId()).orElseThrow().getCompanyName()).isEqualTo("새 고객사");
            resume.countDown();
            assertThat(requirements.get(15, TimeUnit.SECONDS)).isTrue();
            assertBothChangesSurvive();
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    private void pauseAfterRead(String thread, boolean afterAi, CountDownLatch read, CountDownLatch resume) {
        AtomicBoolean paused = new AtomicBoolean();
        doAnswer(invocation -> {
            Object result = mockingDetails(projects).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            if (Thread.currentThread().getName().equals(thread) && (!afterAi || aiResponded.get())
                && paused.compareAndSet(false, true)) {
                read.countDown();
                assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(projects).findById(project.getId());
    }

    private int sendChat() throws Exception {
        return mvc.perform(post("/v1/projects/" + project.getId() + "/chat/messages").with(owner)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    private void saveRequirements() throws Exception {
        mvc.perform(put("/v1/projects/" + project.getId() + "/requirements").with(owner)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"content":"새 요구사항","priorities":["COST"],"containsPersonalData":true}
                """)).andExpect(status().isOk());
    }

    private void assertBothChangesSurvive() {
        Project stored = projects.findById(project.getId()).orElseThrow();
        assertThat(stored.getCompanyName()).isEqualTo("새 고객사");
        assertThat(stored.getRequirements().content()).isEqualTo("새 요구사항");
        assertThat(stored.getRequirements().containsPersonalData()).isTrue();
    }
}
