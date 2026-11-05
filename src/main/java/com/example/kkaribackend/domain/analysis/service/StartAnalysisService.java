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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class StartAnalysisService {
    private final ProjectFacade projectFacade;
    private final AnalysisJobRepository analysisJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 고객사 Role 임시 자격증명 유효 시간. 이보다 오래 진행 중이면 서버 재시작 등으로 멈춘 작업으로 본다
    // 생성 시각 기준이라 오래 대기 후 실행 중인 작업이 실패 처리될 수 있지만, 결과 조회는 가장 최근 성공 작업을 쓰므로 허용한다
    private static final Duration STALE_AFTER = Duration.ofHours(1);

    // 이미 대기·실행 중인 분석이 있으면 새로 만들지 않고 그 작업을 돌려준다
    @Transactional
    public StartAnalysisResponse execute(Long projectId) {
        Project project = projectFacade.getAssignedProject(projectId);
        if (project.getAwsConnectionStatus() != AwsConnectionStatus.CONNECTED) {
            throw new AwsNotConnectedException();
        }

        Optional<AnalysisJob> inProgress = analysisJobRepository
            .findFirstByProjectIdAndStatusInOrderByIdDesc(projectId, AnalysisStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            AnalysisJob job = inProgress.get();
            if (job.getCreatedAt().isAfter(LocalDateTime.now().minus(STALE_AFTER))) {
                return new StartAnalysisResponse(job.getId());
            }
            job.fail("시간 초과로 중단되었습니다.");
        }

        AnalysisJob created = analysisJobRepository.save(AnalysisJob.builder().project(project).build());
        eventPublisher.publishEvent(new AnalysisRequestedEvent(created.getId()));
        return new StartAnalysisResponse(created.getId());
    }
}
