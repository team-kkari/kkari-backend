package com.example.kkaribackend.global.ai.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record RecommendRequest(
    String infra,
    String goal,
    List<String> priorities
) {
}
