package com.example.kkaribackend.domain.architecture.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ArchitectureErrorCode implements ErrorProperty {
    ANALYSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "AR001", "인프라 분석에 성공한 프로젝트만 추천 아키텍처를 생성할 수 있습니다."),
    ARCHITECTURE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "AR002", "생성된 추천 아키텍처가 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
