package com.example.kkaribackend.domain.project.presentation;

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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;

    @AfterEach
    void tearDown() {
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 고객사명과_담당자만으로_프로젝트를_만들면_RECEIVED로_시작한다() throws Exception {
        User user = userRepository.save(User.builder()
            .email("engineer@kkari.com").password("x").name("엔지니어").companyName("까리")
            .role(Role.MSP_ENGINEER).build());
        CustomUserDetails principal = new CustomUserDetails(user.getId(), Role.MSP_ENGINEER);
        RequestPostProcessor owner = authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        mockMvc.perform(post("/v1/projects").with(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"companyName":"고객사","contactName":"담당자"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.projectId").isNumber());

        mockMvc.perform(get("/v1/projects").with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].companyName").value("고객사"))
            .andExpect(jsonPath("$[0].stage").value("RECEIVED"));
    }
}
