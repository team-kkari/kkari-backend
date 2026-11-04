package com.example.kkaribackend.domain.analysis.domain.repository;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.repository.ProjectRepository;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AnalysisJobRepositoryTest {
    @Autowired
    private AnalysisJobRepository analysisJobRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 최근_성공한_결과는_이후_실패한_작업과_관계없이_JSON으로_다시_읽힌다() {
        User user = userRepository.save(User.builder().email("a@kkari.com").password("pw").name("엔지니어")
            .companyName("MSP").role(Role.values()[0]).build());
        Project project = projectRepository.save(Project.builder().user(user).companyName("고객사").contactName("담당")
            .build());

        Map<String, BigDecimal> monthly = new HashMap<>();
        monthly.put("i-1", new BigDecimal("9.49"));
        monthly.put("fn", null);
        InfraSnapshot snapshot = InfraSnapshot.builder()
            .region("ap-northeast-2")
            .collectedAt(Instant.parse("2026-11-05T00:00:00Z"))
            .resources(Resources.builder()
                .ec2(List.of(new Ec2Instance("i-1", "t3.micro", "running", "ap-northeast-2a")))
                .build())
            .cost(new Cost("USD", "ON_DEMAND_LIST_PRICE", 730, Map.of("EC2", "단가 × 730h"), monthly))
            .missing(List.of(new Missing("EBS", "AccessDenied (HTTP 403)")))
            .build();

        AnalysisJob succeeded = AnalysisJob.builder().project(project).build();
        succeeded.succeed(objectMapper.writeValueAsString(snapshot));
        analysisJobRepository.save(succeeded);
        AnalysisJob failed = AnalysisJob.builder().project(project).build();
        failed.fail("고객사 Role AssumeRole 실패");
        analysisJobRepository.save(failed);
        entityManager.flush();
        entityManager.clear();

        AnalysisJob latest = analysisJobRepository
            .findFirstByProjectIdAndStatusOrderByIdDesc(project.getId(), AnalysisStatus.SUCCEEDED)
            .orElseThrow();
        assertThat(latest.getId()).isEqualTo(succeeded.getId());
        assertThat(objectMapper.readValue(latest.getResult(), InfraSnapshot.class)).isEqualTo(snapshot);
        assertThat(analysisJobRepository.findFirstByProjectIdAndStatusInOrderByIdDesc(
            project.getId(), AnalysisStatus.IN_PROGRESS)).isEmpty();
    }
}
