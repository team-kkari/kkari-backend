package com.example.kkaribackend.domain.chat.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements ErrorProperty {
    CHAT_TURN_NOT_ALLOWED(HttpStatus.CONFLICT, "CH001", "지금은 챗봇 응답을 요청할 수 없습니다."),
    CONCURRENT_CHAT_MESSAGE(HttpStatus.CONFLICT, "CH002", "다른 메시지와 동시에 전송되었습니다. 대화를 새로고침해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
