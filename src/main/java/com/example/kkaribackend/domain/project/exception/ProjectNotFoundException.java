package com.example.kkaribackend.domain.project.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ProjectNotFoundException extends KkariException {
    public ProjectNotFoundException() {
        super(ProjectErrorCode.PROJECT_NOT_FOUND);
    }
}
