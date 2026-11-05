package com.example.kkaribackend.domain.chat.presentation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SendChatMessageRequestDto(
    @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.") String content,
    @Size(max = 64, message = "메시지 식별자는 64자 이하여야 합니다.") String clientMessageId
) {
}
