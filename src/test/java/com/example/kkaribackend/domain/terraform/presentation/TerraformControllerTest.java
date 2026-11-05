package com.example.kkaribackend.domain.terraform.presentation;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

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
class TerraformControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    void 업로드하면_필요한_속성만_읽은_리소스_구조를_돌려준다() throws Exception {
        upload(TerraformStateParserTest.STATE)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resources.ec2[0].instanceId").value("i-0abc"))
            .andExpect(jsonPath("$.resources.rds[0].dbInstanceId").value("prod-db"))
            .andExpect(jsonPath("$.missing[0].target").value("STRUCTURE TERRAFORM"))
            .andExpect(jsonPath("$.uploadedAt").exists())
            .andExpect(jsonPath("$.cost").doesNotExist())
            .andExpect(content().string(not(containsString(TerraformStateParserTest.SECRET))));
    }

    @Test
    void 형식이_틀리면_400이고_이전_결과는_그대로다() throws Exception {
        upload(TerraformStateParserTest.STATE).andExpect(status().isOk());

        upload("{\"version\":4}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TF001"));

        Project stored = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(stored.getTerraform().resources().ec2()).extracting(Ec2Instance::instanceId).containsExactly("i-0abc");
    }

    @Test
    void 담당하지_않는_프로젝트는_403() throws Exception {
        mockMvc.perform(multipart("/v1/projects/{id}/terraform", project.getId())
                .file(file(TerraformStateParserTest.STATE))
                .with(as(saveUser())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void discovery는_AWS_결과와_terraform을_나란히_돌려준다() throws Exception {
        upload(TerraformStateParserTest.STATE).andExpect(status().isOk());
        // AWS 분석 결과가 기본이라 없으면 terraform 이 있어도 404
        mockMvc.perform(get("/v1/projects/{id}/discovery", project.getId()).with(as(user)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AN003"));

        AnalysisJob job = AnalysisJob.builder().project(project).build();
        job.succeed(objectMapper.writeValueAsString(InfraSnapshot.builder()
            .resources(Resources.builder()
                .ec2(List.of(new Ec2Instance("i-0abc", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a")))
                .build())
            .missing(List.of())
            .build()));
        analysisJobRepository.save(job);

        mockMvc.perform(get("/v1/projects/{id}/discovery", project.getId()).with(as(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.resources.ec2[0].instanceId").value("i-0abc"))
            .andExpect(jsonPath("$.terraform.resources.ec2[0].instanceId").value("i-0abc"))
            .andExpect(jsonPath("$.terraform.resources.rds[0].dbInstanceId").value("prod-db"))
            .andExpect(content().string(not(containsString(TerraformStateParserTest.SECRET))));
    }

    private ResultActions upload(String content) throws Exception {
        return mockMvc.perform(multipart("/v1/projects/{id}/terraform", project.getId())
            .file(file(content))
            .with(as(user)));
    }

    private User saveUser() {
        return userRepository.save(User.builder().email(UUID.randomUUID() + "@kkari.com").password("pw")
            .name("엔지니어").companyName("MSP").role(Role.MSP_ENGINEER).build());
    }

    private static RequestPostProcessor as(User user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            new CustomUserDetails(user.getId(), Role.MSP_ENGINEER), null, List.of()));
    }

    private static MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "terraform.tfstate", "application/json",
            content.getBytes(StandardCharsets.UTF_8));
    }
}
