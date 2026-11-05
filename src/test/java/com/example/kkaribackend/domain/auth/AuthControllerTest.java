package com.example.kkaribackend.domain.auth;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    private static final String SIGN_UP_BODY = """
        {"email":"%s","password":"password1","name":"홍길동","companyName":"고객사"}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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

    @Test
    void 이메일은_검증과_저장_조회_전에_공백을_제거하고_소문자로_변환한다() throws Exception {
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("  Kim@A.com  ")))
            .andExpect(status().isCreated());
        assertThat(userRepository.findByEmail("kim@a.com")).isPresent();
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"  KIM@A.COM  ","password":"password1"}"""))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(post("/v1/auth/signup/customer").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("KIM@a.com")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("U002"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 기존_대문자_이메일도_로그인과_중복_검사에_포함한다() throws Exception {
        userRepository.saveAndFlush(User.builder().email("Legacy@A.com").password(passwordEncoder.encode("password1"))
            .name("기존 사용자").companyName("MSP").role(Role.MSP_ENGINEER).build());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"legacy@a.com","password":"password1"}"""))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("legacy@a.com")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("U002"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "12345678", "abc1234", "가나다라마바사아"})
    void 영문과_숫자가_없거나_길이가_짧은_비밀번호는_400(String password) throws Exception {
        for (String path : new String[]{"/v1/auth/signup", "/v1/auth/signup/customer"}) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                    .content(SIGN_UP_BODY.formatted("invalid@a.com").replace("password1", password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].value").isEmpty());
        }
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void 비밀번호_최대_길이는_64자다() throws Exception {
        String password = "a1" + "a".repeat(62);
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("max@a.com").replace("password1", password)))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("over@a.com").replace("password1", password + "a")))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void 내_계정은_두_역할_모두_조회할_수_있고_비밀번호를_노출하지_않는다(Role role) throws Exception {
        User user = userRepository.saveAndFlush(User.builder().email("me@a.com").password("hash")
            .name("내 이름").companyName("내 회사").role(role).build());
        var principal = new CustomUserDetails(user.getId(), role);
        mockMvc.perform(get("/v1/users/me").with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId()))
            .andExpect(jsonPath("$.email").value("me@a.com"))
            .andExpect(jsonPath("$.name").value("내 이름"))
            .andExpect(jsonPath("$.companyName").value("내 회사"))
            .andExpect(jsonPath("$.role").value(role.name()))
            .andExpect(jsonPath("$.password").doesNotHaveJsonPath());
    }

    @Test
    void 내_계정은_비로그인_상태에서_401이다() throws Exception {
        mockMvc.perform(get("/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 기존_대소문자_충돌_계정은_원래_이메일로_각자_로그인하고_임의로_선택하지_않는다() throws Exception {
        String sharedHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password1");
        User upper = userRepository.saveAndFlush(User.builder().email("Legacy@A.com").password(sharedHash)
            .name("대문자 계정").companyName("MSP").role(Role.MSP_ENGINEER).build());
        User lower = userRepository.saveAndFlush(User.builder().email("legacy@a.com").password(sharedHash)
            .name("소문자 계정").companyName("고객사").role(Role.CUSTOMER).build());
        for (User expected : new User[]{upper, lower}) {
            String body = mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"  " + expected.getEmail() + "  \",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String token = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
            mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(expected.getId()))
                .andExpect(jsonPath("$.role").value(expected.getRole().name()));
        }
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"LEGACY@A.COM\",\"password\":\"password1\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(userRepository.findById(upper.getId()).orElseThrow().getPassword()).isEqualTo(sharedHash);
        assertThat(userRepository.findById(lower.getId()).orElseThrow().getPassword()).isEqualTo(sharedHash);
    }

    @Test
    void 기존_BCrypt_계정은_대소문자를_바꿔도_로그인하고_틀린_비밀번호는_거절한다() throws Exception {
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password1");
        User existing = userRepository.saveAndFlush(User.builder().email("Existing@A.com").password(hash)
            .name("기존 계정").companyName("MSP").role(Role.MSP_ENGINEER).build());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"existing@a.com\",\"password\":\"password1\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"existing@a.com\",\"password\":\"password2\"}"))
            .andExpect(status().isUnauthorized());
        assertThat(userRepository.findById(existing.getId()).orElseThrow().getPassword()).isEqualTo(hash);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/auth/signup", "/v1/auth/signup/customer"})
    void UTF8_72바이트를_넘는_64자_비밀번호로_가입과_로그인이_가능하다(String path) throws Exception {
        String password = "a1" + "가".repeat(60) + "나다";
        String wrong = "a1" + "가".repeat(60) + "다나";
        assertThat(password).hasSize(64);
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY.formatted("unicode@a.com").replace("password1", password)))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unicode@a.com\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unicode@a.com\",\"password\":\"" + wrong + "\"}"))
            .andExpect(status().isUnauthorized());
    }
}
