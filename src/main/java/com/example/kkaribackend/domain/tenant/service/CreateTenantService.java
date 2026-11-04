package com.example.kkaribackend.domain.tenant.service;

import com.example.kkaribackend.domain.engagement.domain.Engagement;
import com.example.kkaribackend.domain.engagement.domain.repository.EngagementRepository;
import com.example.kkaribackend.domain.tenant.domain.Tenant;
import com.example.kkaribackend.domain.tenant.domain.repository.TenantRepository;
import com.example.kkaribackend.domain.tenant.presentation.dto.request.CreateTenantRequest;
import com.example.kkaribackend.domain.tenant.presentation.dto.response.CreateTenantResponse;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CreateTenantService {
    private final TenantRepository tenantRepository;
    private final EngagementRepository engagementRepository;
    private final UserFacade userFacade;

    @Transactional
    public CreateTenantResponse execute(CreateTenantRequest request) {
        Tenant tenant = tenantRepository.save(
            Tenant.builder()
                .user(userFacade.getCurrentUser())
                .name(request.name())
                .contactName(request.contactName())
                .build()
        );

        Engagement engagement = engagementRepository.save(
            Engagement.builder()
                .tenant(tenant)
                .memo(request.memo())
                .build()
        );

        return new CreateTenantResponse(tenant.getId(), engagement.getId());
    }
}
