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
    String security,
    String designPrinciples
) {
    // DB와 AI 원본은 snake_case로 보존하고, FE 응답의 식별자만 profile과 같은 이름으로 바꾼다.
    public static List<String> toProfileFieldNames(List<String> fields) {
        return fields == null ? List.of() : fields.stream().map(IntakeProfileResponseDto::camelCase).toList();
    }

    private static String camelCase(String field) {
        if (field == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char letter : field.toCharArray()) {
            if (letter == '_') {
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(letter) : letter);
                upper = false;
            }
        }
        return result.toString();
    }

    public static IntakeProfileResponseDto from(IntakeProfile profile) {
        if (profile == null) {
            return null;
        }
        return new IntakeProfileResponseDto(profile.companyName(), profile.workload(), profile.architecture(),
            profile.traffic(), profile.availability(), profile.performance(), profile.cost(), profile.monthlyBudget(),
            profile.currency(), profile.containsPersonalData(), profile.frozenResources(), profile.goal(),
            profile.priorities(), profile.changeTolerance(), profile.constraints(), profile.operations(), profile.security(),
            profile.designPrinciples());
    }
}
