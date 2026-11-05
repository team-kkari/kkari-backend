package com.example.kkaribackend.domain.chat.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.Priority;
import com.example.kkaribackend.global.ai.dto.IntakeProfile;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.kkaribackend.global.util.TextTruncator.truncate;

/**
 * 상담 프로필을 요구사항 저장(PUT /requirements) 형식으로 바꾼 초안. 저장은 [솔루션 생성] 때 엔지니어가 확인하고 한다.
 * 확인되지 않은 값은 채우지 않는다: containsPersonalData·frozenResources 가 null 이면 아직 안 물어본 것이다.
 */
@Builder
public record RequirementsDraftResponseDto(
    String content,
    List<Priority> priorities,
    String desiredArchitecture,
    BigDecimal monthlyBudget,
    String currency,
    Boolean containsPersonalData,
    List<String> frozenResources
) {
    private static final int MAX_CONTENT_LENGTH = 5000;
    private static final int BUDGET_SCALE = 2;

    public static RequirementsDraftResponseDto from(IntakeProfile profile) {
        if (profile == null) {
            return null;
        }
        return RequirementsDraftResponseDto.builder()
            .content(describe(profile))
            .priorities(toPriorities(profile.priorities()))
            .monthlyBudget(toBudget(profile.monthlyBudget()))
            .currency(profile.currency())
            .containsPersonalData(profile.containsPersonalData())
            .frozenResources(toFrozenResources(profile.frozenResources()))
            .build();
    }

    // 예: "목표: 비용 절감\n워크로드: 쇼핑몰, 하루 1만 명"
    private static String describe(IntakeProfile profile) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("목표", profile.goal());
        fields.put("설계 원칙·고려사항", profile.designPrinciples());
        fields.put("워크로드", profile.workload());
        fields.put("현재 구성", profile.architecture());
        fields.put("트래픽", profile.traffic());
        fields.put("가용성 목표", profile.availability());
        fields.put("성능 요구", profile.performance());
        fields.put("현재 비용", profile.cost());
        fields.put("변경 허용 범위", profile.changeTolerance());
        fields.put("제약", profile.constraints());
        fields.put("운영 체계", profile.operations());
        fields.put("보안 요구", profile.security());
        String text = fields.entrySet().stream()
            .filter(field -> field.getValue() != null && !field.getValue().isBlank())
            .map(field -> field.getKey() + ": " + field.getValue().strip())
            .collect(Collectors.joining("\n"));
        return text.isEmpty() ? null : truncate(text, MAX_CONTENT_LENGTH);
    }

    // 명세의 4개(비용·성능·안정성·보안)만 남긴다. AI 의 operations·serverless 같은 값은 뺀다. 순서는 유지한다
    private static List<Priority> toPriorities(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull)
            .map(value -> parse(value.strip().toUpperCase(Locale.ROOT)))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    // 요구사항 저장은 소수 2자리까지 받는다
    private static BigDecimal toBudget(BigDecimal budget) {
        return budget == null || budget.scale() <= BUDGET_SCALE ? budget : budget.setScale(BUDGET_SCALE, RoundingMode.HALF_UP);
    }

    // null(안 물어봄)과 [](없음)은 구분해서 그대로 둔다
    private static List<String> toFrozenResources(List<String> resources) {
        if (resources == null) {
            return null;
        }
        return resources.stream().filter(resource -> resource != null && !resource.isBlank()).map(String::strip).toList();
    }

    private static Priority parse(String name) {
        for (Priority priority : Priority.values()) {
            if (priority.name().equals(name)) {
                return priority;
            }
        }
        return null;
    }
}
