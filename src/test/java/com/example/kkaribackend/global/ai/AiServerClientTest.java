package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.AiMessage;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.dto.ChatEvent;
import com.example.kkaribackend.global.ai.dto.ChatRequest;
import com.example.kkaribackend.global.ai.dto.ConsultRequest;
import com.example.kkaribackend.global.ai.dto.ConsultResponse;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import com.example.kkaribackend.global.ai.dto.TerraformFile;
import com.example.kkaribackend.global.ai.dto.TerraformFormat;
import com.example.kkaribackend.global.ai.dto.TerraformRequest;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 HTTP 로 확인한다. 스트림·시간 초과·멀티파트는 MockRestServiceServer 로 재현할 수 없다.
 */
class AiServerClientTest {

    private static final ArchitectureRequest ARCHITECTURE_REQUEST = ArchitectureRequest.builder()
        .infra("EC2 i-1: t3.micro").goal("").priorities(List.of("cost")).count(3).build();
    private static final IntakeRequestDto INTAKE_REQUEST = new IntakeRequestDto(List.of(), "", "project-1", null);
    private static final TerraformRequest TERRAFORM_REQUEST = TerraformRequest.builder()
        .name("serverless").diagram(json("{\"nodes\":[]}")).build();
    private static final String ERROR_BODY = "{\"code\":\"%s\",\"message\":\"%s\",\"retryable\":%s}";

    private record Recorded(String path, String contentType, String body) {
    }

    // delay 뒤에 헤더를 보내고, chunks 를 하나씩 flush 한다. beforeChunk 는 각 조각의 순서를 받아 보내기 직전에 실행한다
    private record Reply(int status, Map<String, String> headers, Duration delay, List<byte[]> chunks,
                         IntConsumer beforeChunk) {
    }

    private static final Map<String, String> NDJSON = Map.of("Content-Type", "application/x-ndjson; charset=utf-8");

    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private AiServerClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = client(AiServerClient.Timeouts.SPEC);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void health는_ok일_때만_true이다() {
        replyJson(200, "{\"ok\":true,\"chunks\":1200}");
        replyJson(200, "{\"ok\":false}");
        replyJson(500, ERROR_BODY.formatted("INTERNAL_ERROR", "오류", false));

        assertThat(client.isHealthy()).isTrue();
        assertThat(client.isHealthy()).isFalse();
        assertThat(client.isHealthy()).isFalse();
        assertThat(requests.getFirst().path()).isEqualTo("/health");
    }

    @Test
    void health는_재시도_가능_오류여도_다시_보내지_않는다() {
        replyJson(503, ERROR_BODY.formatted("UPSTREAM_UNAVAILABLE", "잠시 후", true));

        assertThat(client.isHealthy()).isFalse();
        assertThat(requests).hasSize(1);
    }

