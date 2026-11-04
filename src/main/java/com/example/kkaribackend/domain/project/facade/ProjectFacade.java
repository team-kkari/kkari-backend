package com.example.kkaribackend.domain.project.facade;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.exception.NotAssignedProjectException;
import com.example.kkaribackend.domain.project.exception.ProjectNotFoundException;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectFacade {

    private final ProjectRepository projectRepository;
    private final UserFacade userFacade;

    // 현재 유저가 담당하는 프로젝트만 반환한다
    public Project getAssignedProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(ProjectNotFoundException::new);

        if (!project.getUser().getId().equals(userFacade.getCurrentUserId())) {
            throw new NotAssignedProjectException();
        }
        return project;
    }
}
