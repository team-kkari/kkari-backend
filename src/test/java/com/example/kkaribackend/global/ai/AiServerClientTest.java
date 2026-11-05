package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.RecommendRequest;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import com.example.kkaribackend.global.ai.dto.RecommendResponse;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServerClientTest {

    private static final RecommendRequest REQUEST = RecommendRequest.builder()
        .infra("EC2 1대")
        .goal("")
        .priorities(List.of("cost"))
        .build();

    private MockRestServiceServer server;
    private AiServerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServerClient(builder.build());
    }

    @Test
    void recommend_성공시_solution과_sources를_파싱한다() {
        server.expect(requestTo("http://ai/recommend"))
            .andExpect(method(POST))
            .andExpect(content().json("""
                {"infra":"EC2 1대","goal":"","priorities":["cost"]}
                """))
            .andRespond(withSuccess("""
                {"solution":{"summary":"요약","items":[1,2]},
                 "sources":[{"n":1,"title":"t","bp_id":null,"category":"c","service":"EC2","url":"u","extra":1}],
                 "extra":true}
                """, MediaType.APPLICATION_JSON));

        RecommendResponse response = client.recommend(REQUEST);

        assertThat(response.solution().get("summary").asString()).isEqualTo("요약");
        assertThat(response.sources()).containsExactly(
            new RecommendResponse.Source(1, "t", null, "c", "EC2", "u"));
        server.verify();
    }

    @Test
    void recommend_5xx면_AI_SERVER_ERROR() {
        server.expect(requestTo("http://ai/recommend"))
            .andRespond(withServerError().body("{\"detail\":\"LLM failed\"}"));

        assertThatThrownBy(() -> client.recommend(REQUEST))
            .isInstanceOf(AiServerException.class)
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void recommend_응답시간_초과면_AI_SERVER_TIMEOUT() throws Exception {
        HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/recommend", exchange -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException ignored) {
            }
            exchange.close();
        });
        slowServer.start();
        try {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofMillis(200));
            AiServerClient slowClient = new AiServerClient(RestClient.builder()
                .baseUrl("http://127.0.0.1:" + slowServer.getAddress().getPort())
                .requestFactory(factory)
                .build());

            assertThatThrownBy(() -> slowClient.recommend(REQUEST))
                .isInstanceOf(AiServerException.class)
                .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_TIMEOUT);
        } finally {
            slowServer.stop(0);
        }
    }

    @Test
    void 연결_실패면_AI_SERVER_ERROR() {
        AiServerClient downClient = new AiServerClient(RestClient.builder()
            .baseUrl("http://127.0.0.1:1")
            .requestFactory(new JdkClientHttpRequestFactory())
            .build());

        assertThatThrownBy(downClient::health)
            .isInstanceOf(AiServerException.class)
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
    }
    @Test
    void intake_요청은_snake_case이고_새_응답_필드는_무시한다() {
        server.expect(requestTo("http://ai/intake"))
            .andExpect(method(POST))
            .andExpect(content().json("""
                {"history":[{"role":"user","content":"안녕"}],"infra_context":"EC2 1대","session_id":"project-12"}
                """, org.springframework.test.json.JsonCompareMode.STRICT))
            .andRespond(withSuccess("""
                {"reply":"질문","quick_replies":["예"],"profile":{"company_name":"고객사",
                 "monthly_budget":3000000.0,"contains_personal_data":null,"frozen_resources":[],
                 "currency":"KRW","change_tolerance":"허용","new_field":true},
                 "missing":["고객사명"],"missing_fields":["company_name"],"ready":false,
                 "recommend_request":{"infra":"EC2","constraints":{"monthly_budget":0}},"extra":true}
                """, MediaType.APPLICATION_JSON));

        IntakeResponseDto response = client.intake(new IntakeRequestDto(
            List.of(new IntakeRequestDto.Message("user", "안녕")), "EC2 1대", "project-12", null));

        assertThat(response.reply()).isEqualTo("질문");
        assertThat(response.quickReplies()).containsExactly("예");
        assertThat(response.profile().companyName()).isEqualTo("고객사");
        assertThat(response.profile().monthlyBudget()).isEqualByComparingTo("3000000.0");
        assertThat(response.profile().containsPersonalData()).isNull();
        assertThat(response.profile().frozenResources()).isEmpty();
        assertThat(response.profile().changeTolerance()).isEqualTo("허용");
        assertThat(response.missing()).containsExactly("고객사명");
        assertThat(response.missingFields()).containsExactly("company_name");
        assertThat(response.ready()).isFalse();
        assertThat(response.recommendRequest().get("infra").asString()).isEqualTo("EC2");
        assertThat(response.rawResponse().get("extra").asBoolean()).isTrue();
        assertThat(response.rawResponse().get("profile").get("new_field").asBoolean()).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "500, '{\"retryable\":true}', AI_SERVER_UNAVAILABLE",
        "503, '{\"retryable\":false}', AI_SERVER_ERROR",
        "500, '{\"retryable\":false}', AI_SERVER_ERROR",
        "503, '{}', AI_SERVER_ERROR",
        "429, '{\"retryable\":false}', AI_SERVER_ERROR",
        "429, not-json, AI_SERVER_UNAVAILABLE",
        "503, not-json, AI_SERVER_UNAVAILABLE",
        "500, not-json, AI_SERVER_ERROR",
        "500, '{\"message\":\"legacy\"}', AI_SERVER_ERROR"
    })
    void 오류_본문과_상태로_재시도_가능_여부를_구분한다(int status, String body, AiErrorCode expected) {
        server.expect(requestTo("http://ai/recommend"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                .withStatus(HttpStatus.valueOf(status)).body(body).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.recommend(REQUEST))
            .isInstanceOf(AiServerException.class).extracting("errorCode").isEqualTo(expected);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"reply\":null}", "{\"reply\":\"  \"}"})
    void intake의_빈_성공_응답은_AI_오류로_처리한다(String body) {
        server.expect(requestTo("http://ai/intake"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.intake(new IntakeRequestDto(List.of(), "", "project-1", null)))
            .isInstanceOf(AiServerException.class).extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
        server.verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void intake_파싱_실패는_AI_오류이며_응답_원문을_로그에_남기지_않는다(CapturedOutput output) {
        server.expect(requestTo("http://ai/intake"))
            .andRespond(withSuccess("{\"reply\":PRIVATE_AI_RESPONSE}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.intake(new IntakeRequestDto(List.of(), "", "project-1", null)))
            .isInstanceOf(AiServerException.class).extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
        assertThat(output.getAll()).doesNotContain("PRIVATE_AI_RESPONSE");
        server.verify();
    }

    @Test
    void intake의_ready와_누락_항목은_독립적이고_정형_제약은_보존한다() {
        server.expect(requestTo("http://ai/intake"))
            .andRespond(withSuccess("""
                {"reply":"누락 정보가 있으니 확인해 주세요","quick_replies":[],
                 "profile":{"monthly_budget":0,"currency":null,"contains_personal_data":false,"frozen_resources":[]},
                 "missing":["워크로드"],"missing_fields":["workload"],"ready":true,
                 "recommend_request":{"infra":"EC2","goal":"","priorities":["cost"],
                 "constraints":{"monthly_budget":0,"currency":null,"contains_personal_data":false,"frozen_resources":[]}}}
                """, MediaType.APPLICATION_JSON));

        IntakeResponseDto response = client.intake(new IntakeRequestDto(List.of(), "", "project-1", null));

        assertThat(response.ready()).isTrue();
        assertThat(response.missingFields()).containsExactly("workload");
        assertThat(response.missing()).containsExactly("워크로드");
        var constraints = response.recommendRequest().get("constraints");
        assertThat(constraints.get("monthly_budget").asInt()).isZero();
        assertThat(constraints.get("currency").isNull()).isTrue();
        assertThat(constraints.get("contains_personal_data").asBoolean()).isFalse();
        assertThat(constraints.get("frozen_resources").isArray()).isTrue();
        assertThat(constraints.get("frozen_resources").isEmpty()).isTrue();
        server.verify();
    }

}
