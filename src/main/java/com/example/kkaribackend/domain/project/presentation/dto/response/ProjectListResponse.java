package com.example.kkaribackend.domain.project.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import lombok.Builder;
import com.example.kkaribackend.global.util.ResponseTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Builder
public record ProjectListResponse(
    Long projectId,
    String companyName,
    String contactName,
    ProjectStage stage,
    @Schema(description = "최근 성공한 인프라 분석의 월 예상 비용 합(USD). 미수집·부분 누락은 null")
    BigDecimal monthlyCost,
    @Schema(description = "최근 수정 시각. ISO 8601 offset 포함", example = "2026-11-05T16:00:00+09:00")
    OffsetDateTime updatedAt
) {
    public static ProjectListResponse from(Project project, BigDecimal monthlyCost) {
        return ProjectListResponse.builder()
            .projectId(project.getId())
            .companyName(project.getCompanyName())
            .contactName(project.getContactName())
            .stage(project.getStage())
            .monthlyCost(monthlyCost)
            .updatedAt(ResponseTime.withOffset(project.getUpdatedAt()))
            .build();
    }
}
