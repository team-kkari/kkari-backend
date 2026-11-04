package com.example.kkaribackend.domain.auth.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements ErrorProperty {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "이메일 혹은 비밀번호가 맞지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
