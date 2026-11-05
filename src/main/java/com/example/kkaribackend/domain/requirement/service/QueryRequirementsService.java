package com.example.kkaribackend.domain.requirement.service;

import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.requirement.exception.RequirementsNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryRequirementsService {
    private final ProjectFacade projectFacade;

    @Transactional(readOnly = true)
    public Requirements execute(Long projectId) {
        Requirements requirements = projectFacade.getAssignedProject(projectId).getRequirements();
        if (requirements == null) {
            throw new RequirementsNotFoundException();
        }
        return requirements;
    }
}
