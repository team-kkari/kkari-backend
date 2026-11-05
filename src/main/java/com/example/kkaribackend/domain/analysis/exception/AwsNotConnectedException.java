package com.example.kkaribackend.domain.analysis.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AwsNotConnectedException extends KkariException {
    public AwsNotConnectedException() {
        super(AnalysisErrorCode.AWS_NOT_CONNECTED);
    }
}
