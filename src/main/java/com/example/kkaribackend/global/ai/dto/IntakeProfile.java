package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntakeProfile(
    @JsonProperty("company_name") String companyName,
    String workload,
    String architecture,
    String traffic,
    String availability,
    String performance,
    String cost,
    @JsonProperty("monthly_budget") BigDecimal monthlyBudget,
    String currency,
    @JsonProperty("contains_personal_data") Boolean containsPersonalData,
    @JsonProperty("frozen_resources") List<String> frozenResources,
    String goal,
    List<String> priorities,
    @JsonProperty("change_tolerance") String changeTolerance,
    String constraints,
    String operations,
    String security,
    @JsonProperty("design_principles") String designPrinciples
) {
}
