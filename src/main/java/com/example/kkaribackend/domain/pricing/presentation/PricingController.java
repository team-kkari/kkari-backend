package com.example.kkaribackend.domain.pricing.presentation;

import com.example.kkaribackend.domain.pricing.presentation.dto.request.EstimateCostRequest;
import com.example.kkaribackend.domain.pricing.presentation.dto.response.CostReport;
import com.example.kkaribackend.domain.pricing.service.EstimateArchitectureCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal")
@RequiredArgsConstructor
@RequestMapping("/v1/internal/pricing")
@RestController
public class PricingController {
    private final EstimateArchitectureCostService estimateArchitectureCostService;

    @Operation(summary = "AI 설계안 월 예상 비용 계산 (서버 간 전용)",
        description = "AI 서버가 설계안 구성요소(nodes)를 보내면 서울 리전 AWS Price List 온디맨드 단가(할인 전 정가)로 "
            + "월 비용을 계산해 AI 서버 CostReport 모양으로 돌려준다. JWT 가 아니라 X-Internal-Token 헤더로 인증한다. "
            + "단가는 처음 쓸 때 Price List API 에서 받아 DB 에 두고 30일마다 갱신하며, 구간 요금은 누적 계산한다. "
            + "가격을 찾지 못한 구성요소는 monthly_usd 가 null 이고 unpriced 에 들어가며 합계에서 빠진다. "
            + "저장된 단가가 없는데 Price List API 호출이 실패하면 일부만 계산된 결과 대신 502(AW001)를 돌려준다. "
            + "nodes 는 최대 30개이고 Price List API 호출 하나는 최대 10초다.")
    @PostMapping("/estimate")
    public CostReport estimate(@Valid @RequestBody EstimateCostRequest request) {
        return estimateArchitectureCostService.execute(request);
    }
}
