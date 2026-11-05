package com.example.kkaribackend.domain.chat.presentation.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record ChatHistoryResponseDto(
    List<ChatMessageResponseDto> messages,
    List<String> missingFields,
    Boolean ready,
    IntakeProfileResponseDto profile
) {
}
