package com.example.kkaribackend.global.aws.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AwsException extends KkariException {
    public AwsException(AwsErrorCode errorCode) {
        super(errorCode);
    }
}
