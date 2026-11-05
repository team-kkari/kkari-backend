package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.util.List;

// question: 요약·확인 턴이면 null. input 은 text | single | multi | rank | budget | yes_no
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultResponse(
    String reply,
    Question question,
    boolean ready,
    @JsonProperty("recommend_request") JsonNode recommendRequest,
    @JsonIgnore JsonNode rawResponse
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(String id, String label, String input, List<Option> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String value, String label) {
    }
}
