package com.example.kkaribackend.domain.requirement.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.Priority;
import com.example.kkaribackend.domain.project.domain.Requirements;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record RequirementsResponse(
    String content,
    List<Priority> priorities,
    String desiredArchitecture,
    BigDecimal monthlyBudget,
    String currency,
    boolean containsPersonalData,
    List<String> frozenResources
) {
    public static RequirementsResponse from(Requirements requirements) {
        return RequirementsResponse.builder()
            .content(requirements.content())
            .priorities(requirements.priorities())
            .desiredArchitecture(requirements.desiredArchitecture())
            .monthlyBudget(requirements.monthlyBudget())
            .currency(requirements.currency())
            .containsPersonalData(requirements.containsPersonalData())
            .frozenResources(requirements.frozenResources())
            .build();
    }
}
