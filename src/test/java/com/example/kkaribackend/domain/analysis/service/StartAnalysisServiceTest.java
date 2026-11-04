package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.exception.AwsNotConnectedException;
import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartAnalysisServiceTest {
    private ProjectFacade projectFacade;
    private AnalysisJobRepository analysisJobRepository;
    private ApplicationEventPublisher eventPublisher;
    private StartAnalysisService service;

    @BeforeEach
    void setUp() {
        projectFacade = mock(ProjectFacade.class);
        analysisJobRepository = mock(AnalysisJobRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new StartAnalysisService(projectFacade, analysisJobRepository, eventPublisher);
    }

    @Test
    void 연결되지_않은_프로젝트는_분석할_수_없다() {
        when(projectFacade.getAssignedProject(1L)).thenReturn(project(AwsConnectionStatus.UNTESTED));

        assertThatThrownBy(() -> service.execute(1L)).isInstanceOf(AwsNotConnectedException.class);
        verify(analysisJobRepository, never()).save(any());
    }

    @Test
    void 진행_중인_분석이_있으면_그_작업을_돌려준다() {
        Project project = project(AwsConnectionStatus.CONNECTED);
        when(projectFacade.getAssignedProject(1L)).thenReturn(project);
        when(analysisJobRepository.findFirstByProjectIdAndStatusInOrderByIdDesc(1L, AnalysisStatus.IN_PROGRESS))
            .thenReturn(Optional.of(AnalysisJob.builder().id(7L).project(project).status(AnalysisStatus.RUNNING).build()));

        assertThat(service.execute(1L).jobId()).isEqualTo(7L);
        verify(analysisJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void 진행_중인_분석이_없으면_새_작업을_만들고_실행을_요청한다() {
        Project project = project(AwsConnectionStatus.CONNECTED);
        when(projectFacade.getAssignedProject(1L)).thenReturn(project);
        when(analysisJobRepository.findFirstByProjectIdAndStatusInOrderByIdDesc(1L, AnalysisStatus.IN_PROGRESS))
            .thenReturn(Optional.empty());
        when(analysisJobRepository.save(any(AnalysisJob.class)))
            .thenReturn(AnalysisJob.builder().id(8L).project(project).build());

        assertThat(service.execute(1L).jobId()).isEqualTo(8L);
        verify(eventPublisher).publishEvent(new AnalysisRequestedEvent(8L));
    }

    private static Project project(AwsConnectionStatus status) {
        return Project.builder().id(1L).awsConnectionStatus(status).build();
    }
}
