package com.example.kkaribackend.global.aws.dto;

import lombok.Builder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 서버 CostReport 와 같은 모양의 설계안 월 예상 비용(USD).
 * 추정할 수 없는 구성요소는 0 이 아니라 null 이고, unpriced 에 label 이 들어가며 합계에서 빠진다.
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CostReport(
    BigDecimal monthlyTotalUsd,
    List<Item> items,
    List<String> unpriced,
    List<String> assumptions
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(String nodeId, String label, String kind, BigDecimal monthlyUsd, String formula) {
    }
}
