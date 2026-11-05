package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartArchitectureServiceTest {
    private AnalysisJobRepository analysisJobRepository;
    private ApplicationEventPublisher eventPublisher;
    private StartArchitectureService service;
    private Project project;

    @BeforeEach
    void setUp() {
        ProjectFacade projectFacade = mock(ProjectFacade.class);
        analysisJobRepository = mock(AnalysisJobRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new StartArchitectureService(projectFacade, analysisJobRepository, eventPublisher,
            new ObjectMapper());
        project = Project.builder().id(1L).build();
        when(projectFacade.lockAssignedProject(1L)).thenReturn(project);
    }

    @Test
    void 진행_중인_생성_작업이_있으면_그_작업을_돌려준다() {
        givenSucceededAnalysis();
        when(analysisJobRepository.findFirstByProjectIdAndTypeAndStatusInOrderByIdDesc(1L,
            AnalysisJobType.ARCHITECTURE, AnalysisStatus.IN_PROGRESS))
            .thenReturn(Optional.of(AnalysisJob.builder().id(7L).project(project).type(AnalysisJobType.ARCHITECTURE)
                .status(AnalysisStatus.RUNNING).createdAt(LocalDateTime.now().minusMinutes(2)).build()));

        assertThat(service.execute(1L, null).jobId()).isEqualTo(7L);
        verify(analysisJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private void givenSucceededAnalysis() {
        when(analysisJobRepository.findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(1L,
            AnalysisJobType.ANALYSIS, AnalysisStatus.SUCCEEDED))
            .thenReturn(Optional.of(AnalysisJob.builder().id(3L).project(project).status(AnalysisStatus.SUCCEEDED)
                .result("""
                    {"region":"ap-northeast-2","resources":{"ec2":[{"instanceId":"i-1","instanceType":"t3.micro"}]}}
                    """).build()));
    }
}
