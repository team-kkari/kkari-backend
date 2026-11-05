package com.example.kkaribackend.domain.chat;

import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import com.example.kkaribackend.domain.chat.domain.repository.ChatMessageRepository;
import com.example.kkaribackend.domain.chat.service.ChatMessageWriter;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.dto.IntakeProfile;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {
    private static final IntakeResponseDto REPLY = IntakeResponseDto.builder().reply("고객사 이름은요?")
        .quickReplies(List.of("까리", "다른 회사"))
        .profile(IntakeProfile.builder().companyName("까리").monthlyBudget(BigDecimal.ZERO)
            .containsPersonalData(false).frozenResources(List.of()).build())
        .missing(List.of("워크로드")).missingFields(List.of("workload")).ready(false)
        .recommendRequest(JsonMapper.shared().readTree("{\"infra\":\"EC2\"}")).build();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private AnalysisJobRepository analysisJobRepository;
    @Autowired private ChatMessageWriter writer;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private AiServerClient aiServerClient;

    private String url;
    private Project project;
    private RequestPostProcessor owner;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder().email("chat@kkari.com").password("x")
            .name("엔지니어").companyName("까리").role(Role.MSP_ENGINEER).build());
        project = projectRepository.save(Project.builder().user(user).companyName("고객사").contactName("담당자").build());
        owner = loginAs(user.getId());
        url = "/v1/projects/" + project.getId() + "/chat/messages";
        when(aiServerClient.intake(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return REPLY;
        });
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        analysisJobRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RequestPostProcessor loginAs(Long userId) {
        CustomUserDetails principal = new CustomUserDetails(userId, Role.MSP_ENGINEER);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post(url).with(owner).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<ChatMessage> messages() {
        return chatMessageRepository.findAllByProjectIdOrderBySeqAsc(project.getId());
    }

    @Test
    void 빈_대화에서_내용_없이_시작하면_인사만_저장한다() throws Exception {
        send("{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.userMessage").doesNotExist())
            .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
            .andExpect(jsonPath("$.profile.companyName").value("까리"))
            .andExpect(jsonPath("$.profile.monthlyBudget").value(0))
            .andExpect(jsonPath("$.profile.containsPersonalData").value(false))
            .andExpect(jsonPath("$.profile.frozenResources").isEmpty())
            .andExpect(jsonPath("$.profile.company_name").doesNotHaveJsonPath());

        assertThat(messages()).singleElement().satisfies(message -> {
            assertThat(message.getSeq()).isEqualTo(1);
            assertThat(message.getRole()).isEqualTo(ChatRole.ASSISTANT);
            assertThat(message.getInfraContextHash()).hasSize(64);
            assertThat(JsonMapper.shared().readTree(message.getRecommendRequest()).get("infra").asString()).isEqualTo("EC2");
        });
        ArgumentCaptor<IntakeRequestDto> request = ArgumentCaptor.forClass(IntakeRequestDto.class);
        verify(aiServerClient).intake(request.capture());
        assertThat(request.getValue().history()).isEmpty();
        assertThat(request.getValue().infraContext()).isEmpty();
        assertThat(request.getValue().sessionId()).isEqualTo("project-" + project.getId());
        assertThat(request.getValue().requiredFields()).isNull();
    }

    @Test
    void 전송하면_전체_대화를_보내고_사용자와_AI_순서로_저장한다() throws Exception {
        send("{}").andExpect(status().isOk());
        send("{\"content\":\"까리입니다\",\"clientMessageId\":\"m1\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.userMessage.content").value("까리입니다"));

        assertThat(messages()).extracting(ChatMessage::getRole)
            .containsExactly(ChatRole.ASSISTANT, ChatRole.USER, ChatRole.ASSISTANT);
        assertThat(messages()).extracting(ChatMessage::getSeq).containsExactly(1, 2, 3);
        ArgumentCaptor<IntakeRequestDto> request = ArgumentCaptor.forClass(IntakeRequestDto.class);
        verify(aiServerClient, times(2)).intake(request.capture());
        assertThat(request.getValue().history()).containsExactly(
            new IntakeRequestDto.Message("assistant", REPLY.reply()), new IntakeRequestDto.Message("user", "까리입니다"));
    }

    @Test
    void 조회하면_순서와_최근_상담_결과를_복원한다() throws Exception {
        send("{\"content\":\"안녕\"}").andExpect(status().isOk());
        mockMvc.perform(get(url).with(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].role").value("USER"))
            .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
            .andExpect(jsonPath("$.messages[1].quickReplies[0]").value("까리"))
            .andExpect(jsonPath("$.messages[1].createdAt").exists())
            .andExpect(jsonPath("$.missingFields[0]").value("workload"))
            .andExpect(jsonPath("$.ready").value(false))
            .andExpect(jsonPath("$.profile.companyName").value("까리"))
            .andExpect(jsonPath("$.profile.monthlyBudget").value(0))
            .andExpect(jsonPath("$.profile.containsPersonalData").value(false))
            .andExpect(jsonPath("$.profile.frozenResources").isEmpty())
            .andExpect(jsonPath("$.profile.company_name").doesNotHaveJsonPath());
    }

    @Test
    void 담당하지_않는_프로젝트_조회와_전송은_403이다() throws Exception {
        User other = userRepository.save(User.builder().email("other-chat@kkari.com").password("x")
            .name("다른 엔지니어").companyName("까리").role(Role.MSP_ENGINEER).build());
        mockMvc.perform(get(url).with(loginAs(other.getId()))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("P002"));
        mockMvc.perform(post(url).with(loginAs(other.getId())).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("P002"));
        verifyNoInteractions(aiServerClient);
        assertThat(messages()).isEmpty();
    }

    @Test
    void AI_실패에도_사용자_메시지는_남고_빈_내용으로_재시도한다() throws Exception {
        when(aiServerClient.intake(any())).thenThrow(new AiServerException(AiErrorCode.AI_SERVER_ERROR)).thenReturn(REPLY);
        send("{\"content\":\"안녕\",\"clientMessageId\":\"retry\"}")
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI001"));
        assertThat(messages()).extracting(ChatMessage::getRole).containsExactly(ChatRole.USER);
        send("{}").andExpect(status().isOk()).andExpect(jsonPath("$.userMessage").doesNotExist());
        assertThat(messages()).extracting(ChatMessage::getRole).containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        ArgumentCaptor<IntakeRequestDto> request = ArgumentCaptor.forClass(IntakeRequestDto.class);
        verify(aiServerClient, times(2)).intake(request.capture());
        assertThat(request.getAllValues().get(0).history()).isEqualTo(request.getValue().history());
    }

    @Test
    void 같은_clientMessageId는_저장과_AI_호출을_반복하지_않는다() throws Exception {
        String body = "{\"content\":\"안녕\",\"clientMessageId\":\"same\"}";
        send(body).andExpect(status().isOk());
        Long userId = messages().getFirst().getId();
        Long assistantId = messages().getLast().getId();
        send(body).andExpect(status().isOk())
            .andExpect(jsonPath("$.userMessage.id").value(userId))
            .andExpect(jsonPath("$.assistantMessage.id").value(assistantId));
        assertThat(messages()).hasSize(2);
        verify(aiServerClient).intake(any());
    }

    @Test
    void 실패한_clientMessageId를_재전송하면_사용자를_중복_저장하지_않는다() throws Exception {
        when(aiServerClient.intake(any())).thenThrow(new AiServerException(AiErrorCode.AI_SERVER_TIMEOUT)).thenReturn(REPLY);
        String body = "{\"content\":\"안녕\",\"clientMessageId\":\"same\"}";
        send(body).andExpect(status().isGatewayTimeout());
        Long userId = messages().getFirst().getId();
        send(body).andExpect(status().isOk()).andExpect(jsonPath("$.userMessage.id").value(userId));
        assertThat(messages()).hasSize(2);
    }

    @Test
    void 실패한_메시지_이후_다른_답변이_있으면_재전송을_거절한다() throws Exception {
        when(aiServerClient.intake(any())).thenThrow(new AiServerException(AiErrorCode.AI_SERVER_ERROR)).thenReturn(REPLY);
        String first = "{\"content\":\"첫 메시지\",\"clientMessageId\":\"A\"}";
        send(first).andExpect(status().isBadGateway());
        send("{\"content\":\"다음 메시지\",\"clientMessageId\":\"B\"}").andExpect(status().isOk());
        List<Long> ids = messages().stream().map(ChatMessage::getId).toList();

        send(first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CH001"));

        assertThat(messages()).extracting(ChatMessage::getId).containsExactlyElementsOf(ids);
        assertThat(messages()).extracting(ChatMessage::getRole)
            .containsExactly(ChatRole.USER, ChatRole.USER, ChatRole.ASSISTANT);
        verify(aiServerClient, times(2)).intake(any());
    }

    @Test
    void 실패한_메시지_이후_다른_사용자_메시지만_있어도_재전송을_거절한다() throws Exception {
        when(aiServerClient.intake(any())).thenThrow(new AiServerException(AiErrorCode.AI_SERVER_ERROR));
        String first = "{\"content\":\"첫 메시지\",\"clientMessageId\":\"A\"}";
        send(first).andExpect(status().isBadGateway());
        send("{\"content\":\"다음 메시지\",\"clientMessageId\":\"B\"}").andExpect(status().isBadGateway());
        List<Long> ids = messages().stream().map(ChatMessage::getId).toList();

        send(first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CH001"));

        assertThat(messages()).extracting(ChatMessage::getId).containsExactlyElementsOf(ids);
        verify(aiServerClient, times(2)).intake(any());
    }

    @Test
    void 인프라가_같은_AI_차례_재요청은_409이다() throws Exception {
        send("{}").andExpect(status().isOk());
        send("{\"content\":\"   \"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CH001"));
        verify(aiServerClient).intake(any());
    }

    @Test
    void 인프라_요약이_바뀌면_내용_없이_질문을_요청한다() throws Exception {
        send("{}").andExpect(status().isOk());
        project.updateTerraform(new TerraformInfra(Resources.builder().ec2(List.of()).build(), List.of(), Instant.EPOCH));
        projectRepository.save(project);
        send("{}").andExpect(status().isOk());
        assertThat(messages()).hasSize(2);
        assertThat(messages().getFirst().getInfraContextHash()).isNotEqualTo(messages().getLast().getInfraContextHash());
        ArgumentCaptor<IntakeRequestDto> request = ArgumentCaptor.forClass(IntakeRequestDto.class);
        verify(aiServerClient, times(2)).intake(request.capture());
        assertThat(request.getValue().infraContext()).contains("Terraform");
        send("{}").andExpect(status().isConflict());
    }

    @Test
    void 분석이_완료되면_인프라_요약으로_추가_질문을_요청한다() throws Exception {
        send("{}").andExpect(status().isOk());
        AnalysisJob job = AnalysisJob.builder().project(project).build();
        job.succeed(JsonMapper.shared().writeValueAsString(InfraSnapshot.builder()
            .region("ap-northeast-2").collectedAt(Instant.EPOCH).build()));
        analysisJobRepository.save(job);

        send("{}").andExpect(status().isOk());
        ArgumentCaptor<IntakeRequestDto> request = ArgumentCaptor.forClass(IntakeRequestDto.class);
        verify(aiServerClient, times(2)).intake(request.capture());
        assertThat(request.getValue().infraContext()).contains("[AWS 분석]", "ap-northeast-2");
        assertThat(messages()).hasSize(2);
    }

    @Test
    void AI_실패로_사용자가_마지막이어도_이전_상담_결과를_조회한다() throws Exception {
        send("{}").andExpect(status().isOk());
        when(aiServerClient.intake(any())).thenThrow(new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE));
        send("{\"content\":\"추가 답변\"}").andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI003"));

        mockMvc.perform(get(url).with(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[1].role").value("USER"))
            .andExpect(jsonPath("$.missingFields[0]").value("workload"))
            .andExpect(jsonPath("$.profile.companyName").value("까리"));
    }

    @Test
    void 확인_전_프로필의_null을_조회에서도_유지한다() throws Exception {
        when(aiServerClient.intake(any())).thenReturn(IntakeResponseDto.builder().reply("첫 질문")
            .quickReplies(List.of()).profile(IntakeProfile.builder().build())
            .missingFields(List.of("monthly_budget", "contains_personal_data", "frozen_resources")).ready(false).build());
        send("{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.monthlyBudget").hasJsonPath())
            .andExpect(jsonPath("$.profile.monthlyBudget").value(nullValue()))
            .andExpect(jsonPath("$.profile.containsPersonalData").hasJsonPath())
            .andExpect(jsonPath("$.profile.containsPersonalData").value(nullValue()))
            .andExpect(jsonPath("$.profile.frozenResources").hasJsonPath())
            .andExpect(jsonPath("$.profile.frozenResources").value(nullValue()));
        ChatMessage stored = messages().getFirst();
        assertThat(stored.getProfile().monthlyBudget()).isNull();
        assertThat(stored.getProfile().containsPersonalData()).isNull();
        assertThat(stored.getProfile().frozenResources()).isNull();
        mockMvc.perform(get(url).with(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.monthlyBudget").hasJsonPath())
            .andExpect(jsonPath("$.profile.monthlyBudget").value(nullValue()))
            .andExpect(jsonPath("$.profile.containsPersonalData").hasJsonPath())
            .andExpect(jsonPath("$.profile.containsPersonalData").value(nullValue()))
            .andExpect(jsonPath("$.profile.frozenResources").hasJsonPath())
            .andExpect(jsonPath("$.profile.frozenResources").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"reply\":\"질문\",\"ready\":false}",
        "{\"reply\":\"질문\",\"ready\":false,\"recommend_request\":null}"
    })
    void 추천_요청이_없거나_JSON_null이면_SQL_NULL로_저장한다(String body) throws Exception {
        when(aiServerClient.intake(any())).thenReturn(JsonMapper.shared().readValue(body, IntakeResponseDto.class));

        send("{}").andExpect(status().isOk());

        ChatMessage stored = messages().getFirst();
        assertThat(stored.getRecommendRequest()).isNull();
        assertThat(jdbcTemplate.queryForObject(
            "select recommend_request is null from tbl_chat_message where id = ?", Boolean.class, stored.getId()))
            .isTrue();
    }

    @Test
    void 내용이_2001자이거나_식별자가_65자면_400이다() throws Exception {
        send("{\"content\":\"" + "가".repeat(2001) + "\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].reason").value("메시지는 2000자 이하여야 합니다."));
        send("{\"content\":\"안녕\",\"clientMessageId\":\"" + "a".repeat(65) + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].reason").value("메시지 식별자는 64자 이하여야 합니다."));
        assertThat(messages()).isEmpty();
        verifyNoInteractions(aiServerClient);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 검증과_JSON_파싱_실패시_대화_원문을_로그에_남기지_않는다(CapturedOutput output) throws Exception {
        String content = "PRIVATE_CHAT_CONTENT_".repeat(101);
        send("{\"content\":\"" + content + "\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[0].field").value("content"))
            .andExpect(jsonPath("$.errors[0].reason").exists())
            .andExpect(jsonPath("$.errors[0].value").doesNotExist())
            .andExpect(content().string(not(containsString("PRIVATE_CHAT_CONTENT_"))));
        send("{\"content\":UNQUOTED_PRIVATE_CHAT}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(content().string(not(containsString("UNQUOTED_PRIVATE_CHAT"))));
        assertThat(output.getAll()).doesNotContain(content, "UNQUOTED_PRIVATE_CHAT");
        verifyNoInteractions(aiServerClient);
    }

    @Test
    void 동시_시작으로_먼저_저장된_인사를_반환한다() throws Exception {
        when(aiServerClient.intake(any())).thenAnswer(invocation -> {
            writer.saveAssistant(project, 1, REPLY, "a".repeat(64));
            return IntakeResponseDto.builder().reply("버릴 응답").build();
        });
        send("{}").andExpect(status().isOk()).andExpect(jsonPath("$.assistantMessage.content").value(REPLY.reply()));
        assertThat(messages()).hasSize(1);
    }

    @Test
    void AI_호출_중_대화가_바뀌면_오래된_응답은_409로_거절한다() throws Exception {
        when(aiServerClient.intake(any())).thenAnswer(invocation -> {
            writer.saveUser(project, 2, "동시 메시지", "concurrent");
            return REPLY;
        });
        send("{\"content\":\"첫 메시지\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CH002"));
        assertThat(messages()).extracting(ChatMessage::getRole).containsExactly(ChatRole.USER, ChatRole.USER);
    }

    @Test
    void 없는_프로젝트는_404이다() throws Exception {
        url = "/v1/projects/9223372036854775807/chat/messages";
        send("{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("P001"));
        mockMvc.perform(get(url).with(owner)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("P001"));
        verifyNoInteractions(aiServerClient);
    }
}
