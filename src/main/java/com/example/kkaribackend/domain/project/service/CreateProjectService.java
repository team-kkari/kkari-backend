package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.presentation.dto.request.CreateProjectRequest;
import com.example.kkaribackend.domain.project.presentation.dto.response.CreateProjectResponse;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CreateProjectService {
    private final ProjectRepository projectRepository;
    private final UserFacade userFacade;

    @Transactional
    public CreateProjectResponse execute(CreateProjectRequest request) {
        Project project = projectRepository.save(
            Project.builder()
                .user(userFacade.getCurrentUser())
                .companyName(request.companyName())
                .contactName(request.contactName())
                .memo(request.memo())
                .build()
        );
        return new CreateProjectResponse(project.getId());
    }
}
