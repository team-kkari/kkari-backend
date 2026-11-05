package com.example.kkaribackend.global.config;

import com.example.kkaribackend.global.ai.AiServerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiServerConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    // 엔드포인트마다 read timeout 이 달라 요청 팩토리는 나누되, 연결 풀은 HttpClient 하나를 같이 쓴다
    @Bean
    public AiServerClient aiServerClient(@Value("${ai-server.url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        return new AiServerClient(RestClient.builder().baseUrl(baseUrl), readTimeout -> {
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(readTimeout);
            return requestFactory;
        }, RETRY_DELAY);
    }
}
