package com.example.kkaribackend.domain.auth;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    private static final String SIGN_UP_BODY = """
        {"email":"%s","password":"password1","name":"홍길동","companyName":"고객사"}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void 고객_가입과_로그인은_CUSTOMER_역할을_돌려준다() throws Exception {
        mockMvc.perform(post("/v1/auth/signup/customer").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("customer@kkari.com")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"customer@kkari.com","password":"password1"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER"));

        assertThat(userRepository.findByEmail("customer@kkari.com").orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void 엔지니어_가입과_로그인은_MSP_ENGINEER_역할을_돌려준다() throws Exception {
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("engineer@kkari.com")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("MSP_ENGINEER"));

        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"engineer@kkari.com","password":"password1"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MSP_ENGINEER"));
    }
}
