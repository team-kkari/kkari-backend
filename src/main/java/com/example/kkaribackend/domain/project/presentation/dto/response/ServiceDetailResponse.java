package com.example.kkaribackend.domain.project.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record ServiceDetailResponse(
    Long serviceId,
    String serviceName,
    Map<String, String> answers,
    String engineerName,
    ProjectStage stage,
    LocalDateTime createdAt
) {
    public static ServiceDetailResponse from(Project project) {
        return ServiceDetailResponse.builder()
            .serviceId(project.getId())
            .serviceName(project.getServiceName())
            .answers(project.getAnswers())
            .engineerName(project.getUser().getName())
            .stage(project.getStage())
            .createdAt(project.getCreatedAt())
            .build();
    }
}
