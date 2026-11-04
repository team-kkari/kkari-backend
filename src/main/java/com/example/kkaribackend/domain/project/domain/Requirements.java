package com.example.kkaribackend.domain.project.domain;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record Requirements(
    String content,
    // 앞에 있을수록 중요하다
    List<Priority> priorities,
    String desiredArchitecture,
    BigDecimal monthlyBudget,
    String currency,
    boolean containsPersonalData,
    List<String> frozenResources
) {
}
