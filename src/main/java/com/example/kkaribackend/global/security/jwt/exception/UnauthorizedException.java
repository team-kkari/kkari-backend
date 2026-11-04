package com.example.kkaribackend.global.security.jwt.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class UnauthorizedException extends KkariException {
    public UnauthorizedException() {
        super(SecurityErrorCode.UNAUTHORIZED);
    }
}
