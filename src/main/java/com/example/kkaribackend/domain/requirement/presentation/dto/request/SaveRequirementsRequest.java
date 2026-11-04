package com.example.kkaribackend.domain.requirement.presentation.dto.request;

import com.example.kkaribackend.domain.project.domain.Priority;
import com.example.kkaribackend.domain.project.domain.Requirements;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SaveRequirementsRequest(
    @NotBlank(message = "요구사항은 비어있을 수 없습니다.")
    @Size(max = 5000, message = "요구사항은 5000자 이하여야 합니다.")
    String content,

    @NotEmpty(message = "우선순위를 하나 이상 선택해야 합니다.")
    @Size(max = 4, message = "우선순위는 4개 이하여야 합니다.")
    List<@NotNull(message = "우선순위는 비어있을 수 없습니다.") Priority> priorities,

    @Size(max = 2000, message = "원하는 구조는 2000자 이하여야 합니다.")
    String desiredArchitecture,

    @DecimalMin(value = "0", message = "월 예산은 0 이상이어야 합니다.")
    @Digits(integer = 15, fraction = 2, message = "월 예산은 정수 15자리, 소수 2자리 이하여야 합니다.")
    BigDecimal monthlyBudget,

    @Pattern(regexp = "^[A-Z]{3}$", message = "통화는 ISO 4217 3자리 대문자 코드여야 합니다.")
    String currency,

    @NotNull(message = "개인정보 포함 여부는 비어있을 수 없습니다.")
    Boolean containsPersonalData,

    @Size(max = 50, message = "변경 금지 리소스는 50개 이하여야 합니다.")
    List<@NotBlank(message = "변경 금지 리소스는 비어있을 수 없습니다.")
         @Size(max = 200, message = "변경 금지 리소스는 200자 이하여야 합니다.") String> frozenResources
) {
    @AssertTrue(message = "월 예산을 입력하면 통화도 입력해야 합니다.")
    private boolean isCurrencyGivenWithBudget() {
        return monthlyBudget == null || currency != null;
    }

    public Requirements toRequirements() {
        return Requirements.builder()
            .content(content)
            .priorities(priorities.stream().distinct().toList())
            .desiredArchitecture(desiredArchitecture)
            .monthlyBudget(monthlyBudget)
            .currency(currency)
            .containsPersonalData(containsPersonalData)
            .frozenResources(frozenResources == null ? List.of() : frozenResources)
            .build();
    }
}
