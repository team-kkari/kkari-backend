package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

// question: 요약·확인 턴이면 null. input 은 text | single | multi | rank | budget | yes_no
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultResponse(
    String reply,
    Question question,
    @JsonProperty("quick_replies") List<String> quickReplies,
    JsonNode profile,
    List<String> missing,
    @JsonProperty("missing_labels") List<String> missingLabels,
    @JsonProperty("recommended_missing") List<String> recommendedMissing,
    boolean ready,
    @JsonProperty("recommend_request") JsonNode recommendRequest
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(String id, int step, String label, String priority, String input, List<Option> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String value, String label) {
    }
}
