package com.example.kkaribackend.domain.auth;

import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class SignUpConcurrencyTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @MockitoSpyBean private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void 두_요청이_동시에_중복_검사를_통과해도_한_건은_409_U002다() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> signup("Race@A.com"));
            var second = executor.submit(() -> signup("  race@a.com  "));
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(responses).extracting(response -> response.getStatus()).containsExactlyInAnyOrder(201, 409);
            assertThat(responses.stream().filter(response -> response.getStatus() == 409).findFirst().orElseThrow()
                .getContentAsString()).contains("\"code\":\"U002\"");
            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(userRepository.findByEmail("race@a.com")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.mock.web.MockHttpServletResponse signup(String email) throws Exception {
        return mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"%s","password":"password1","name":"가입자","companyName":"MSP"}
            """.formatted(email))).andReturn().getResponse();
    }
}
