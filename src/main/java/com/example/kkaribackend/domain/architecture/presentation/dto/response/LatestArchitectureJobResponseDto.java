package com.example.kkaribackend.domain.architecture.presentation.dto.response;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJobSummary;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "프로젝트의 가장 최근 설계 작업. 작업을 시작한 적이 없으면 null")
public record LatestArchitectureJobResponseDto(
    Long jobId,
    AnalysisStatus status,
    @Schema(description = "실패 사유. 실패하지 않았으면 null") String failureReason
) {
    public static LatestArchitectureJobResponseDto from(AnalysisJobSummary job) {
        return job == null ? null : new LatestArchitectureJobResponseDto(
            job.id(), job.status(), job.failureReason());
    }
}
