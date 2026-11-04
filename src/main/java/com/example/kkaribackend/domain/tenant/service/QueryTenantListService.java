package com.example.kkaribackend.domain.tenant.service;

import com.example.kkaribackend.domain.engagement.domain.EngagementStage;
import com.example.kkaribackend.domain.engagement.domain.repository.EngagementRepository;
import com.example.kkaribackend.domain.tenant.presentation.dto.response.TenantListResponse;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class QueryTenantListService {
    private final EngagementRepository engagementRepository;
    private final UserFacade userFacade;

    @Transactional(readOnly = true)
    public List<TenantListResponse> execute(EngagementStage stage) {
        return engagementRepository.findLatestByUserId(userFacade.getCurrentUserId(), stage).stream()
            .map(TenantListResponse::from)
            .toList();
    }
}
