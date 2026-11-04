package com.example.kkaribackend.domain.requirement;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequirementControllerTest {

    private static final String VALID_BODY = """
        {"content":"트래픽 급증에도 버티는 구조","priorities":["COST","SECURITY","COST"],
         "desiredArchitecture":"EKS","monthlyBudget":1500.50,"currency":"USD",
         "containsPersonalData":true,"frozenResources":["prod-rds"]}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;

    private String url;
    private RequestPostProcessor owner;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
            .email("engineer@kkari.com").password("x").name("엔지니어").companyName("까리")
            .role(Role.MSP_ENGINEER).build());
        Project project = projectRepository.save(Project.builder()
            .user(user).companyName("고객사").contactName("담당자").build());

        CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getRole());
        owner = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        url = "/v1/projects/" + project.getId() + "/requirements";
    }

    @AfterEach
    void tearDown() {
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 저장한_요구사항을_그대로_조회한다() throws Exception {
        mockMvc.perform(get(url).with(owner))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R001"));

        mockMvc.perform(put(url).with(owner).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isOk());

        mockMvc.perform(get(url).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("트래픽 급증에도 버티는 구조"))
            .andExpect(jsonPath("$.priorities.length()").value(2))
            .andExpect(jsonPath("$.priorities[1]").value("SECURITY"))
            .andExpect(jsonPath("$.monthlyBudget").value(1500.50))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.containsPersonalData").value(true))
            .andExpect(jsonPath("$.frozenResources[0]").value("prod-rds"));
    }

    @Test
    void 예산이_음수면_400() throws Exception {
        mockMvc.perform(put(url).with(owner).contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY.replace("1500.50", "-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("monthlyBudget"));
    }

    @Test
    void 예산만_있고_통화가_없으면_400() throws Exception {
        mockMvc.perform(put(url).with(owner).contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY.replace("\"currency\":\"USD\",", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("currencyGivenWithBudget"));
    }

    @Test
    void 정해지지_않은_우선순위면_400() throws Exception {
        mockMvc.perform(put(url).with(owner).contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY.replace("\"SECURITY\"", "\"SPEED\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }
}
