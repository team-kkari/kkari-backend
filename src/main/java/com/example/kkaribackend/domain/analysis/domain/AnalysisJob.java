package com.example.kkaribackend.domain.analysis.domain;

import com.example.kkaribackend.domain.project.domain.Project;
import jakarta.persistence.*;
import lombok.*;
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AnalysisStatus status = AnalysisStatus.QUEUED;

    // 진행 중인 단계. 시작 전에는 null
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AnalysisStep step;

    // InfraSnapshot JSON. PostgreSQL 에서는 jsonb
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    @Column(length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    // 파일 업로드처럼 요청 안에서 바로 끝나는 분석
    public static AnalysisJob succeeded(Project project, String result) {
        AnalysisJob job = AnalysisJob.builder().project(project).build();
        job.succeed(result);
        return job;
    }

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
