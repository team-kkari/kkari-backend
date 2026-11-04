package com.example.kkaribackend.global.security.jwt.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SecurityErrorCode implements ErrorProperty {
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "S001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "S002", "만료된 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "S003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "S004", "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