    @Test
    void health는_연결할_수_없으면_false이다() {
        server.stop(0);

        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void consultFiles는_files_필드로_파일을_그대로_보내고_file_analysis를_돌려준다() {
        replyJson(200, "{\"file_analysis\":{\"summary\":\"요약\",\"monthly_cost_usd\":null,\"files\":[]}}");

        JsonNode fileAnalysis = client.consultFiles(List.of(
            new MockMultipartFile("upload", "main.tf", "text/plain", "resource {}".getBytes()),
            new MockMultipartFile("upload", "cost.csv", "text/csv", "a,b".getBytes())));

        assertThat(fileAnalysis).isEqualTo(json("{\"summary\":\"요약\",\"monthly_cost_usd\":null,\"files\":[]}"));
        Recorded request = requests.getFirst();
        assertThat(request.path()).isEqualTo("/consult/files");
        assertThat(request.contentType()).startsWith("multipart/form-data");
        assertThat(request.body())
            .contains("name=\"files\"; filename=\"main.tf\"").contains("resource {}")
            .contains("name=\"files\"; filename=\"cost.csv\"").contains("a,b")
            .doesNotContain("name=\"upload\"");
    }

    @Test
    void consultFiles를_재시도하면_파일을_다시_보낸다() {
        replyJson(503, ERROR_BODY.formatted("UPSTREAM_UNAVAILABLE", "잠시 후", true));
        replyJson(200, "{\"file_analysis\":{}}");

        client.consultFiles(List.of(new MockMultipartFile("upload", "main.tf", "text/plain", "resource {}".getBytes())));

        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).body()).contains("filename=\"main.tf\"").contains("resource {}");
    }

    @Test
    void consultFiles_응답에_file_analysis가_없으면_AI_SERVER_ERROR() {
        replyJson(200, "{}");

        assertError(() -> client.consultFiles(List.of()), AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void consult_요청은_snake_case이고_질문을_읽으며_원본을_보존한다() {
        replyJson(200, """
            {"reply":"어떤 고민이 있으세요?","question":{"id":"Q2","step":1,"label":"받고 싶은 것","priority":"required",
              "input":"multi","options":[{"value":"cost","label":"요금 절감"}]},
             "quick_replies":["잘 모르겠어요"],"profile":{"concern":null},"missing":["Q2"],
             "missing_labels":["받고 싶은 것"],"recommended_missing":["Q6"],"ready":false,
             "recommend_request":null,"new_field":1}""");

        ConsultResponse response = client.consult(ConsultRequest.builder()
            .history(List.of(new AiMessage("user", "안녕"))).fileAnalysis(json("{\"summary\":\"요약\"}"))
            .sessionId("consult-1").build());

        assertThat(requests.getFirst().path()).isEqualTo("/consult");
        assertThat(json(requests.getFirst().body())).isEqualTo(json("""
            {"history":[{"role":"user","content":"안녕"}],"file_analysis":{"summary":"요약"},"session_id":"consult-1"}"""));
        assertThat(response.reply()).isEqualTo("어떤 고민이 있으세요?");
        assertThat(response.question().input()).isEqualTo("multi");
        assertThat(response.question().options()).containsExactly(new ConsultResponse.Option("cost", "요금 절감"));
        assertThat(response.ready()).isFalse();
        assertThat(response.rawResponse().get("missing_labels").get(0).asString()).isEqualTo("받고 싶은 것");
        assertThat(response.rawResponse().get("new_field").asInt()).isEqualTo(1);
    }

    @Test
    void consult_첫_턴은_파일_분석_없이_보낸다() {
        replyJson(200, "{\"reply\":\"첫 질문\",\"question\":null,\"ready\":false}");

        ConsultResponse response = client.consult(ConsultRequest.builder().history(List.of()).sessionId("c").build());

        assertThat(json(requests.getFirst().body())).isEqualTo(json("{\"history\":[],\"session_id\":\"c\"}"));
        assertThat(response.question()).isNull();
    }

    @Test
    void intake_요청은_snake_case이고_새_응답_필드는_무시하며_원본을_보존한다() {
        replyJson(200, """
            {"reply":"질문","quick_replies":["예"],"profile":{"company_name":"고객사",
             "monthly_budget":3000000.0,"contains_personal_data":null,"frozen_resources":[],
             "currency":"KRW","change_tolerance":"허용","new_field":true},
             "missing":["고객사명"],"missing_fields":["company_name"],"ready":false,
             "recommend_request":{"infra":"EC2","constraints":{"monthly_budget":0}},"extra":true}""");

        IntakeResponseDto response = client.intake(new IntakeRequestDto(
            List.of(new AiMessage("user", "안녕")), "EC2 1대", "project-12", List.of("company_name")));

        assertThat(json(requests.getFirst().body())).isEqualTo(json("""
            {"history":[{"role":"user","content":"안녕"}],"infra_context":"EC2 1대","session_id":"project-12",
             "required_fields":["company_name"]}"""));
        assertThat(response.reply()).isEqualTo("질문");
        assertThat(response.profile().companyName()).isEqualTo("고객사");
        assertThat(response.profile().monthlyBudget()).isEqualByComparingTo("3000000.0");
        assertThat(response.profile().changeTolerance()).isEqualTo("허용");
        assertThat(response.missingFields()).containsExactly("company_name");
        assertThat(response.recommendRequest().get("infra").asString()).isEqualTo("EC2");
        assertThat(response.rawResponse().get("extra").asBoolean()).isTrue();
        assertThat(response.rawResponse().get("profile").get("new_field").asBoolean()).isTrue();
    }

    @Test
    void intake_required_fields가_없으면_보내지_않는다() {
        replyJson(200, "{\"reply\":\"질문\",\"ready\":false}");

        client.intake(INTAKE_REQUEST);

        assertThat(json(requests.getFirst().body()))
            .isEqualTo(json("{\"history\":[],\"infra_context\":\"\",\"session_id\":\"project-1\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"reply\":null}", "{\"reply\":\"  \"}"})
    void intake의_빈_성공_응답은_AI_SERVER_ERROR(String body) {
        replyJson(200, body);

        assertError(() -> client.intake(INTAKE_REQUEST), AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 응답_파싱_실패는_AI_SERVER_ERROR이며_응답_원문은_AI_CALL_로그에만_남긴다(CapturedOutput output) {
        replyJson(200, "{\"reply\":PRIVATE_AI_RESPONSE}");

        assertError(() -> client.intake(INTAKE_REQUEST), AiErrorCode.AI_SERVER_ERROR);
        assertThat(otherLogs(output)).doesNotContain("PRIVATE_AI_RESPONSE");
    }

    @Test
    void architectures는_제약과_현재_비용을_보내고_응답_JSON을_그대로_돌려준다() {
        String body = """
            {"overall":"요약","architectures":[{"rank":1,"name":"서버리스","cost":{"monthly_total_usd":12.5}}],
             "failed":[]}""";
        replyJson(200, body);

        JsonNode response = client.architectures(ArchitectureRequest.builder()
            .infra("EC2").goal("비용 절감").priorities(List.of("cost"))
            .constraints(json("{\"monthly_budget\":3000000,\"currency\":\"KRW\",\"frozen_resources\":[]}"))
            .count(5).currentMonthlyUsd(new BigDecimal("4200")).build());

        assertThat(response).isEqualTo(json(body));
        assertThat(requests.getFirst().path()).isEqualTo("/architectures");
        assertThat(json(requests.getFirst().body())).isEqualTo(json("""
            {"infra":"EC2","goal":"비용 절감","priorities":["cost"],
             "constraints":{"monthly_budget":3000000,"currency":"KRW","frozen_resources":[]},
             "count":5,"current_monthly_usd":4200}"""));
    }

    @Test
    void architectures는_제약과_현재_비용이_없으면_보내지_않는다() {
        replyJson(200, "{\"architectures\":[]}");

        client.architectures(ARCHITECTURE_REQUEST);

        assertThat(json(requests.getFirst().body()))
            .isEqualTo(json("{\"infra\":\"EC2 i-1: t3.micro\",\"goal\":\"\",\"priorities\":[\"cost\"],\"count\":3}"));
    }

    @Test
    void architectures_응답이_비어_있으면_AI_SERVER_ERROR() {
        replyJson(200, "null");

        assertError(() -> client.architectures(ARCHITECTURE_REQUEST), AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void terraform_json은_파일별_내용을_JSON으로_돌려준다() {
        replyJson(200, "{\"project\":\"serverless\",\"files\":{\"main.tf\":\"resource {}\"},\"warnings\":[]}");

        JsonNode response = client.terraform(TerraformRequest.builder().diagram(json("{\"nodes\":[]}")).build());

        assertThat(response.get("files").get("main.tf").asString()).isEqualTo("resource {}");
        assertThat(json(requests.getFirst().body())).isEqualTo(json("{\"diagram\":{\"nodes\":[]},\"format\":\"json\"}"));
    }

    @Test
    void terraform_tf는_파일_이름과_종류와_내용을_그대로_돌려준다() {
        reply(200, Map.of("Content-Type", "text/plain; charset=utf-8",
            "Content-Disposition", "attachment; filename=\"serverless.tf\""), "# 전체\nresource {}".getBytes(StandardCharsets.UTF_8));

        TerraformFile file = client.terraformFile(TERRAFORM_REQUEST, TerraformFormat.TF);

        assertThat(file.filename()).isEqualTo("serverless.tf");
        assertThat(file.contentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo("# 전체\nresource {}");
        assertThat(json(requests.getFirst().body()))
            .isEqualTo(json("{\"name\":\"serverless\",\"diagram\":{\"nodes\":[]},\"format\":\"tf\"}"));
    }

    @Test
    void terraform_zip은_바이트를_바꾸지_않는다() {
        byte[] zip = {0x50, 0x4b, 0x03, 0x04, (byte) 0xff, 0x00, (byte) 0x80};
        reply(200, Map.of("Content-Type", "application/zip",
            "Content-Disposition", "attachment; filename=\"serverless-terraform.zip\""), zip);

        TerraformFile file = client.terraformFile(TERRAFORM_REQUEST, TerraformFormat.ZIP);

        assertThat(file.filename()).isEqualTo("serverless-terraform.zip");
        assertThat(file.contentType()).isEqualTo(MediaType.parseMediaType("application/zip"));
        assertThat(file.content()).isEqualTo(zip);
        assertThat(json(requests.getFirst().body()).get("format").asString()).isEqualTo("zip");
    }

    @Test
    void terraform_파일_이름이_없으면_AI_SERVER_ERROR() {
        reply(200, Map.of("Content-Type", "application/zip"), new byte[]{1});

        assertError(() -> client.terraformFile(TERRAFORM_REQUEST, TerraformFormat.ZIP), AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void terraform_Content_Disposition이_잘못되면_AI_SERVER_ERROR() {
        reply(200, Map.of("Content-Type", "application/zip",
            "Content-Disposition", "attachment; filename*=UTF-8''%ZZ.zip"), new byte[]{1});

        assertError(() -> client.terraformFile(TERRAFORM_REQUEST, TerraformFormat.ZIP), AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void chat은_NDJSON을_모으지_않고_한_줄씩_넘기고_끝날_때_요약_한_줄만_남긴다(CapturedOutput output) {
        CountDownLatch firstEventReceived = new CountDownLatch(1);
        AtomicBoolean streamed = new AtomicBoolean();
        List<ChatEvent> events = new ArrayList<>();
        replies.add(new Reply(200, NDJSON, Duration.ZERO, List.of(
            line("{\"type\":\"sources\",\"sources\":[{\"n\":1,\"title\":\"NAT\"}]}"),
            line("{\"type\":\"delta\",\"text\":\"NAT \"}"),
            line(""),
            line("{\"type\":\"delta\",\"text\":\"줄이기 [1]\"}"),
            line("{\"type\":\"done\",\"refused\":false}")),
            // 두 번째 줄을 보내기 전에 첫 이벤트가 이미 처리됐어야 한다
            index -> {
                if (index == 1) {
                    streamed.set(await(firstEventReceived));
                }
            }));

        client.chat(ChatRequest.builder().question("NAT 비용").history(List.of()).build(), event -> {
            events.add(event);
            firstEventReceived.countDown();
        });

        assertThat(streamed).isTrue();
        assertThat(events).hasSize(4);
        assertThat(((ChatEvent.Sources) events.get(0)).sources().get(0).get("n").asInt()).isEqualTo(1);
        assertThat(events.subList(1, 4)).containsExactly(new ChatEvent.Delta("NAT "),
            new ChatEvent.Delta("줄이기 [1]"), new ChatEvent.Done(false));
        assertThat(json(requests.getFirst().body())).isEqualTo(json("{\"question\":\"NAT 비용\",\"history\":[]}"));
        assertThat(aiCallLogs(output)).hasSize(3);
        assertThat(aiCallLog(output, "ai_call POST /chat")).contains("attempt=1/3", "status=200", "response=stream",
            "request={\"question\":\"NAT 비용\",\"history\":[]}").doesNotContain("delta");
        assertThat(aiCallLog(output, "ai_chat_summary"))
            .contains("sources=1", "refused=false", "error=null", "answer=NAT 줄이기 [1]");
        assertThat(aiCallLog(output, "ai_call_end")).contains("POST /chat", "attempts=1/3", "result=OK");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void chat_스트림_도중_오류는_요약에_코드와_retryable을_남긴다(CapturedOutput output) {
        replyStream("{\"type\":\"sources\",\"sources\":[]}", "{\"type\":\"delta\",\"text\":\"일부\"}",
            "{\"type\":\"error\",\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"잠시 후\",\"retryable\":true}");

        client.chat(ChatRequest.builder().question("q").build(), event -> {
        });

        assertThat(aiCallLog(output, "ai_chat_summary"))
            .contains("sources=0", "error=UPSTREAM_UNAVAILABLE(retryable=true)", "answer=일부");
    }

    @Test
    void chat_스트림_도중_시간_제한이_지나_끊기면_AI_SERVER_TIMEOUT이고_다시_보내지_않는다() {
        Duration limit = Duration.ofMillis(300);
        AiServerClient slowClient = client(new AiServerClient.Timeouts(limit, limit, limit, limit, limit));
        replies.add(new Reply(200, NDJSON, Duration.ZERO, List.of(line("{\"type\":\"sources\",\"sources\":[]}"),
            line("{\"type\":\"done\",\"refused\":false}")), index -> {
                if (index == 1) {
                    sleep(1_000);
                }
            }));
        List<ChatEvent> events = new ArrayList<>();

        assertError(() -> slowClient.chat(ChatRequest.builder().question("q").build(), events::add),
            AiErrorCode.AI_SERVER_TIMEOUT);
        assertThat(events).hasSize(1);
        assertThat(requests).hasSize(1);
    }

    @Test
    void chat_거절은_done_refused로_넘긴다() {
        replyStream("{\"type\":\"sources\",\"sources\":[]}", "{\"type\":\"done\",\"refused\":true}");
        List<ChatEvent> events = new ArrayList<>();

        client.chat(ChatRequest.builder().question("q").build(), events::add);

        assertThat(events.getLast()).isEqualTo(new ChatEvent.Done(true));
    }

    @Test
    void chat_스트림_도중_오류는_마지막_이벤트로_넘기고_다시_요청하지_않는다() {
        replyStream("{\"type\":\"sources\",\"sources\":[]}", "{\"type\":\"delta\",\"text\":\"일부\"}",
            "{\"type\":\"error\",\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"잠시 후\",\"retryable\":true}");
        List<ChatEvent> events = new ArrayList<>();

        client.chat(ChatRequest.builder().question("q").build(), events::add);

        assertThat(events.getLast()).isEqualTo(new ChatEvent.Failure("UPSTREAM_UNAVAILABLE", "잠시 후", true));
        assertThat(requests).hasSize(1);
    }

    @Test
    void chat_첫_줄_전에_온_재시도_가능_오류는_다시_요청한다() {
        replyJson(429, ERROR_BODY.formatted("RATE_LIMITED", "한도", true));
        replyStream("{\"type\":\"done\",\"refused\":false}");
        List<ChatEvent> events = new ArrayList<>();

        client.chat(ChatRequest.builder().question("q").build(), events::add);

        assertThat(events).containsExactly(new ChatEvent.Done(false));
        assertThat(requests).hasSize(2);
    }

    @Test
    void chat_끝_이벤트_없이_끊기면_AI_SERVER_UNAVAILABLE이고_다시_요청하지_않는다() {
        replyStream("{\"type\":\"sources\",\"sources\":[]}", "{\"type\":\"delta\",\"text\":\"일부\"}");
        List<ChatEvent> events = new ArrayList<>();

        assertError(() -> client.chat(ChatRequest.builder().question("q").build(), events::add),
            AiErrorCode.AI_SERVER_UNAVAILABLE);
        assertThat(events).hasSize(2);
        assertThat(requests).hasSize(1);
    }

    @Test
    void chat_알_수_없는_이벤트는_건너뛴다() {
        replyStream("{\"type\":\"ping\"}", "{\"type\":\"done\",\"refused\":false}");
        List<ChatEvent> events = new ArrayList<>();

        client.chat(ChatRequest.builder().question("q").build(), events::add);

        assertThat(events).containsExactly(new ChatEvent.Done(false));
    }

    @Test
    void chat_JSON이_아닌_줄은_AI_SERVER_ERROR() {
        replyStream("not json");

        assertError(() -> client.chat(ChatRequest.builder().question("q").build(), event -> {
        }), AiErrorCode.AI_SERVER_ERROR);
    }

    @ParameterizedTest
    @CsvSource({
        "429, RATE_LIMITED, true, AI_SERVER_UNAVAILABLE, 3",
        "503, UPSTREAM_UNAVAILABLE, true, AI_SERVER_UNAVAILABLE, 3",
        "500, INTERNAL_ERROR, true, AI_SERVER_UNAVAILABLE, 3",
        "422, REFUSED, false, AI_REQUEST_REFUSED, 1",
        "400, INVALID_REQUEST, false, AI_SERVER_ERROR, 1",
        "422, VALIDATION_ERROR, false, AI_SERVER_ERROR, 1",
        "404, NOT_FOUND, false, AI_SERVER_ERROR, 1",
        "405, METHOD_NOT_ALLOWED, false, AI_SERVER_ERROR, 1",
        "500, NOT_CONFIGURED, false, AI_SERVER_ERROR, 1",
        "500, LLM_AUTH, false, AI_SERVER_ERROR, 1",
        "502, GENERATION_FAILED, false, AI_SERVER_ERROR, 1",
        "500, INTERNAL_ERROR, false, AI_SERVER_ERROR, 1",
        "503, UPSTREAM_UNAVAILABLE, false, AI_SERVER_ERROR, 1"
    })
    void AI_오류_코드를_우리_코드로_바꾸고_retryable일_때만_최대_3번_보낸다(
        int status, String code, boolean retryable, AiErrorCode expected, int attempts) {
        for (int i = 0; i < 3; i++) {
            replyJson(status, ERROR_BODY.formatted(code, "AI 문구", retryable));
        }

        assertError(() -> client.architectures(ARCHITECTURE_REQUEST), expected);
        assertThat(requests).hasSize(attempts);
    }

    @ParameterizedTest
    @CsvSource({
        "429, AI_SERVER_UNAVAILABLE",
        "503, AI_SERVER_UNAVAILABLE",
        "504, AI_SERVER_TIMEOUT",
        "502, AI_SERVER_ERROR",
        "500, AI_SERVER_ERROR"
    })
    void JSON이_아닌_오류는_상태_코드로_판단하고_다시_보내지_않는다(int status, AiErrorCode expected) {
        reply(status, Map.of("Content-Type", "text/html"), "<html>proxy</html>".getBytes());

        assertError(() -> client.architectures(ARCHITECTURE_REQUEST), expected);
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
        "429, AI_SERVER_UNAVAILABLE",
        "503, AI_SERVER_UNAVAILABLE",
        "504, AI_SERVER_TIMEOUT",
        "502, AI_SERVER_ERROR"
    })
    void code가_없는_JSON_오류도_상태_코드로_판단하고_다시_보내지_않는다(int status, AiErrorCode expected) {
        replyJson(status, "{\"message\":\"Service Unavailable\"}");

        assertError(() -> client.architectures(ARCHITECTURE_REQUEST), expected);
        assertThat(requests).hasSize(1);
    }

    @Test
    void 재시도_가능_오류_뒤에_성공하면_성공_응답을_돌려준다() {
        replyJson(503, ERROR_BODY.formatted("UPSTREAM_UNAVAILABLE", "잠시 후", true));
        replyJson(200, "{\"architectures\":[]}");

        assertThat(client.architectures(ARCHITECTURE_REQUEST)).isEqualTo(json("{\"architectures\":[]}"));
        assertThat(requests).hasSize(2);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 파일_분석의_INVALID_REQUEST는_AI_문구를_고객에게_보여주되_AI_CALL_밖_로그에는_남기지_않는다(CapturedOutput output) {
        replyJson(400, ERROR_BODY.formatted("INVALID_REQUEST", "분석할 수 있는 파일이 없습니다: 고객파일.xlsx", false));

        assertThatThrownBy(() -> client.consultFiles(List.of()))
            .isInstanceOf(AiServerException.class)
            .hasMessage("분석할 수 있는 파일이 없습니다: 고객파일.xlsx")
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_INVALID_REQUEST);
        assertThat(otherLogs(output)).doesNotContain("고객파일");
    }

    @Test
    void 파일_분석이_아닌_INVALID_REQUEST는_우리_요청_오류라_AI_문구를_보여주지_않는다() {
        replyJson(400, ERROR_BODY.formatted("INVALID_REQUEST", "required_fields 에 알 수 없는 항목", false));

        assertThatThrownBy(() -> client.intake(INTAKE_REQUEST))
            .isInstanceOf(AiServerException.class)
            .hasMessage(AiErrorCode.AI_SERVER_ERROR.getMessage())
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void terraform_응답이_terraform_시간_제한을_넘으면_AI_SERVER_TIMEOUT이고_다시_보내지_않는다() {
        Duration spec = Duration.ofSeconds(5);
        AiServerClient slowClient = client(new AiServerClient.Timeouts(spec, spec, spec, spec, Duration.ofMillis(200)));
        replies.add(new Reply(200, Map.of(), Duration.ofSeconds(1), List.of(), index -> {
        }));

        assertError(() -> slowClient.terraform(TERRAFORM_REQUEST), AiErrorCode.AI_SERVER_TIMEOUT);
        assertThat(requests).hasSize(1);
    }

    @Test
    void 연결할_수_없으면_AI_SERVER_UNAVAILABLE() {
        server.stop(0);

        assertError(() -> client.intake(INTAKE_REQUEST), AiErrorCode.AI_SERVER_UNAVAILABLE);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void AI_CALL_로그는_본문_전체와_상태_시간_session_id를_남기고_헤더는_남기지_않는다(CapturedOutput output) {
        replyJson(200, "{\"reply\":\"안녕하세요\",\"missing_fields\":[],\"ready\":false}");

        client.intake(INTAKE_REQUEST);

        assertThat(aiCallLog(output, "ai_call POST /intake")).contains("attempt=1/3", "session_id=project-1",
            "request_bytes=58 request={\"history\":[],\"infra_context\":\"\",\"session_id\":\"project-1\"}",
            "status=200", "duration_ms=", "response={\"reply\":\"안녕하세요\",\"missing_fields\":[],\"ready\":false}")
            .containsPattern("response_bytes=\\d+").doesNotContain("Content-Type");
        assertThat(aiCallLog(output, "ai_call_end")).contains("POST /intake", "attempts=1/3", "result=OK");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void AI_CALL_로그는_재시도마다_시도_번호와_AI_오류_코드를_남긴다(CapturedOutput output) {
        replyJson(503, ERROR_BODY.formatted("UPSTREAM_UNAVAILABLE", "잠시 후", true));
        replyJson(422, ERROR_BODY.formatted("REFUSED", "거절", false));

        assertError(() -> client.architectures(ARCHITECTURE_REQUEST), AiErrorCode.AI_REQUEST_REFUSED);

        List<String> logs = aiCallLogs(output);
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0)).contains("ai_call POST /architectures attempt=1/3", "status=503",
            "ai_code=UPSTREAM_UNAVAILABLE retryable=true");
        assertThat(logs.get(1)).contains("ai_call POST /architectures attempt=2/3", "status=422",
            "ai_code=REFUSED retryable=false", "response={\"code\":\"REFUSED\",\"message\":\"거절\"");
        assertThat(logs.get(2)).contains("ai_call_end POST /architectures attempts=2/3", "result=AI004");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void AI_CALL_로그는_업로드_파일의_이름과_크기만_남긴다(CapturedOutput output) {
        replyJson(200, "{\"file_analysis\":{}}");

        client.consultFiles(List.of(
            new MockMultipartFile("upload", "main.tf", "text/plain", "resource {}".getBytes()),
            new MockMultipartFile("upload", "cost.csv", "text/csv", "a,b".getBytes())));

        assertThat(aiCallLog(output, "ai_call POST /consult/files"))
            .contains("request_files=[main.tf(11B), cost.csv(3B)]", "response={\"file_analysis\":{}}")
            .doesNotContain("resource {}", "request=");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void AI_CALL_로그는_terraform_파일의_내용_대신_이름_종류_크기를_남긴다(CapturedOutput output) {
        reply(200, Map.of("Content-Type", "application/zip",
            "Content-Disposition", "attachment; filename=\"serverless-terraform.zip\""),
            "PK-ZIP-CONTENT".getBytes(StandardCharsets.UTF_8));

        client.terraformFile(TERRAFORM_REQUEST, TerraformFormat.ZIP);

        assertThat(aiCallLog(output, "ai_call POST /terraform"))
            .contains("\"format\":\"zip\"", "response_bytes=14", "response_file=serverless-terraform.zip",
                "response_type=application/zip")
            .doesNotContain("PK-ZIP-CONTENT");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void AI_CALL_로그가_본문을_읽다_끊겨도_오류_분류는_그대로이다(CapturedOutput output) {
        Duration limit = Duration.ofMillis(300);
        AiServerClient slowClient = client(new AiServerClient.Timeouts(limit, limit, limit, limit, limit));
        replies.add(new Reply(200, Map.of("Content-Type", "application/json"), Duration.ZERO,
            List.of(line("{\"architectures\":"), line("[]}")), index -> {
                if (index == 1) {
                    sleep(1_000);
                }
            }));

        assertError(() -> slowClient.architectures(ARCHITECTURE_REQUEST), AiErrorCode.AI_SERVER_ERROR);
        assertThat(aiCallLog(output, "ai_call POST /architectures")).contains("status=200", "error=IOException");
        assertThat(requests).hasSize(1);
    }

    private static List<String> aiCallLogs(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains(" AI_CALL ")).toList();
    }

    private static String aiCallLog(CapturedOutput output, String marker) {
        List<String> logs = aiCallLogs(output).stream().filter(line -> line.contains(marker)).toList();
        assertThat(logs).hasSize(1);
        return logs.getFirst();
    }

    private static String otherLogs(CapturedOutput output) {
        return String.join("\n", output.getAll().lines().filter(line -> !line.contains(" AI_CALL ")).toList());
    }

    private AiServerClient client(AiServerClient.Timeouts timeouts) {
        return new AiServerClient("http://127.0.0.1:" + server.getAddress().getPort(), timeouts, Duration.ofMillis(10));
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(new Recorded(exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        Reply reply = replies.poll();
        if (reply == null) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        sleep(reply.delay().toMillis());
        exchange.sendResponseHeaders(reply.status(), 0);
        try (OutputStream body = exchange.getResponseBody()) {
            for (int i = 0; i < reply.chunks().size(); i++) {
                reply.beforeChunk().accept(i);
                body.write(reply.chunks().get(i));
                body.flush();
            }
        } catch (IOException ignored) {
            // 클라이언트가 시간 초과로 먼저 끊은 경우
        }
    }

    private void replyJson(int status, String body) {
        reply(status, Map.of("Content-Type", "application/json"), body.getBytes(StandardCharsets.UTF_8));
    }

    private void reply(int status, Map<String, String> headers, byte[] body) {
        replies.add(new Reply(status, headers, Duration.ZERO, List.of(body), index -> {
        }));
    }

    private void replyStream(String... lines) {
        replies.add(new Reply(200, NDJSON, Duration.ZERO, Arrays.stream(lines).map(AiServerClientTest::line).toList(),
            index -> {
            }));
    }

    private static byte[] line(String json) {
        return (json + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode json(String body) {
        return JsonMapper.shared().readTree(body);
    }

    private static void assertError(ThrowingCallable call, AiErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(AiServerException.class).extracting("errorCode").isEqualTo(expected);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
