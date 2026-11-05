package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.exception.AnalysisResultNotFoundException;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.DiscoveryResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Service
public class QueryDiscoveryService {
    private final ProjectFacade projectFacade;
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    // 마지막으로 성공한 분석 결과. 이후 분석이 실패해도 이 결과는 유지된다
    @Transactional(readOnly = true)
    public DiscoveryResponse execute(Long projectId) {
        projectFacade.getAssignedProject(projectId);
        AnalysisJob job = analysisJobRepository
            .findFirstByProjectIdAndStatusOrderByIdDesc(projectId, AnalysisStatus.SUCCEEDED)
            .orElseThrow(AnalysisResultNotFoundException::new);
        return new DiscoveryResponse(job.getId(), job.getFinishedAt(),
            objectMapper.readValue(job.getResult(), InfraSnapshot.class));
    }
}
