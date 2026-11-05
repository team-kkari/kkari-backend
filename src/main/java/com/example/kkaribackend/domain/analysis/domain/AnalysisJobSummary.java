package com.example.kkaribackend.domain.analysis.domain;

import lombok.Builder;

// 상태 조회로 엔티티를 영속성 컨텍스트에 넣지 않도록 필요한 값만 읽는다.
@Builder
public record AnalysisJobSummary(Long id, AnalysisStatus status, String failureReason) {
    public static AnalysisJobSummary from(AnalysisJob job) {
        return new AnalysisJobSummary(job.getId(), job.getStatus(), job.getFailureReason());
    }
}
