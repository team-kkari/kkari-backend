package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record RecommendResponse(
    JsonNode solution,
    List<Source> sources
) {
    public record Source(
        int n,
        String title,
        @JsonProperty("bp_id") String bpId,
        String category,
        String service,
        String url
    ) {
    }
}
