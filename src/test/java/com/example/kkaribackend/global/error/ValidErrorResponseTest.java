package com.example.kkaribackend.global.error;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.FieldError;
import com.example.kkaribackend.global.error.response.ValidErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ValidErrorResponseTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void 검증_실패시_비밀번호를_응답과_로그에_남기지_않는다(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"audit@example.com","password":"Zq9!lk","name":"n","companyName":"c"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[0].field").value("password"))
            .andExpect(jsonPath("$.errors[0].reason").exists())
            .andExpect(jsonPath("$.errors[0].value").hasJsonPath())
            .andExpect(jsonPath("$.errors[0].value").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(content().string(not(containsString("Zq9!lk"))));
        assertThat(output.getAll()).doesNotContain("Zq9!lk");
    }

    @Test
    void 일반_필드의_검증_오류는_기존_value를_유지한다() {
        FieldError field = new FieldError("request", "companyName", "company-input", false, null, null, "길이 초과");

        var response = ValidErrorResponse.FieldErrorDetail.from(field);

        assertThat(response.field()).isEqualTo("companyName");
        assertThat(response.value()).isEqualTo("company-input");
        assertThat(response.reason()).isEqualTo("길이 초과");
    }

    @Test
    void JSON_파싱_실패시_입력을_응답과_로그에_남기지_않는다(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":UNQUOTED_PRIVATE_SIGNUP}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(content().string(not(containsString("UNQUOTED_PRIVATE_SIGNUP"))));
        assertThat(output.getAll()).doesNotContain("UNQUOTED_PRIVATE_SIGNUP");
    }

    @Test
    void 타입_변환_실패시_입력을_응답과_로그에_남기지_않는다(CapturedOutput output) throws Exception {
        CustomUserDetails principal = new CustomUserDetails(1L, Role.MSP_ENGINEER);
        var owner = authentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
        for (String path : new String[]{"/v1/projects?stage=PRIVATE_STAGE_VALUE",
            "/v1/projects/PRIVATE_PROJECT_ID/aws-connection"}) {
            mockMvc.perform(get(path).with(owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(content().string(not(containsString("PRIVATE_STAGE_VALUE"))))
                .andExpect(content().string(not(containsString("PRIVATE_PROJECT_ID"))));
        }
        assertThat(output.getAll()).doesNotContain("PRIVATE_STAGE_VALUE", "PRIVATE_PROJECT_ID");
    }
}
