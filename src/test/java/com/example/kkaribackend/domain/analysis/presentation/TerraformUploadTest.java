package com.example.kkaribackend.domain.analysis.presentation;

import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import com.example.kkaribackend.global.terraform.TerraformStateParserTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TerraformUploadTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        user = saveUser();
        project = projectRepository.save(Project.builder().user(user).companyName("고객사").contactName("담당").build());
    }

    // 작업이 프로젝트를, 프로젝트가 유저를 참조하므로 이 순서로 지운다
    @AfterEach
    void tearDown() {
        analysisJobRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 업로드하면_바로_결과를_돌려주고_discovery로도_조회된다() throws Exception {
        mockMvc.perform(multipart("/v1/projects/{id}/terraform", project.getId())
                .file(file("terraform.tfstate", TerraformStateParserTest.STATE))
                .with(as(user)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jobId").isNumber())
            .andExpect(jsonPath("$.result.source").value("TERRAFORM"))
            .andExpect(jsonPath("$.result.resources.ec2[0].instanceId").value("i-0abc"))
            .andExpect(jsonPath("$.result.resources.rds[0].dbInstanceId").value("prod-db"))
            .andExpect(jsonPath("$.result.usage").doesNotExist())
            .andExpect(content().string(not(containsString(TerraformStateParserTest.SECRET))));

        mockMvc.perform(get("/v1/projects/{id}/discovery", project.getId()).with(as(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.source").value("TERRAFORM"))
            .andExpect(content().string(not(containsString(TerraformStateParserTest.SECRET))));
    }

    @Test
    void 형식이_틀리면_400이고_저장하지_않는다() throws Exception {
        long before = analysisJobRepository.count();

        mockMvc.perform(multipart("/v1/projects/{id}/terraform", project.getId())
                .file(file("terraform.tfstate", "{\"version\":4}"))
                .with(as(user)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TF001"));

        assertThat(analysisJobRepository.count()).isEqualTo(before);
    }

    @Test
    void 담당하지_않는_프로젝트는_403() throws Exception {
        mockMvc.perform(multipart("/v1/projects/{id}/terraform", project.getId())
                .file(file("terraform.tfstate", TerraformStateParserTest.STATE))
                .with(as(saveUser())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P002"));
    }

    private User saveUser() {
        return userRepository.save(User.builder().email(UUID.randomUUID() + "@kkari.com").password("pw")
            .name("엔지니어").companyName("MSP").role(Role.MSP_ENGINEER).build());
    }

    private static RequestPostProcessor as(User user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            new CustomUserDetails(user.getId(), Role.MSP_ENGINEER), null, List.of()));
    }

    private static MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/json", content.getBytes(StandardCharsets.UTF_8));
    }
}
