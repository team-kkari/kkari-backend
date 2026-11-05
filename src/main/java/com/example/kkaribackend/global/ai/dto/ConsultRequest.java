package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.util.List;

// fileAnalysis: consultFiles 결과를 그대로 넣는다. 파일을 올리지 않았으면 null
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsultRequest(
    List<AiMessage> history,
    @JsonProperty("file_analysis") JsonNode fileAnalysis,
    @JsonProperty("session_id") String sessionId
) {
}
