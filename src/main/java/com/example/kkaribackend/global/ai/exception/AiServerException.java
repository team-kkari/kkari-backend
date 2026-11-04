package com.example.kkaribackend.global.ai.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class AiServerException extends KkariException {
    public AiServerException(AiErrorCode errorCode) {
        super(errorCode);
    }
}
