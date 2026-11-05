package com.example.kkaribackend.domain.architecture.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// count: 추천 개수. 비우면 5
public record StartArchitectureRequest(
    @Min(value = 1, message = "추천 개수는 1 이상이어야 합니다.")
    @Max(value = 5, message = "추천 개수는 5 이하여야 합니다.")
    Integer count
) {
}
