package com.example.kkaribackend.domain.analysis.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AnalysisJobNotFoundException extends KkariException {
    public AnalysisJobNotFoundException() {
        super(AnalysisErrorCode.ANALYSIS_JOB_NOT_FOUND);
    }
}
