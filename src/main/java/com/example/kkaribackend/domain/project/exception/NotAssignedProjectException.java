package com.example.kkaribackend.domain.project.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class NotAssignedProjectException extends KkariException {
    public NotAssignedProjectException() {
        super(ProjectErrorCode.NOT_ASSIGNED_PROJECT);
    }
}
