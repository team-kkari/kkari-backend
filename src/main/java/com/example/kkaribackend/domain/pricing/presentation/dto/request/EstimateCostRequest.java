package com.example.kkaribackend.domain.pricing.presentation.dto.request;

import com.example.kkaribackend.global.aws.dto.ArchitectureNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 노드 수를 묶어 한 요청이 보내는 가격 계산 줄 수를 제한한다
public record EstimateCostRequest(
    @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid ArchitectureNode> nodes
) {
}
