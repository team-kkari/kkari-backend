package com.example.kkaribackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// 실행기는 Spring Boot 기본 실행기를 쓰고 크기는 spring.task.execution 으로 정한다
@EnableAsync
@Configuration
public class AsyncConfig {
}
