package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServerClientTest {

    private static final ArchitectureRequest REQUEST = ArchitectureRequest.builder()
        .infra("EC2 i-1: t3.micro")
        .goal("")
        .priorities(List.of("cost"))
        .count(3)
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
    void architectures_성공시_응답_JSON을_돌려준다() {
        String body = """
            {"overall":"요약", "architectures":[{"rank":1,"name":"서버리스","cost":{"monthly_total_usd":12.5}}],
             "failed":[], "timings_s":{"total":90.1}}""";
        server.expect(requestTo("http://ai/architectures"))
            .andExpect(method(POST))
            .andExpect(content().json("""
                {"infra":"EC2 i-1: t3.micro","goal":"","priorities":["cost"],"count":3}
                """, JsonCompareMode.STRICT))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.architectures(REQUEST)).isEqualTo(new ObjectMapper().readTree(body));
        server.verify();
    }

    @Test
    void architectures_429면_AI_SERVER_BUSY() {
        server.expect(requestTo("http://ai/architectures"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"detail\":\"rate limited\"}"));

        assertThatThrownBy(() -> client.architectures(REQUEST))
            .isInstanceOf(AiServerException.class)
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_BUSY);
    }

    @Test
    void architectures_응답이_비어_있으면_AI_SERVER_ERROR() {
        server.expect(requestTo("http://ai/architectures"))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.architectures(REQUEST))
            .isInstanceOf(AiServerException.class)
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void architectures_5xx면_AI_SERVER_ERROR() {
        server.expect(requestTo("http://ai/architectures"))
            .andRespond(withServerError().body("{\"detail\":\"generation failed\"}"));

        assertThatThrownBy(() -> client.architectures(REQUEST))
            .isInstanceOf(AiServerException.class)
            .extracting("errorCode").isEqualTo(AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void architectures_응답시간_초과면_AI_SERVER_TIMEOUT() throws Exception {
        HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/architectures", exchange -> {
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

            assertThatThrownBy(() -> slowClient.architectures(REQUEST))
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
}
