package com.example.kkaribackend.global.config;

import com.example.kkaribackend.global.ai.AiServerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServerConfig {

    @Bean
    public AiServerClient aiServerClient(@Value("${ai-server.url}") String baseUrl) {
        return new AiServerClient(baseUrl);
    }
}
