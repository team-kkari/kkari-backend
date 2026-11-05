package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.exception.AnalysisJobNotFoundException;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.AnalysisJobResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryAnalysisJobService {
    private final AnalysisJobRepository analysisJobRepository;
    private final ProjectFacade projectFacade;

    @Transactional(readOnly = true)
    public AnalysisJobResponse execute(Long jobId) {
        AnalysisJob job = analysisJobRepository.findById(jobId)
            .orElseThrow(AnalysisJobNotFoundException::new);
        projectFacade.getAssignedProject(job.getProject().getId());
        return AnalysisJobResponse.of(job);
    }
}
