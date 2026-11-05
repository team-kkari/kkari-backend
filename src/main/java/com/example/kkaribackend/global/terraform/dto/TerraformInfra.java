package com.example.kkaribackend.global.terraform.dto;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;

import java.time.Instant;
import java.util.List;

/**
 * Terraform state 파일에서 읽은 리소스 구조.
 * AWS 수집 결과와 나란히 쓰도록 같은 Resources 모양과 리소스 ID 를 쓴다. 사용률·비용은 AWS 수집 결과에만 있다.
 * missing 에는 지원하지 않아 제외한 리소스를 남긴다.
 */
public record TerraformInfra(Resources resources, List<Missing> missing, Instant uploadedAt) {
}
