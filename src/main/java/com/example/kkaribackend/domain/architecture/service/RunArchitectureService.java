package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;

/**
 * 추천 아키텍처 생성을 별도 스레드에서 실행한다. 인프라 분석과 같은 실행기를 쓴다.
 * 트랜잭션 없이 상태가 바뀔 때마다 save 로 바로 커밋한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RunArchitectureService {
    private final AnalysisJobRepository analysisJobRepository;
    private final AiServerClient aiServerClient;
    private final ProjectFacade projectFacade;

    private static final String UNEXPECTED_FAILURE_REASON = "추천 아키텍처 생성 중 예상하지 못한 오류가 발생했습니다.";

    @Async
    @TransactionalEventListener
    public void execute(ArchitectureRequestedEvent event) {
        AnalysisJob job = analysisJobRepository.findById(event.jobId()).orElseThrow();
        // 실행 전에 시간 초과로 실패 처리된 작업은 다시 돌리지 않는다
        if (job.getStatus() != AnalysisStatus.QUEUED) {
            return;
        }
        job.start();
        analysisJobRepository.save(job);

        try {
            JsonNode architectures = aiServerClient.architectures(event.request());
            job.succeed(architectures.toString());
            projectFacade.markRecommended(job.getProject().getId());
        } catch (AiServerException e) {
            job.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("추천 아키텍처 생성 실패: job={}", job.getId(), e);
            job.fail(UNEXPECTED_FAILURE_REASON);
        }
        analysisJobRepository.save(job);
    }
}
