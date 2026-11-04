package com.example.kkaribackend.domain.tenant.presentation.dto.response;

import com.example.kkaribackend.domain.engagement.domain.Engagement;
import com.example.kkaribackend.domain.engagement.domain.EngagementStage;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record TenantListResponse(
    Long tenantId,
    String name,
    String contactName,
    Long engagementId,
    EngagementStage stage,
    // 비용 수집 전에는 null (0원이 아님)
    BigDecimal monthlyCost,
    LocalDateTime updatedAt
) {
    public static TenantListResponse from(Engagement engagement) {
        return TenantListResponse.builder()
            .tenantId(engagement.getTenant().getId())
            .name(engagement.getTenant().getName())
            .contactName(engagement.getTenant().getContactName())
            .engagementId(engagement.getId())
            .stage(engagement.getStage())
            .monthlyCost(null)
            .updatedAt(engagement.getUpdatedAt())
            .build();
    }
}
