package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.architecture.exception.AnalysisNotCompletedException;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(projectFacade.getAssignedProject(1L)).thenReturn(project);
    }

    @Test
    void 성공한_분석이_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> service.execute(1L, null)).isInstanceOf(AnalysisNotCompletedException.class);
        verify(analysisJobRepository, never()).save(any());
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

    @Test
    void 개수를_비우면_5개로_생성_작업을_만든다() {
        givenSucceededAnalysis();
        when(analysisJobRepository.findFirstByProjectIdAndTypeAndStatusInOrderByIdDesc(1L,
            AnalysisJobType.ARCHITECTURE, AnalysisStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        ArgumentCaptor<AnalysisJob> saved = ArgumentCaptor.forClass(AnalysisJob.class);
        when(analysisJobRepository.save(saved.capture()))
            .thenReturn(AnalysisJob.builder().id(8L).project(project).type(AnalysisJobType.ARCHITECTURE).build());

        assertThat(service.execute(1L, null).jobId()).isEqualTo(8L);
        assertThat(saved.getValue().getType()).isEqualTo(AnalysisJobType.ARCHITECTURE);
        ArgumentCaptor<ArchitectureRequestedEvent> event = ArgumentCaptor.forClass(ArchitectureRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().jobId()).isEqualTo(8L);
        assertThat(event.getValue().request().count()).isEqualTo(5);
        assertThat(event.getValue().request().infra()).isEqualTo("[AWS] 리전 ap-northeast-2\nEC2 i-1: t3.micro");
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
