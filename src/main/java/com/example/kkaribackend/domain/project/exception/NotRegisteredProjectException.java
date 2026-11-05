package com.example.kkaribackend.domain.project.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class NotRegisteredProjectException extends KkariException {
    public NotRegisteredProjectException() {
        super(ProjectErrorCode.NOT_REGISTERED_PROJECT);
    }
}
