package com.example.kkaribackend.global.security.jwt.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class InvalidTokenException extends KkariException {
    public InvalidTokenException() {
        super(SecurityErrorCode.INVALID_TOKEN);
    }
}
