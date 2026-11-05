package com.example.kkaribackend.domain.architecture.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AnalysisNotCompletedException extends KkariException {
    public AnalysisNotCompletedException() {
        super(ArchitectureErrorCode.ANALYSIS_NOT_COMPLETED);
    }
}
