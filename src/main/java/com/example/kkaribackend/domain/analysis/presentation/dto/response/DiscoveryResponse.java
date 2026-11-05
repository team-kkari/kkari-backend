package com.example.kkaribackend.domain.analysis.presentation.dto.response;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot;

import java.time.LocalDateTime;

public record DiscoveryResponse(Long jobId, LocalDateTime analyzedAt, InfraSnapshot result) {
}
