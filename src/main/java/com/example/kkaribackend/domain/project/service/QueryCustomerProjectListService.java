package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.presentation.dto.response.ServiceListResponse;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class QueryCustomerProjectListService {
    private final ProjectRepository projectRepository;
    private final UserFacade userFacade;

    @Transactional(readOnly = true)
    public List<ServiceListResponse> execute() {
        return projectRepository.findAllByCustomerIdWithEngineer(userFacade.getCurrentUserId()).stream()
            .map(ServiceListResponse::from)
            .toList();
    }
}
