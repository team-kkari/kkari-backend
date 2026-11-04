package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.exception.AnalysisJobNotFoundException;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.AnalysisJobResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class QueryAnalysisJobService {
    private final AnalysisJobRepository analysisJobRepository;
    private final ProjectFacade projectFacade;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AnalysisJobResponse execute(Long jobId) {
        AnalysisJob job = analysisJobRepository.findById(jobId)
            .orElseThrow(AnalysisJobNotFoundException::new);
        projectFacade.getAssignedProject(job.getProject().getId());

        Map<String, Integer> resourceCounts = job.getResult() == null ? null
            : objectMapper.readValue(job.getResult(), InfraSnapshot.class).resources().countByType();
        return AnalysisJobResponse.of(job, resourceCounts);
    }
}
