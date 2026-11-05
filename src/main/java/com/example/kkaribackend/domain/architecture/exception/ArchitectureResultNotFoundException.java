package com.example.kkaribackend.domain.architecture.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ArchitectureResultNotFoundException extends KkariException {
    public ArchitectureResultNotFoundException() {
        super(ArchitectureErrorCode.ARCHITECTURE_RESULT_NOT_FOUND);
    }
}
