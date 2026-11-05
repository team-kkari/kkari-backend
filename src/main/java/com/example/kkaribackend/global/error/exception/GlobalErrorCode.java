package com.example.kkaribackend.global.error.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements ErrorProperty {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 메서드입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 내부 오류입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "요청한 리소스를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "C005", "지원하지 않는 미디어 타입입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "C006", "응답할 수 없는 미디어 타입입니다."),
    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "C007", "요청 크기가 너무 큽니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
