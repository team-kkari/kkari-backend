package com.example.kkaribackend.domain.project.service;

import com.example.kkaribackend.domain.project.domain.ProjectStage;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.project.presentation.dto.response.ProjectListResponse;
import com.example.kkaribackend.domain.user.facade.UserFacade;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.global.aws.InfraCostSummarizer;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class QueryProjectListService {
    private final ProjectRepository projectRepository;
    private final UserFacade userFacade;
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ProjectListResponse> execute(ProjectStage stage) {
        var projects = projectRepository.findAllByUserId(userFacade.getCurrentUserId(), stage);
        if (projects.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> costs = new HashMap<>();
        analysisJobRepository.findLatestByProjectIdsAndTypeAndStatus(
            projects.stream().map(project -> project.getId()).toList(), AnalysisJobType.ANALYSIS, AnalysisStatus.SUCCEEDED)
            .forEach(job -> costs.put(job.getProject().getId(),
                InfraCostSummarizer.monthlyUsd(objectMapper.readValue(job.getResult(), InfraSnapshot.class))));
        return projects.stream()
            .map(project -> ProjectListResponse.from(project, costs.get(project.getId())))
            .toList();
    }
}
