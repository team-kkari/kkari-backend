package com.example.kkaribackend.domain.chat.presentation.dto.response;

import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import com.example.kkaribackend.domain.chat.domain.ChatRole;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ChatMessageResponseDto(
    Long id,
    ChatRole role,
    String content,
    List<String> quickReplies,
    LocalDateTime createdAt
) {
    public static ChatMessageResponseDto from(ChatMessage message) {
        return new ChatMessageResponseDto(message.getId(), message.getRole(), message.getContent(),
            message.getQuickReplies(), message.getCreatedAt());
    }
}
