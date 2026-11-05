package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

// infra: 한 줄에 리소스 하나. AI 서버가 줄 단위로 나눠 문서를 검색한다
// priorities: cost, performance, reliability, security 등. 앞에 있을수록 중요하다
// constraints: 상담 recommend_request.constraints 를 그대로 넣는다. monthly_budget 이 있으면 currency 도 있어야 한다
// currentMonthlyUsd: 주면 AI 가 현재 비용을 다시 추정하지 않고 "현재 대비" 의 기준으로 쓴다
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArchitectureRequest(
    String infra,
    String goal,
    List<String> priorities,
    JsonNode constraints,
    int count,
    @JsonProperty("current_monthly_usd") BigDecimal currentMonthlyUsd
) {
}
