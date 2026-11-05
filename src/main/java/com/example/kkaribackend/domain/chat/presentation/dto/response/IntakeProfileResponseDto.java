package com.example.kkaribackend.domain.chat.presentation.dto.response;

import com.example.kkaribackend.global.ai.dto.IntakeProfile;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record IntakeProfileResponseDto(
    String companyName,
    String workload,
    String architecture,
    String traffic,
    String availability,
    String performance,
    String cost,
    BigDecimal monthlyBudget,
    String currency,
    Boolean containsPersonalData,
    List<String> frozenResources,
    String goal,
    List<String> priorities,
    String changeTolerance,
    String constraints,
    String operations,
    String security
) {
    public static IntakeProfileResponseDto from(IntakeProfile profile) {
        if (profile == null) {
            return null;
        }
        return new IntakeProfileResponseDto(profile.companyName(), profile.workload(), profile.architecture(),
            profile.traffic(), profile.availability(), profile.performance(), profile.cost(), profile.monthlyBudget(),
            profile.currency(), profile.containsPersonalData(), profile.frozenResources(), profile.goal(),
            profile.priorities(), profile.changeTolerance(), profile.constraints(), profile.operations(), profile.security());
    }
}
