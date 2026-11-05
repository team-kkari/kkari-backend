package com.example.kkaribackend.domain.analysis.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AnalysisErrorCode implements ErrorProperty {
    AWS_NOT_CONNECTED(HttpStatus.CONFLICT, "AN001", "AWS 연결 테스트에 성공(CONNECTED)한 프로젝트만 분석할 수 있습니다."),
    ANALYSIS_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "AN002", "분석 작업을 찾을 수 없습니다."),
    ANALYSIS_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "AN003", "완료된 분석 결과가 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
