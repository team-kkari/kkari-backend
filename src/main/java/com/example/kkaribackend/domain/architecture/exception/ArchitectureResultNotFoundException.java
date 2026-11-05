package com.example.kkaribackend.domain.architecture.exception;

import com.example.kkaribackend.global.error.exception.KkariException;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobSummary;
import lombok.Getter;

@Getter
public class ArchitectureResultNotFoundException extends KkariException {
    private final AnalysisJobSummary latestJob;

    public ArchitectureResultNotFoundException() {
        this(null);
    }

    public ArchitectureResultNotFoundException(AnalysisJobSummary latestJob) {
        super(ArchitectureErrorCode.ARCHITECTURE_RESULT_NOT_FOUND);
        this.latestJob = latestJob;
    }
}
