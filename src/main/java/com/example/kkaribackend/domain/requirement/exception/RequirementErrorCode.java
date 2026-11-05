package com.example.kkaribackend.domain.requirement.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RequirementErrorCode implements ErrorProperty {
    REQUIREMENTS_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "아직 입력된 요구사항이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
