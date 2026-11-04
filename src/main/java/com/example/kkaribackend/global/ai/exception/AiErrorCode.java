package com.example.kkaribackend.global.ai.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiErrorCode implements ErrorProperty {
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI001", "AI 서버 요청에 실패했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI002", "AI 서버 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
