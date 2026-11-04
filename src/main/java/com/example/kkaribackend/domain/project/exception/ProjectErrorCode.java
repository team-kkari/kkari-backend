package com.example.kkaribackend.domain.project.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ProjectErrorCode implements ErrorProperty {
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "프로젝트를 찾을 수 없습니다."),
    NOT_ASSIGNED_PROJECT(HttpStatus.FORBIDDEN, "P002", "담당하지 않는 프로젝트입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
