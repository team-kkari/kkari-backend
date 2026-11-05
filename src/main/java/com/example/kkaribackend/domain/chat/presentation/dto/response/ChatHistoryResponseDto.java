package com.example.kkaribackend.domain.chat.presentation.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record ChatHistoryResponseDto(
    List<ChatMessageResponseDto> messages,
    List<String> missingFields,
    List<String> missing,
    Boolean ready,
    IntakeProfileResponseDto profile
) {
}
