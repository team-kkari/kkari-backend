package com.example.kkaribackend.global.ai.dto;

import lombok.Builder;

import java.util.List;

// infra: 한 줄에 리소스 하나. AI 서버가 줄 단위로 나눠 문서를 검색한다
// priorities: cost, performance, reliability, security 등. 앞에 있을수록 중요하다
@Builder
public record ArchitectureRequest(
    String infra,
    String goal,
    List<String> priorities,
    int count
) {
}
