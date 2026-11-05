package com.example.kkaribackend.domain.chat.presentation.dto.response;

import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatErrorResponseDto(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    boolean retryable
) {
    public static ChatErrorResponseDto from(AiServerException exception) {
        ErrorProperty error = exception.getErrorCode();
        return new ChatErrorResponseDto(LocalDateTime.now(), error.getStatus().value(), error.getCode(),
            error.getMessage(), error == AiErrorCode.AI_SERVER_UNAVAILABLE || error == AiErrorCode.AI_SERVER_TIMEOUT);
    }
}
