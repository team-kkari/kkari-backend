package com.example.kkaribackend.global.ai.dto;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

// name, diagram: 고객이 고른 architectures[i] 의 값을 그대로 넣는다
@Builder
public record TerraformRequest(
    String name,
    JsonNode diagram,
    JsonNode constraints
) {
}
