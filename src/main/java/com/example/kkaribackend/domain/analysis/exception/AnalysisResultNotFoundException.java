package com.example.kkaribackend.domain.analysis.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AnalysisResultNotFoundException extends KkariException {
    public AnalysisResultNotFoundException() {
        super(AnalysisErrorCode.ANALYSIS_RESULT_NOT_FOUND);
    }
}
