package com.example.kkaribackend.domain.architecture.presentation.dto.response;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

// result: AI 서버 응답 JSON 그대로
public record ArchitectureResponse(Long jobId, LocalDateTime generatedAt, JsonNode result) {
}
