package com.example.kkaribackend.domain.analysis.domain;

import com.example.kkaribackend.domain.project.domain.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Table(name = "tbl_analysis_job")
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // type 이 생기기 전에 만든 행은 모두 인프라 분석이라, 컬럼을 추가할 때 DB 기본값으로 채운다
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ANALYSIS'")
    @Column(length = 20, nullable = false)
    private AnalysisJobType type = AnalysisJobType.ANALYSIS;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AnalysisStatus status = AnalysisStatus.QUEUED;

    // 진행 중인 단계. 시작 전과 추천 아키텍처 작업은 null
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AnalysisStep step;

    // ANALYSIS: InfraSnapshot JSON, ARCHITECTURE: AI 서버 응답 JSON. PostgreSQL 에서는 jsonb
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    @Column(length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    public void start() {
        this.status = AnalysisStatus.RUNNING;
    }

    public void moveTo(AnalysisStep step) {
        this.step = step;
    }

    public void succeed(String result) {
        this.status = AnalysisStatus.SUCCEEDED;
        this.result = result;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String failureReason) {
        this.status = AnalysisStatus.FAILED;
        this.failureReason = failureReason;
        this.finishedAt = LocalDateTime.now();
    }
}
