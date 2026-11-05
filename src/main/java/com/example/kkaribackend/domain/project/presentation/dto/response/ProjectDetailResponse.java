package com.example.kkaribackend.domain.project.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record ProjectDetailResponse(
    Long projectId,
    String serviceName,
    String companyName,
    String contactName,
    Map<String, String> answers,
    ProjectStage stage,
    AwsConnectionStatus awsConnectionStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProjectDetailResponse from(Project project) {
        return ProjectDetailResponse.builder()
            .projectId(project.getId())
            .serviceName(project.getServiceName())
            .companyName(project.getCompanyName())
            .contactName(project.getContactName())
            .answers(project.getAnswers())
            .stage(project.getStage())
            .awsConnectionStatus(project.getAwsConnectionStatus())
            .createdAt(project.getCreatedAt())
            .updatedAt(project.getUpdatedAt())
            .build();
    }
}
