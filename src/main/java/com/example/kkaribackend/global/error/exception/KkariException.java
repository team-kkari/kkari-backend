package com.example.kkaribackend.global.error.exception;

import lombok.Getter;

@Getter
public class KkariException extends RuntimeException {
    private final ErrorProperty errorCode;
    public KkariException(ErrorProperty errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public KkariException(ErrorProperty errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
