package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStep;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.aws.AwsConnectionProvider;
import com.example.kkaribackend.global.aws.AwsInfraCollector;
import com.example.kkaribackend.global.aws.exception.AwsErrorCode;
import com.example.kkaribackend.global.aws.exception.AwsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunAnalysisServiceTest {
    private AwsConnectionProvider awsConnectionProvider;
    private AwsInfraCollector awsInfraCollector;
    private RunAnalysisService service;
    private AnalysisJob job;

    @BeforeEach
    void setUp() {
        AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
        awsConnectionProvider = mock(AwsConnectionProvider.class);
        awsInfraCollector = mock(AwsInfraCollector.class);
        service = new RunAnalysisService(analysisJobRepository, awsConnectionProvider, awsInfraCollector,
            new ObjectMapper());

        Project project = Project.builder().id(1L).roleArn("arn:aws:iam::123456789012:role/kkari").build();
        job = AnalysisJob.builder().id(10L).project(project).build();
        when(analysisJobRepository.findWithProjectById(10L)).thenReturn(Optional.of(job));
    }

    @Test
    void 예상하지_못한_예외가_나면_FAILED() {
        when(awsInfraCollector.collect(any(), any())).thenAnswer(invocation -> {
            invocation.<Consumer<AnalysisStep>>getArgument(1).accept(AnalysisStep.STRUCTURE);
            throw new IllegalStateException("boom");
        });

        service.execute(new AnalysisRequestedEvent(10L));

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getStep()).isEqualTo(AnalysisStep.STRUCTURE);
        assertThat(job.getFailureReason()).isEqualTo("분석 중 예상하지 못한 오류가 발생했습니다.");
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void AssumeRole이_실패하면_FAILED() {
        when(awsConnectionProvider.assumeRole(anyString(), any(), anyString()))
            .thenThrow(new AwsException(AwsErrorCode.AWS_REQUEST_FAILED));

        service.execute(new AnalysisRequestedEvent(10L));

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("AWS 요청에 실패했습니다.");
        assertThat(job.getResult()).isNull();
    }
}
