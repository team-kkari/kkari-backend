package com.example.kkaribackend.domain.chat;

import com.example.kkaribackend.domain.chat.presentation.dto.response.RequirementsDraftResponseDto;
import com.example.kkaribackend.domain.project.domain.Priority;
import com.example.kkaribackend.global.ai.dto.IntakeProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementsDraftResponseDtoTest {

    @Test
    void 프로필이_없으면_초안도_없다() {
        assertThat(RequirementsDraftResponseDto.from(null)).isNull();
    }

    @Test
    void 설계_원칙과_고려사항은_요구사항_내용에_담긴다() {
        RequirementsDraftResponseDto draft = RequirementsDraftResponseDto.from(IntakeProfile.builder()
            .goal("비용 절감").designPrinciples("Multi-AZ 필수, 관리형 서비스 우선").build());

        assertThat(draft.content()).isEqualTo("목표: 비용 절감\n설계 원칙·고려사항: Multi-AZ 필수, 관리형 서비스 우선");
    }

    @Test
    void 우선순위는_명세의_4개만_순서대로_남긴다() {
        IntakeProfile profile = IntakeProfile.builder()
            .priorities(List.of("serverless", "security", "operations", "cost", "COST", "sustainability")).build();

        assertThat(RequirementsDraftResponseDto.from(profile).priorities())
            .containsExactly(Priority.SECURITY, Priority.COST);
    }

    @Test
    void 정형_값은_그대로_두고_확인되지_않은_값은_null로_둔다() {
        RequirementsDraftResponseDto confirmed = RequirementsDraftResponseDto.from(IntakeProfile.builder()
            .monthlyBudget(new BigDecimal("1500")).currency("USD").containsPersonalData(true)
            .frozenResources(List.of("prod-db")).build());
        assertThat(confirmed.monthlyBudget()).isEqualByComparingTo("1500");
        assertThat(confirmed.currency()).isEqualTo("USD");
        assertThat(confirmed.containsPersonalData()).isTrue();
        assertThat(confirmed.frozenResources()).containsExactly("prod-db");

        // 아직 안 물어본 값을 false·[] 로 채우면 "개인정보 없음", "변경 금지 없음"으로 확정된 것처럼 보인다
        RequirementsDraftResponseDto unknown = RequirementsDraftResponseDto.from(IntakeProfile.builder().build());
        assertThat(unknown.containsPersonalData()).isNull();
        assertThat(unknown.frozenResources()).isNull();
        assertThat(unknown.content()).isNull();
        assertThat(unknown.priorities()).isEmpty();
    }

    @Test
    void 요구사항_저장에서_거절될_값은_저장_형식에_맞춘다() {
        RequirementsDraftResponseDto draft = RequirementsDraftResponseDto.from(IntakeProfile.builder()
            .monthlyBudget(new BigDecimal("1234.5678")).frozenResources(List.of(" prod-db ", "", " ")).build());

        assertThat(draft.monthlyBudget()).isEqualByComparingTo("1234.57");
        assertThat(draft.frozenResources()).containsExactly("prod-db");
    }

    @Test
    void 서술_항목은_이름을_붙여_한_줄씩_쓰고_비어_있으면_뺀다() {
        RequirementsDraftResponseDto draft = RequirementsDraftResponseDto.from(IntakeProfile.builder()
            .companyName("까리").goal("비용 절감").workload(" 쇼핑몰 ").traffic("").availability("99.9%")
            .changeTolerance("새벽 30분 허용").build());

        assertThat(draft.content()).isEqualTo("목표: 비용 절감\n워크로드: 쇼핑몰\n가용성 목표: 99.9%\n변경 허용 범위: 새벽 30분 허용");
    }

    @Test
    void 서술이_길면_요구사항_저장_한도_안으로_자른다() {
        RequirementsDraftResponseDto draft = RequirementsDraftResponseDto.from(IntakeProfile.builder()
            .workload("가".repeat(6000)).build());

        assertThat(draft.content()).hasSize(5000).endsWith("…(생략)");
    }
}
