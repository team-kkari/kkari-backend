package com.example.kkaribackend.domain.pricing.service;

import com.example.kkaribackend.domain.pricing.presentation.dto.request.EstimateCostRequest;
import com.example.kkaribackend.global.aws.ArchitectureCostEstimator;
import com.example.kkaribackend.global.aws.dto.CostReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class EstimateArchitectureCostService {
    private final ArchitectureCostEstimator architectureCostEstimator;

    public CostReport execute(EstimateCostRequest request) {
        return architectureCostEstimator.estimate(request.nodes());
    }
}
