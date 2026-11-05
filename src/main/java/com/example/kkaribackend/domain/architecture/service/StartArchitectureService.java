package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.architecture.exception.AnalysisNotCompletedException;
import com.example.kkaribackend.domain.architecture.presentation.dto.request.StartArchitectureRequest;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.StartArchitectureResponse;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class StartArchitectureService {
    private final ProjectFacade projectFacade;
    private final AnalysisJobRepository analysisJobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_ARCHITECTURE_COUNT = 5;
    // 생성은 길어야 몇 분이라, 이보다 오래 진행 중이면 서버 재시작 등으로 멈춘 작업으로 본다
    private static final Duration STALE_AFTER = Duration.ofHours(1);
    private static final String STALE_FAILURE_REASON = "시간 초과로 중단되었습니다.";

    // 이미 대기·실행 중인 생성 작업이 있으면 새로 만들지 않고 그 작업을 돌려준다(요청한 개수가 달라도)
    @Transactional
    public StartArchitectureResponse execute(Long projectId, StartArchitectureRequest request) {
        Project project = projectFacade.lockAssignedProject(projectId);
        AnalysisJob latestAnalysis = analysisJobRepository
            .findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(projectId, AnalysisJobType.ANALYSIS,
                AnalysisStatus.SUCCEEDED)
            .orElseThrow(AnalysisNotCompletedException::new);

        Optional<AnalysisJob> inProgress = analysisJobRepository
            .findFirstByProjectIdAndTypeAndStatusInOrderByIdDesc(projectId, AnalysisJobType.ARCHITECTURE,
                AnalysisStatus.IN_PROGRESS);
        if (inProgress.isPresent() && !isStale(inProgress.get())) {
            return new StartArchitectureResponse(inProgress.get().getId());
        }
        inProgress.ifPresent(staleJob -> staleJob.fail(STALE_FAILURE_REASON));

        InfraSnapshot snapshot = objectMapper.readValue(latestAnalysis.getResult(), InfraSnapshot.class);
        ArchitectureRequest input = ArchitectureInputFactory.create(snapshot, project.getTerraform(),
            project.getRequirements(), requestedCount(request));

        AnalysisJob created = analysisJobRepository.save(AnalysisJob.builder()
            .project(project).type(AnalysisJobType.ARCHITECTURE).build());
        eventPublisher.publishEvent(new ArchitectureRequestedEvent(created.getId(), input));
        return new StartArchitectureResponse(created.getId());
    }

    private boolean isStale(AnalysisJob job) {
        return job.getCreatedAt().isBefore(LocalDateTime.now().minus(STALE_AFTER));
    }

    private int requestedCount(StartArchitectureRequest request) {
        if (request == null || request.count() == null) {
            return DEFAULT_ARCHITECTURE_COUNT;
        }
        return request.count();
    }
}
