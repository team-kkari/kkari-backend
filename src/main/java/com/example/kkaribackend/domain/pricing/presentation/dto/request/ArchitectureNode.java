package com.example.kkaribackend.domain.pricing.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * AI 서버 설계안의 구성요소 하나. AI 서버의 Node 를 그대로 받고 id·label·pricing 만 읽는다.
 * 안 쓰는 값은 0, 빈 문자열, false 다.
 */
public record ArchitectureNode(
    @NotBlank @Size(max = 100) String id,
    @NotNull @Size(max = 200) String label,
    @NotNull @Valid Pricing pricing
) {
    // spec·engine 은 usageType 에 그대로 들어가므로 쓸 수 있는 글자를 제한한다
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Pricing(
        @NotBlank @Size(max = 40) String kind,
        @NotNull @Pattern(regexp = "[A-Za-z0-9. -]{0,40}") String spec,
        @NotNull @Pattern(regexp = "[A-Za-z0-9. -]{0,40}") String engine,
        boolean multiAz,
        @PositiveOrZero @Max(1_000) double count,
        @PositiveOrZero @Max(744) double hoursPerMonth,
        @PositiveOrZero @Max(10_000_000) double storageGb,
        @PositiveOrZero @Max(10_000_000) double dataGbMonth,
        @PositiveOrZero @Max(10_000_000) double requestsMillionMonth,
        @PositiveOrZero @Max(10_000_000) double writeRequestsMillionMonth,
        @PositiveOrZero @Max(1_000) double vcpu,
        @PositiveOrZero @Max(10_000) double memoryGb,
        @PositiveOrZero @Max(900_000) double durationMs,
        // waf 는 규칙 수를 이 값으로 보낸다
        @PositiveOrZero @Max(10_000) double lcu
    ) {
    }
}
