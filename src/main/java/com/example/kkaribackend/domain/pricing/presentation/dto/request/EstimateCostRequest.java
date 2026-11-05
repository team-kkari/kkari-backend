package com.example.kkaribackend.domain.pricing.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 노드 하나가 가격 계산 후보를 최대 4개 만든다. 30개로 묶어 배치 호출을 최대 5번으로 제한한다
public record EstimateCostRequest(
    @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid ArchitectureNode> nodes
) {
}
