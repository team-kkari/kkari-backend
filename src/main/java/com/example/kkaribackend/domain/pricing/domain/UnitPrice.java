package com.example.kkaribackend.domain.pricing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * AWS Price List 에서 받아 둔 사용량 한 종류의 온디맨드 단가.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Table(name = "tbl_unit_price", uniqueConstraints = @UniqueConstraint(name = "uk_unit_price_usage",
    columnNames = {"service_code", "usage_type", "operation"}))
public class UnitPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String serviceCode;

    @Column(length = 100, nullable = false)
    private String usageType;

    // 유니크 제약에서 NULL 은 서로 다른 값이라, operation 이 없는 요금은 빈 문자열로 둔다
    @Column(length = 50, nullable = false)
    private String operation;

    @Column(length = 30)
    private String unit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Tier> tiers;

    @Column(nullable = false)
    private Instant fetchedAt;

    // endRange 가 null 이면 상한이 없다
    public record Tier(BigDecimal beginRange, BigDecimal endRange, BigDecimal usdPerUnit) {
    }

    // AWS 청구처럼 사용량을 구간마다 나눠 그 구간 단가를 곱해 더한다
    public BigDecimal cost(double amount) {
        BigDecimal quantity = BigDecimal.valueOf(amount);
        BigDecimal total = BigDecimal.ZERO;
        for (Tier tier : tiers) {
            BigDecimal upper = tier.endRange() == null ? quantity : quantity.min(tier.endRange());
            BigDecimal inTier = upper.subtract(tier.beginRange());
            if (inTier.signum() > 0) {
                total = total.add(inTier.multiply(tier.usdPerUnit()));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public void refresh(UnitPrice fetched) {
        this.unit = fetched.unit;
        this.tiers = fetched.tiers;
        this.fetchedAt = fetched.fetchedAt;
    }
}
