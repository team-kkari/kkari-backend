package com.example.kkaribackend.domain.architecture;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureQueryConcurrencyTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ProjectRepository projects;
    @Autowired private ChatMessageRepository messages;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private AnalysisJobRepository jobs;

    @AfterEach
    void cleanup() {
        messages.deleteAll();
        jobs.deleteAll();
        projects.deleteAll();
        users.deleteAll();
    }

    @Test
    void 두_조회_사이에_설계가_완료되어도_성공_결과와_완료_상태를_돌려준다() throws Exception {
        User user = users.saveAndFlush(User.builder().email("architecture-race@a.com").password("hash")
            .name("엔지니어").companyName("MSP").role(Role.MSP_ENGINEER).build());
        Project project = projects.saveAndFlush(Project.builder().user(user).companyName("고객사").contactName("담당").build());
        AnalysisJob job = AnalysisJob.builder().project(project).type(AnalysisJobType.ARCHITECTURE).build();
        job.start();
        jobs.saveAndFlush(job);
        CountDownLatch latestRead = new CountDownLatch(1), resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object latest = mockingDetails(jobs).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            latestRead.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return latest;
        }).when(jobs).findFirstByProjectIdAndTypeOrderByIdDesc(project.getId(), AnalysisJobType.ARCHITECTURE);
        var principal = new CustomUserDetails(user.getId(), user.getRole());
        var auth = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var response = executor.submit(() -> mvc.perform(get("/v1/projects/" + project.getId() + "/architectures")
                .with(auth)).andReturn().getResponse());
            assertThat(latestRead.await(10, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactions).executeWithoutResult(status ->
                jobs.findById(job.getId()).orElseThrow().succeed("{\"overall\":\"경합 중 완료\"}"));
            assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.SUCCEEDED);
            resume.countDown();
            var actual = response.get(15, TimeUnit.SECONDS);
            assertThat(actual.getStatus()).isEqualTo(200);
            assertThat((String) com.jayway.jsonpath.JsonPath.read(actual.getContentAsString(), "$.result.overall"))
                .isEqualTo("경합 중 완료");
            assertThat((String) com.jayway.jsonpath.JsonPath.read(actual.getContentAsString(), "$.latestJob.status"))
                .isEqualTo("SUCCEEDED");
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }
}
