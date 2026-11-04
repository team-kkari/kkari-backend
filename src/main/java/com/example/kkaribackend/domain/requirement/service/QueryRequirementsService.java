package com.example.kkaribackend.domain.requirement.service;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.requirement.exception.RequirementsNotFoundException;
import com.example.kkaribackend.domain.requirement.presentation.dto.response.RequirementsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryRequirementsService {
    private final ProjectFacade projectFacade;

    @Transactional(readOnly = true)
    public RequirementsResponse execute(Long projectId) {
        Project project = projectFacade.getAssignedProject(projectId);
        if (project.getRequirements() == null) {
            throw new RequirementsNotFoundException();
        }
        return RequirementsResponse.from(project.getRequirements());
    }
}
