package com.example.kkaribackend.domain.pricing.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 노드 하나가 단가 후보를 최대 4개 만든다. 저장되지 않은 단가는 하나씩 Price List 에서 받으므로 한 요청의 호출 수를 묶어 둔다
public record EstimateCostRequest(
    @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid ArchitectureNode> nodes
) {
}
