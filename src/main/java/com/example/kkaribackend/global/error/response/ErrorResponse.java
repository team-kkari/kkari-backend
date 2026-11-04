package com.example.kkaribackend.global.error.response;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ErrorResponse (
    LocalDateTime timestamp,
    int status,
    String code,
    String message
) {
    public static ErrorResponse of(ErrorProperty errorCode, String message) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.getStatus().value())
            .code(errorCode.getCode())
            .message(message)
            .build();
    }

    public static ErrorResponse of(ErrorProperty errorCode) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.getStatus().value())
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .build();
    }
}
