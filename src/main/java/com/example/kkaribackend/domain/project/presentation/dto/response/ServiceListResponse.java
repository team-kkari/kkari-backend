package com.example.kkaribackend.domain.project.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ServiceListResponse(
    Long serviceId,
    String serviceName,
    String engineerName,
    ProjectStage stage,
    LocalDateTime createdAt
) {
    public static ServiceListResponse from(Project project) {
        return ServiceListResponse.builder()
            .serviceId(project.getId())
            .serviceName(project.getServiceName())
            .engineerName(project.getUser().getName())
            .stage(project.getStage())
            .createdAt(project.getCreatedAt())
            .build();
    }
}
