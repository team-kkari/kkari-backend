package com.example.kkaribackend.domain.analysis.presentation.dto.response;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStep;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record AnalysisJobResponse(
    Long jobId,
    AnalysisStatus status,
    AnalysisStep step,
    // 종류별 리소스 수. 성공 전에는 null, 수집 실패한 종류는 값이 null
    Map<String, Integer> resourceCounts,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
    public static AnalysisJobResponse of(AnalysisJob job, Map<String, Integer> resourceCounts) {
        return AnalysisJobResponse.builder()
            .jobId(job.getId())
            .status(job.getStatus())
            .step(job.getStep())
            .resourceCounts(resourceCounts)
            .failureReason(job.getFailureReason())
            .createdAt(job.getCreatedAt())
            .finishedAt(job.getFinishedAt())
            .build();
    }
}
