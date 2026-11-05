package com.example.kkaribackend.domain.chat.presentation.dto.response;

import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import lombok.Builder;

import java.util.List;

@Builder
public record SendChatMessageResponseDto(
    ChatMessageResponseDto userMessage,
    ChatMessageResponseDto assistantMessage,
    List<String> missingFields,
    List<String> missing,
    Boolean ready,
    IntakeProfileResponseDto profile,
    RequirementsDraftResponseDto requirementsDraft
) {
    public static SendChatMessageResponseDto from(ChatMessage user, ChatMessage assistant) {
        return new SendChatMessageResponseDto(user == null ? null : ChatMessageResponseDto.from(user),
            ChatMessageResponseDto.from(assistant), assistant.getMissingFields(), assistant.getMissing(), assistant.getReady(),
            IntakeProfileResponseDto.from(assistant.getProfile()), RequirementsDraftResponseDto.from(assistant.getProfile()));
    }
}
