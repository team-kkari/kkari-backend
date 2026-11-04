package com.example.kkaribackend.domain.analysis.domain;

import java.util.List;

public enum AnalysisStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public static final List<AnalysisStatus> IN_PROGRESS = List.of(QUEUED, RUNNING);
}
