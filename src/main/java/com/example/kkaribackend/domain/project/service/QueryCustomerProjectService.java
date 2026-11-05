package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.project.presentation.dto.response.ServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryCustomerProjectService {
    private final ProjectFacade projectFacade;

    @Transactional(readOnly = true)
    public ServiceResponse execute(Long serviceId) {
        return ServiceResponse.from(projectFacade.getRegisteredProject(serviceId));
    }
}
