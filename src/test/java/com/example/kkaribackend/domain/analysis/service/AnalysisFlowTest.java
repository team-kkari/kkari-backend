package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.AnalysisJobResponse;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.DiscoveryResponse;
import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.aws.AwsConnectionProvider;
import com.example.kkaribackend.global.aws.AwsInfraCollector;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 작업 저장 커밋 -> 비동기 실행 -> 상태 커밋까지 실제 컨텍스트에서 확인한다
@SpringBootTest
class AnalysisFlowTest {
    @Autowired
    private StartAnalysisService startAnalysisService;
    @Autowired
    private QueryAnalysisJobService queryAnalysisJobService;
    @Autowired
    private QueryDiscoveryService queryDiscoveryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AnalysisJobRepository analysisJobRepository;
    @MockitoBean
    private AwsConnectionProvider awsConnectionProvider;
    @MockitoBean
    private AwsInfraCollector awsInfraCollector;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        analysisJobRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 분석을_시작하면_비동기로_실행되어_결과를_조회할_수_있다() throws InterruptedException {
        User user = userRepository.save(User.builder().email("flow@kkari.com").password("pw").name("엔지니어")
            .companyName("MSP").role(Role.MSP_ENGINEER).build());
        Project project = Project.builder().user(user).companyName("고객사").contactName("담당").build();
        project.registerRoleArn("arn:aws:iam::123456789012:role/kkari");
        project.updateAwsConnectionStatus(AwsConnectionStatus.CONNECTED);
        projectRepository.save(project);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new CustomUserDetails(user.getId(), Role.MSP_ENGINEER), null, List.of()));
        when(awsInfraCollector.collect(any(), any())).thenReturn(InfraSnapshot.builder()
            .resources(Resources.builder()
                .ec2(List.of(new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a")))
                .build())
            .missing(List.of())
            .build());

        Long jobId = startAnalysisService.execute(project.getId()).jobId();

        AnalysisJobResponse job = queryAnalysisJobService.execute(jobId);
        for (int i = 0; i < 100 && job.status() != AnalysisStatus.SUCCEEDED; i++) {
            Thread.sleep(50);
            job = queryAnalysisJobService.execute(jobId);
        }
        assertThat(job.status()).isEqualTo(AnalysisStatus.SUCCEEDED);
        DiscoveryResponse discovery = queryDiscoveryService.execute(project.getId());
        assertThat(discovery.jobId()).isEqualTo(jobId);
        assertThat(discovery.result().resources().ec2()).hasSize(1);
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getStage())
            .isEqualTo(ProjectStage.ANALYZED);
    }
}
