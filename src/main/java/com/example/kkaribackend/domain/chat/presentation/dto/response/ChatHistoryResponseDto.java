package com.example.kkaribackend.domain.chat.presentation.dto.response;

import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Builder
public record ChatHistoryResponseDto(
    List<ChatMessageResponseDto> messages,
    @Schema(description = "profile과 동일한 camelCase 필드명", example = "[\"monthlyBudget\",\"containsPersonalData\"]")
    List<String> missingFields,
    List<String> missing,
    Boolean ready,
    IntakeProfileResponseDto profile,
    RequirementsDraftResponseDto requirementsDraft
) {
}
