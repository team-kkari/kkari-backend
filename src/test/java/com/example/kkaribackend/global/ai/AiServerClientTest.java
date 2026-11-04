package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.RecommendRequest;
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
}
