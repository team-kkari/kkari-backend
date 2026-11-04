package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.aws.AwsConnectionProvider;
import com.example.kkaribackend.global.aws.AwsInfraCollector;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.exception.AwsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import tools.jackson.databind.ObjectMapper;

/**
 * 분석 작업을 별도 스레드에서 실행한다.
 * 트랜잭션 없이 상태가 바뀔 때마다 save 로 바로 커밋해서 폴링하는 쪽이 진행 상황을 볼 수 있게 한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RunAnalysisService {
    private final AnalysisJobRepository analysisJobRepository;
    private final AwsConnectionProvider awsConnectionProvider;
    private final AwsInfraCollector awsInfraCollector;
    private final ObjectMapper objectMapper;

    @Async("analysisExecutor")
    @TransactionalEventListener
    public void execute(AnalysisRequestedEvent event) {
        AnalysisJob job = analysisJobRepository.findWithProjectById(event.jobId()).orElseThrow();
        Project project = job.getProject();
        job.start();
        analysisJobRepository.save(job);

        try {
            AwsCredentialsProvider credentials = awsConnectionProvider.assumeRole(
                project.getRoleArn(), project.getExternalId(), "kkari-analysis-" + job.getId());
            InfraSnapshot snapshot = awsInfraCollector.collect(credentials, step -> {
                job.moveTo(step);
                analysisJobRepository.save(job);
            });
            job.succeed(objectMapper.writeValueAsString(snapshot));
        } catch (AwsException e) {
            job.fail("고객사 Role AssumeRole 실패: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("인프라 분석 실패: job={}", job.getId(), e);
            job.fail("분석 중 예상하지 못한 오류가 발생했습니다.");
        }
        analysisJobRepository.save(job);
    }
}
