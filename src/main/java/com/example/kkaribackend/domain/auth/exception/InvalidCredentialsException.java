package com.example.kkaribackend.domain.auth.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class InvalidCredentialsException extends KkariException {
    public InvalidCredentialsException() {
        super(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
