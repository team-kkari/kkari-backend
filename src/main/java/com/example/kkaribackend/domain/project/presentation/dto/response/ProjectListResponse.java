package com.example.kkaribackend.domain.project.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ProjectListResponse(
    Long projectId,
    String companyName,
    String contactName,
    ProjectStage stage,
    // 비용 수집 전에는 null (0원이 아님)
    BigDecimal monthlyCost,
    LocalDateTime updatedAt
) {
    public static ProjectListResponse from(Project project) {
        return ProjectListResponse.builder()
            .projectId(project.getId())
            .companyName(project.getCompanyName())
            .contactName(project.getContactName())
            .stage(project.getStage())
            .monthlyCost(null)
            .updatedAt(project.getUpdatedAt())
            .build();
    }
}
