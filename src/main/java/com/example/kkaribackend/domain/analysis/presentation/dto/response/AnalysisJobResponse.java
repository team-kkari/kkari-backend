package com.example.kkaribackend.domain.analysis.presentation.dto.response;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStep;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AnalysisJobResponse(
    Long jobId,
    AnalysisJobType type,
    AnalysisStatus status,
    AnalysisStep step,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
    public static AnalysisJobResponse of(AnalysisJob job) {
        return AnalysisJobResponse.builder()
            .jobId(job.getId())
            .type(job.getType())
            .status(job.getStatus())
            .step(job.getStep())
            .failureReason(job.getFailureReason())
            .createdAt(job.getCreatedAt())
            .finishedAt(job.getFinishedAt())
            .build();
    }
}
