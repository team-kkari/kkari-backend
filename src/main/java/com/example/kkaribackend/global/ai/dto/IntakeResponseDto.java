package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntakeResponseDto(
    String reply,
    @JsonProperty("quick_replies") List<String> quickReplies,
    IntakeProfile profile,
    List<String> missing,
    @JsonProperty("missing_fields") List<String> missingFields,
    boolean ready,
    @JsonProperty("recommend_request") JsonNode recommendRequest,
    @JsonIgnore JsonNode rawResponse
) {
}
