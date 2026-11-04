package com.example.kkaribackend.global.security.jwt.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ExpiredTokenException extends KkariException {
    public ExpiredTokenException() {
        super(SecurityErrorCode.EXPIRED_TOKEN);
    }
}
