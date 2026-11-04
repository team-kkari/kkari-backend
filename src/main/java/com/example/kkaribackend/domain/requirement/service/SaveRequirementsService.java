package com.example.kkaribackend.domain.requirement.service;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.requirement.presentation.dto.request.SaveRequirementsRequest;
import com.example.kkaribackend.domain.requirement.presentation.dto.response.RequirementsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SaveRequirementsService {
    private final ProjectFacade projectFacade;

    @Transactional
    public RequirementsResponse execute(Long projectId, SaveRequirementsRequest request) {
        Project project = projectFacade.getAssignedProject(projectId);
        project.updateRequirements(request.toRequirements());
        return RequirementsResponse.from(project.getRequirements());
    }
}
