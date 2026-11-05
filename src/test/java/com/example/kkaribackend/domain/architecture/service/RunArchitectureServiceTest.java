package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.ai.AiServerClient;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunArchitectureServiceTest {
    private static final ArchitectureRequest REQUEST = ArchitectureRequest.builder()
        .infra("[AWS]").goal("").priorities(List.of()).count(5).build();

    private AiServerClient aiServerClient;
    private ProjectFacade projectFacade;
    private RunArchitectureService service;
    private AnalysisJob job;

    @BeforeEach
    void setUp() {
        AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
        aiServerClient = mock(AiServerClient.class);
        projectFacade = mock(ProjectFacade.class);
        service = new RunArchitectureService(analysisJobRepository, aiServerClient, projectFacade);
        job = AnalysisJob.builder().id(10L).project(Project.builder().id(3L).build()).type(AnalysisJobType.ARCHITECTURE).build();
        when(analysisJobRepository.findById(10L)).thenReturn(Optional.of(job));
    }

    @Test
    void AI_응답을_그대로_결과로_저장한다() {
        when(aiServerClient.architectures(REQUEST)).thenReturn(new ObjectMapper().readTree("{\"overall\":\"요약\"}"));

        service.execute(new ArchitectureRequestedEvent(10L, REQUEST));

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.SUCCEEDED);
        assertThat(job.getResult()).isEqualTo("{\"overall\":\"요약\"}");
        assertThat(job.getStep()).isNull();
        verify(projectFacade).markRecommended(3L);
    }

    @Test
    void AI_서버가_실패하면_우리_메시지로_FAILED() {
        when(aiServerClient.architectures(REQUEST)).thenThrow(new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE));

        service.execute(new ArchitectureRequestedEvent(10L, REQUEST));

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo(AiErrorCode.AI_SERVER_UNAVAILABLE.getMessage());
        assertThat(job.getResult()).isNull();
        verifyNoInteractions(projectFacade);
    }

    @Test
    void 이미_실패_처리된_작업은_실행하지_않는다() {
        job.fail("시간 초과로 중단되었습니다.");

        service.execute(new ArchitectureRequestedEvent(10L, REQUEST));

        verifyNoInteractions(aiServerClient);
    }
}
