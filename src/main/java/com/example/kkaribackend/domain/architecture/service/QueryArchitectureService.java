package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.architecture.exception.ArchitectureResultNotFoundException;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Service
public class QueryArchitectureService {
    private final ProjectFacade projectFacade;
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    // 마지막으로 성공한 생성 결과. 이후 생성이 실패해도 이 결과는 유지된다
    @Transactional(readOnly = true)
    public ArchitectureResponse execute(Long projectId) {
        projectFacade.getAssignedProject(projectId);
        AnalysisJob job = analysisJobRepository
            .findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(projectId, AnalysisJobType.ARCHITECTURE,
                AnalysisStatus.SUCCEEDED)
            .orElseThrow(ArchitectureResultNotFoundException::new);
        return new ArchitectureResponse(job.getId(), job.getFinishedAt(), objectMapper.readTree(job.getResult()));
    }
}
