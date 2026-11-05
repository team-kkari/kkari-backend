package com.example.kkaribackend.domain.architecture.presentation.dto.response;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;

// result: AI 서버 응답 JSON 그대로
@Builder
public record ArchitectureResponse(
    @Schema(description = "마지막으로 성공한 설계 작업 ID") Long jobId,
    @Schema(description = "성공한 결과의 생성 시각. ISO 8601 offset 포함", example = "2026-11-05T16:00:00+09:00")
    OffsetDateTime generatedAt,
    JsonNode result,
    LatestArchitectureJobResponseDto latestJob
) {
}
