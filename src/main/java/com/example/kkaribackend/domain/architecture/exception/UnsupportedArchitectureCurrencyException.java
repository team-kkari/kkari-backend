package com.example.kkaribackend.domain.architecture.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class UnsupportedArchitectureCurrencyException extends KkariException {
    public UnsupportedArchitectureCurrencyException() {
        super(ArchitectureErrorCode.UNSUPPORTED_CURRENCY);
    }
}
