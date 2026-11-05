package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryCustomerArchitectureService {
    private final ProjectFacade projectFacade;
    private final QueryArchitectureService queryArchitectureService;

    @Transactional(readOnly = true)
    public ArchitectureResponse execute(Long serviceId) {
        projectFacade.getRegisteredProject(serviceId);
        return queryArchitectureService.latest(serviceId);
    }
}
