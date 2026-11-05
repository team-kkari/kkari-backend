package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfraCostSummarizerTest {
    @Test
    void 종류별_리소스의_USD_비용을_정밀도_손실_없이_합한다() {
        assertThat(InfraCostSummarizer.monthlyUsd(snapshot(Map.of(
            "EC2", Map.of("i-1", new BigDecimal("7.592")), "EBS", Map.of("vol-1", new BigDecimal("8"))))))
            .isEqualByComparingTo("15.592");
    }

    @Test
    void 미수집과_부분_비용은_0이나_부분_합으로_표시하지_않는다() {
        assertThat(InfraCostSummarizer.monthlyUsd(null)).isNull();
        assertThat(InfraCostSummarizer.monthlyUsd(InfraSnapshot.builder().build())).isNull();
        assertThat(InfraCostSummarizer.monthlyUsd(snapshot(Map.of()))).isNull();
        Map<String, BigDecimal> partial = new HashMap<>();
        partial.put("i-1", BigDecimal.TEN);
        partial.put("i-2", null);
        assertThat(InfraCostSummarizer.monthlyUsd(snapshot(Map.of("EC2", partial)))).isNull();
        assertThat(InfraCostSummarizer.monthlyUsd(InfraSnapshot.builder().cost(snapshot(Map.of("EC2", Map.of("i-1", BigDecimal.TEN))).cost())
            .missing(List.of(new Missing("STRUCTURE RDS", "조회 실패"))).build())).isNull();
        assertThat(InfraCostSummarizer.monthlyUsd(InfraSnapshot.builder().cost(snapshot(Map.of("EC2", Map.of("i-1", BigDecimal.TEN))).cost())
            .missing(List.of(new Missing("COST", "가격 조회 실패"))).build())).isNull();
        assertThat(InfraCostSummarizer.monthlyUsd(InfraSnapshot.builder().cost(snapshot(Map.of("EC2", Map.of("i-1", BigDecimal.TEN))).cost())
            .resources(Resources.builder().ec2(List.of(new Ec2Instance("i-2", null, null, null, null))).build()).build()))
            .isNull();
    }

    @Test
    void 알려진_비용이_0이면_0을_유지하고_사용률_누락만으로_비용을_지우지_않는다() {
        assertThat(InfraCostSummarizer.monthlyUsd(snapshot(Map.of("EC2", Map.of("i-1", BigDecimal.ZERO)))))
            .isEqualByComparingTo("0");
        assertThat(InfraCostSummarizer.monthlyUsd(snapshot(Map.of("EC2", Map.of())))).isEqualByComparingTo("0");
        assertThat(InfraCostSummarizer.monthlyUsd(InfraSnapshot.builder().cost(snapshot(Map.of("EC2", Map.of("i-1", BigDecimal.TEN))).cost())
            .missing(List.of(new Missing("USAGE EC2", "CPU 조회 실패"))).build())).isEqualByComparingTo("10");
    }

    private static InfraSnapshot snapshot(Map<String, Map<String, BigDecimal>> costs) {
        return InfraSnapshot.builder().cost(new Cost("USD", "ON_DEMAND", 730, Map.of(), costs)).build();
    }
}
