package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.project.presentation.dto.response.ProjectDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryProjectService {
    private final ProjectFacade projectFacade;

    @Transactional(readOnly = true)
    public ProjectDetailResponse execute(Long projectId) {
        return ProjectDetailResponse.from(projectFacade.getAssignedProject(projectId));
    }
}
