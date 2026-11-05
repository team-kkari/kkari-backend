package com.example.kkaribackend.global.config;

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
    // 추천 아키텍처 생성이 1~2.5분 걸린다
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(300);

    @Bean
    public RestClient aiRestClient(@Value("${ai-server.url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }
}
