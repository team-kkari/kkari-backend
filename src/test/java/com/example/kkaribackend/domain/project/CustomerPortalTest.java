package com.example.kkaribackend.domain.project;

import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerPortalTest {
    private static final String SERVICES = "/v1/customer/services";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @MockitoBean private AiServerClient aiServerClient;

    private User engineer;
    private User customer;
    private RequestPostProcessor asEngineer;
    private RequestPostProcessor asCustomer;

    @BeforeEach
    void setUp() {
        engineer = saveUser("engineer@kkari.com", "김엔지", "까리", Role.MSP_ENGINEER);
        customer = saveUser("customer@kkari.com", "홍고객", "고객사", Role.CUSTOMER);
        asEngineer = loginAs(engineer);
        asCustomer = loginAs(customer);
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 등록한_서비스를_담당_엔지니어가_목록과_상세에서_본다() throws Exception {
        long serviceId = register(Map.of("트래픽", "평일 낮 집중", "DB", "MySQL"));

        mockMvc.perform(get("/v1/projects").with(asEngineer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].projectId").value(serviceId))
            .andExpect(jsonPath("$[0].companyName").value("고객사"))
            .andExpect(jsonPath("$[0].contactName").value("홍고객"));

        mockMvc.perform(get("/v1/projects/" + serviceId).with(asEngineer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value("쇼핑몰"))
            .andExpect(jsonPath("$.companyName").value("고객사"))
            .andExpect(jsonPath("$.contactName").value("홍고객"))
            .andExpect(jsonPath("$.answers['트래픽']").value("평일 낮 집중"))
            .andExpect(jsonPath("$.answers.DB").value("MySQL"))
            .andExpect(jsonPath("$.stage").value("RECEIVED"))
            .andExpect(jsonPath("$.awsConnectionStatus").value("NOT_CONNECTED"))
            .andExpect(jsonPath("$.externalId").doesNotExist());
    }

    @Test
    void 등록한_답변은_상담_infra_context에_들어간다() throws Exception {
        long serviceId = register(Map.of("트래픽", "평일 낮 집중"));
        when(aiServerClient.intake(any())).thenReturn(IntakeResponseDto.builder().reply("안녕하세요")
            .quickReplies(List.of()).missing(List.of()).missingFields(List.of()).ready(false).build());

        mockMvc.perform(post("/v1/projects/" + serviceId + "/chat/messages").with(asEngineer)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());

        ArgumentCaptor<IntakeRequestDto> request = ArgumentCaptor.forClass(IntakeRequestDto.class);
        verify(aiServerClient).intake(request.capture());
        assertThat(request.getValue().infraContext()).contains("[고객 답변]\n트래픽: 평일 낮 집중");
    }

    @Test
    void 고객은_자기가_등록한_서비스만_본다() throws Exception {
        long serviceId = register(Map.of("트래픽", "평일 낮 집중"));
        RequestPostProcessor otherCustomer = loginAs(saveUser("other@kkari.com", "이고객", "다른 고객사", Role.CUSTOMER));

        mockMvc.perform(get(SERVICES).with(asCustomer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].serviceId").value(serviceId))
            .andExpect(jsonPath("$[0].serviceName").value("쇼핑몰"))
            .andExpect(jsonPath("$[0].engineerName").value("김엔지"))
            .andExpect(jsonPath("$[0].stage").value("RECEIVED"))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
        mockMvc.perform(get(SERVICES + "/" + serviceId).with(asCustomer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value("쇼핑몰"))
            .andExpect(jsonPath("$.answers['트래픽']").value("평일 낮 집중"))
            .andExpect(jsonPath("$.engineerName").value("김엔지"))
            .andExpect(jsonPath("$.stage").value("RECEIVED"))
            .andExpect(jsonPath("$.externalId").doesNotExist())
            .andExpect(jsonPath("$.awsConnectionStatus").doesNotExist());

        mockMvc.perform(get(SERVICES).with(otherCustomer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(SERVICES + "/" + serviceId).with(otherCustomer))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P003"));
        mockMvc.perform(get(SERVICES + "/" + (serviceId + 1000)).with(asCustomer))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void 엔지니어가_직접_만든_프로젝트는_고객이_볼_수_없고_상세의_서비스_항목이_비어_있다() throws Exception {
        Project own = projectRepository.save(Project.builder()
            .user(engineer).companyName("직접 고객사").contactName("담당자").build());

        mockMvc.perform(get(SERVICES + "/" + own.getId()).with(asCustomer))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P003"));
        mockMvc.perform(get("/v1/projects/" + own.getId()).with(asEngineer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("직접 고객사"))
            .andExpect(jsonPath("$.serviceName").isEmpty())
            .andExpect(jsonPath("$.answers").isEmpty());
    }

    @Test
    void 담당하지_않는_엔지니어는_상세를_볼_수_없다() throws Exception {
        long serviceId = register(Map.of());
        RequestPostProcessor otherEngineer = loginAs(saveUser("other-eng@kkari.com", "박엔지", "까리", Role.MSP_ENGINEER));

        mockMvc.perform(get("/v1/projects/" + serviceId).with(otherEngineer))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void 엔지니어가_아닌_사용자를_담당자로_고르면_404() throws Exception {
        for (long engineerId : new long[]{customer.getId(), engineer.getId() + 1000}) {
            mockMvc.perform(post(SERVICES).with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                    .content(body("쇼핑몰", Map.of(), engineerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U003"));
        }
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void 엔지니어_목록은_엔지니어의_id_이름_회사만_준다() throws Exception {
        mockMvc.perform(get("/v1/customer/engineers").with(asCustomer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].engineerId").value(engineer.getId()))
            .andExpect(jsonPath("$[0].name").value("김엔지"))
            .andExpect(jsonPath("$[0].companyName").value("까리"))
            .andExpect(jsonPath("$[0].length()").value(3));
    }

    @Test
    void 한도_안의_답변은_등록된다() throws Exception {
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 49; i++) {
            answers.put("질문" + i, "답");
        }
        answers.put("q".repeat(200), "a".repeat(2000));

        mockMvc.perform(post(SERVICES).with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                .content(body("s".repeat(100), answers, engineer.getId())))
            .andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidRequests() {
        Map<String, String> tooMany = new HashMap<>();
        for (int i = 0; i < 51; i++) {
            tooMany.put("질문" + i, "답");
        }
        Map<String, String> nullAnswer = new HashMap<>();
        nullAnswer.put("질문", null);
        return Stream.of(
            Arguments.of("s".repeat(101), Map.of(), "serviceName"),
            Arguments.of(" ", Map.of(), "serviceName"),
            Arguments.of("쇼핑몰", null, "answers"),
            Arguments.of("쇼핑몰", tooMany, "answers"),
            Arguments.of("쇼핑몰", Map.of("q".repeat(201), "답"), "answers"),
            Arguments.of("쇼핑몰", Map.of(" ", "답"), "answers"),
            Arguments.of("쇼핑몰", Map.of("질문", "a".repeat(2001)), "answers"),
            Arguments.of("쇼핑몰", nullAnswer, "answers")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 한도를_넘거나_빈_값이면_400(String serviceName, Map<String, String> answers, String field) throws Exception {
        mockMvc.perform(post(SERVICES).with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                .content(body(serviceName, answers, engineer.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[0].field").value(startsWith(field)));
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void 담당_엔지니어가_없으면_400() throws Exception {
        mockMvc.perform(post(SERVICES).with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                .content(body("쇼핑몰", Map.of(), null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("engineerId"));
    }

    @Test
    void 고객은_엔지니어_API를_엔지니어는_고객_API를_쓸_수_없다() throws Exception {
        long serviceId = register(Map.of());

        for (String url : List.of("/v1/projects", "/v1/projects/" + serviceId,
            "/v1/projects/" + serviceId + "/aws-connection", "/v1/projects/" + serviceId + "/requirements",
            "/v1/projects/" + serviceId + "/chat/messages", "/v1/projects/" + serviceId + "/discovery",
            "/v1/projects/" + serviceId + "/architectures", "/v1/jobs/1", "/v1/unmapped")) {
            expectForbidden(mockMvc.perform(get(url).with(asCustomer)));
        }
        expectForbidden(mockMvc.perform(post("/v1/projects").with(asCustomer)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"고객사","contactName":"담당자"}""")));

        expectForbidden(mockMvc.perform(get(SERVICES).with(asEngineer)));
        expectForbidden(mockMvc.perform(get(SERVICES + "/" + serviceId).with(asEngineer)));
        expectForbidden(mockMvc.perform(get("/v1/customer/engineers").with(asEngineer)));
        expectForbidden(mockMvc.perform(post(SERVICES).with(asEngineer).contentType(MediaType.APPLICATION_JSON)
            .content(body("쇼핑몰", Map.of(), engineer.getId()))));

        mockMvc.perform(get(SERVICES)).andExpect(status().isUnauthorized());
    }

    @Test
    void API_문서는_역할로_막히는_엔드포인트에_403을_표시한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/v1/customer/services'].get.responses['403']").exists())
            .andExpect(jsonPath("$.paths['/v1/projects/{projectId}'].get.responses['403']").exists())
            .andExpect(jsonPath("$.paths['/v1/auth/login'].post.responses['403']").doesNotExist());
    }

    private void expectForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("S004"));
    }

    private long register(Map<String, String> answers) throws Exception {
        String response = mockMvc.perform(post(SERVICES).with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                .content(body("쇼핑몰", answers, engineer.getId())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonMapper.shared().readTree(response).get("serviceId").asLong();
    }

    private String body(String serviceName, Map<String, String> answers, Long engineerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceName", serviceName);
        body.put("answers", answers);
        body.put("engineerId", engineerId);
        return JsonMapper.shared().writeValueAsString(body);
    }

    private User saveUser(String email, String name, String companyName, Role role) {
        return userRepository.save(User.builder()
            .email(email).password("x").name(name).companyName(companyName).role(role).build());
    }

    private RequestPostProcessor loginAs(User user) {
        CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getRole());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
