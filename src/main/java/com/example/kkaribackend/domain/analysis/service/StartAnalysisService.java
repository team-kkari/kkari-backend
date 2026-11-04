package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.exception.AwsNotConnectedException;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.StartAnalysisResponse;
import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StartAnalysisService {
    private final ProjectFacade projectFacade;
    private final AnalysisJobRepository analysisJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 이미 대기·실행 중인 분석이 있으면 새로 만들지 않고 그 작업을 돌려준다
    @Transactional
    public StartAnalysisResponse execute(Long projectId) {
        Project project = projectFacade.getAssignedProject(projectId);
        if (project.getAwsConnectionStatus() != AwsConnectionStatus.CONNECTED) {
            throw new AwsNotConnectedException();
        }

        AnalysisJob job = analysisJobRepository
            .findFirstByProjectIdAndStatusInOrderByIdDesc(projectId, AnalysisStatus.IN_PROGRESS)
            .orElseGet(() -> {
                AnalysisJob created = analysisJobRepository.save(AnalysisJob.builder().project(project).build());
                eventPublisher.publishEvent(new AnalysisRequestedEvent(created.getId()));
                return created;
            });
        return new StartAnalysisResponse(job.getId());
    }
}
