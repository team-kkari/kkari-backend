package com.example.kkaribackend.global.ai.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AiServerException extends KkariException {
    public AiServerException(AiErrorCode errorCode) {
        super(errorCode);
    }

    public AiServerException(AiErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
