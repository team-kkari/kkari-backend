package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.domain.ProjectStage;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.presentation.dto.response.ProjectListResponse;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class QueryProjectListService {
    private final ProjectRepository projectRepository;
    private final UserFacade userFacade;

    @Transactional(readOnly = true)
    public List<ProjectListResponse> execute(ProjectStage stage) {
        return projectRepository.findAllByUserId(userFacade.getCurrentUserId(), stage).stream()
            .map(ProjectListResponse::from)
            .toList();
    }
}
