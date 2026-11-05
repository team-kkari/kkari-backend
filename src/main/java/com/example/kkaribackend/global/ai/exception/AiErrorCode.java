package com.example.kkaribackend.global.ai.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiErrorCode implements ErrorProperty {
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI001", "AI 서버 요청에 실패했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI002", "AI 서버 응답 시간이 초과되었습니다."),
    AI_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI003", "AI 서버가 일시적으로 응답할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    AI_REQUEST_REFUSED(HttpStatus.UNPROCESSABLE_CONTENT, "AI004", "AI 가 요청을 정책상 거절했습니다. 내용을 바꿔 다시 시도해 주세요."),
    AI_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AI005", "AI 서버가 처리할 수 없는 요청입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
