package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record IntakeRequestDto(
    List<AiMessage> history,
    @JsonProperty("infra_context") String infraContext,
    @JsonProperty("session_id") String sessionId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("required_fields") List<String> requiredFields,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("infra_status") String infraStatus
) {
}
