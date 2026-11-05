package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

// categories: null 이면 전체 문서에서 찾는다
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
    String question,
    List<AiMessage> history,
    List<String> categories
) {
}
