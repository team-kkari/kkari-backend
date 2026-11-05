package com.example.kkaribackend.domain.analysis.presentation.dto.response;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;

import java.time.LocalDateTime;

// terraform: 선택 입력인 Terraform state 에서 읽은 리소스 구조. 올리지 않았으면 null
public record DiscoveryResponse(Long jobId, LocalDateTime analyzedAt, InfraSnapshot result, TerraformInfra terraform) {
}
