package com.example.kkaribackend.global.security.jwt.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ForbiddenException extends KkariException {
    public ForbiddenException() {
        super(SecurityErrorCode.FORBIDDEN);
    }
}
