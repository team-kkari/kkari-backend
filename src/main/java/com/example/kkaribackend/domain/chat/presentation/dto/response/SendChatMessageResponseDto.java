package com.example.kkaribackend.domain.chat.presentation.dto.response;

import com.example.kkaribackend.domain.chat.domain.ChatMessage;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Builder
public record SendChatMessageResponseDto(
    ChatMessageResponseDto userMessage,
    ChatMessageResponseDto assistantMessage,
    @Schema(description = "profile과 동일한 camelCase 필드명", example = "[\"monthlyBudget\",\"containsPersonalData\"]")
    List<String> missingFields,
    List<String> missing,
    Boolean ready,
    IntakeProfileResponseDto profile,
    RequirementsDraftResponseDto requirementsDraft
) {
    public static SendChatMessageResponseDto from(ChatMessage user, ChatMessage assistant) {
        return new SendChatMessageResponseDto(user == null ? null : ChatMessageResponseDto.from(user),
            ChatMessageResponseDto.from(assistant), IntakeProfileResponseDto.toProfileFieldNames(assistant.getMissingFields()),
            assistant.getMissing(), assistant.getReady(),
            IntakeProfileResponseDto.from(assistant.getProfile()), RequirementsDraftResponseDto.from(assistant.getProfile()));
    }
}
