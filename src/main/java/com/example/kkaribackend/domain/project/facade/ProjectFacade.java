package com.example.kkaribackend.domain.project.facade;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.exception.NotAssignedProjectException;
import com.example.kkaribackend.domain.project.exception.NotRegisteredProjectException;
import com.example.kkaribackend.domain.project.exception.ProjectNotFoundException;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProjectFacade {

    private final ProjectRepository projectRepository;
    private final UserFacade userFacade;

    // 현재 유저가 담당하는 프로젝트만 반환한다
    public Project getAssignedProject(Long projectId) {
        return checkAssigned(projectRepository.findById(projectId));
    }

    // 트랜잭션이 끝날 때까지 프로젝트 행을 잠가서, 같은 프로젝트의 동시 요청이 차례로 처리되게 한다
    public Project lockAssignedProject(Long projectId) {
        return checkAssigned(projectRepository.findByIdForUpdate(projectId));
    }

    // 현재 고객이 등록한 서비스만 반환한다
    public Project getRegisteredProject(Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        if (!project.isRegisteredBy(userFacade.getCurrentUserId())) {
            throw new NotRegisteredProjectException();
        }
        return project;
    }

    private Project checkAssigned(Optional<Project> found) {
        Project project = found.orElseThrow(ProjectNotFoundException::new);
        if (!project.getUser().getId().equals(userFacade.getCurrentUserId())) {
            throw new NotAssignedProjectException();
        }
        return project;
    }

    // 분석 스레드가 들고 있던 Project 를 save 하면 분석 중에 바뀐 요구사항을 덮어쓴다
    @Transactional
    public void markAnalyzed(Long projectId) {
        projectRepository.findById(projectId)
            .orElseThrow(ProjectNotFoundException::new)
            .markAnalyzed();
    }
}
