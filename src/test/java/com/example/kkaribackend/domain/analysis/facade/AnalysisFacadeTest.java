package com.example.kkaribackend.domain.analysis.facade;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.*;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalysisFacadeTest {
    private AnalysisJobRepository repository;
    private ObjectMapper mapper;
    private AnalysisFacade facade;
    private Project project;

    @BeforeEach
    void setUp() {
        repository = mock(AnalysisJobRepository.class);
        mapper = new ObjectMapper();
        facade = new AnalysisFacade(repository, mapper);
        project = Project.builder().id(1L).build();
        when(repository.findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(1L, AnalysisJobType.ANALYSIS, AnalysisStatus.SUCCEEDED))
            .thenReturn(Optional.empty());
    }

    @Test
    void 분석과_Terraform이_없으면_빈_문자열이다() {
        assertThat(facade.getInfraContext(project)).isEmpty();
    }

    @Test
    void Terraform만_있으면_구조와_사용률_비용_부재를_요약한다() {
        project.updateTerraform(new TerraformInfra(resources(), List.of(new Missing("STRUCTURE", "미지원")), Instant.EPOCH));
        assertThat(facade.getInfraContext(project)).contains("[Terraform]", "사용률·비용 없음", "EC2: 2개",
            "t3.small", "running", "EBS: 1개", "gp3, 100 GiB", "Multi-AZ=true", "누락 항목: 1개")
            .doesNotContain("[AWS 분석]");
    }

    @Test
    void 분석만_있으면_리전_스펙_사용률과_종류별_비용을_요약한다() {
        Map<String, BigDecimal> rdsCosts = new HashMap<>();
        rdsCosts.put("db", null);
        InfraSnapshot snapshot = new InfraSnapshot("ap-northeast-2", Instant.EPOCH, resources(),
            new Usage(14, 3600, "Average", Map.of(
                "EC2", Map.of("i-1", Map.of("CPUUtilization", new MetricStats(10.0, 30.0, 80.0, 100))),
                "RDS", Map.of("db", Map.of("CPUUtilization", new MetricStats(20.0, 40.0, 70.0, 100))))),
            new Cost("USD", "예상", 730, Map.of(), Map.of(
                "EC2", Map.of("i-1", new BigDecimal("10.50"), "i-2", new BigDecimal("20.50")),
                "RDS", rdsCosts, "EBS", Map.of())), List.of(new Missing("COST RDS", "누락")));
        store(snapshot);

        assertThat(facade.getInfraContext(project)).contains("[AWS 분석]", "ap-northeast-2", "1970-01-01T00:00:00Z",
            "EC2: 2개", "db.t3.small", "mysql", "월 예상 비용(USD)", "EC2: 31", "RDS: 추정 불가",
            "EBS: 0", "S3: 추정 불가", "평균=10.0, p95=30.0", "평균=20.0, p95=40.0", "누락 항목: 1개")
            .doesNotContain("[Terraform]");
    }

    @Test
    void 모르는_스펙과_사용률은_null_문자열_없이_요약한다() {
        // Terraform state 에는 EC2 platform 이 없다
        Resources unknown = new Resources(
            List.of(new Ec2Instance("i-1", "t3.micro", null, "running", "ap-northeast-2a")),
            List.of(new EbsVolume("vol-1", "gp3", null)),
            List.of(new RdsInstance("db", "db.t3.micro", "mysql", null, null, "available")),
            List.of(new S3Bucket("bucket", null)), List.of(new LambdaFunction("fn", null, null)),
            List.of(new LoadBalancer("lb", null)), List.of());
        store(InfraSnapshot.builder().resources(unknown)
            .usage(new Usage(14, 3600, "Average", Map.of("EC2",
                Map.of("i-1", Map.of("CPUUtilization", new MetricStats(null, null, null, 0)))))).build());
        project.updateTerraform(new TerraformInfra(unknown, List.of(), Instant.EPOCH));

        assertThat(facade.getInfraContext(project)).doesNotContain("null")
            .contains("t3.micro, running, ap-northeast-2a × 1", "EBS: 1개; gp3 × 1",
                "db.t3.micro, mysql, available × 1", "S3: 1개; 스펙 알 수 없음 × 1",
                "평균=알 수 없음, p95=알 수 없음");
    }

    @Test
    void 분석과_Terraform을_함께_요약한다() {
        store(InfraSnapshot.builder().region("ap-northeast-2").resources(resources()).build());
        project.updateTerraform(new TerraformInfra(resources(), List.of(), Instant.EPOCH));
        assertThat(facade.getInfraContext(project)).contains("[AWS 분석]", "[Terraform]");
    }

    @Test
    void 고객_답변을_질문_답_줄로_붙인다() {
        Project registered = Project.builder().id(1L).answers(Map.of("트래픽", "평일 낮 집중", "DB", "MySQL")).build();
        registered.updateTerraform(new TerraformInfra(resources(), List.of(), Instant.EPOCH));

        assertThat(facade.getInfraContext(registered)).startsWith("[Terraform]")
            .endsWith("\n[고객 답변]\nDB: MySQL\n트래픽: 평일 낮 집중");
    }

    @Test
    void 고객_답변만_있으면_답변_블록만_준다() {
        Project registered = Project.builder().id(1L).answers(Map.of("DB", "MySQL")).build();
        assertThat(facade.getInfraContext(registered)).isEqualTo("[고객 답변]\nDB: MySQL");
    }

    @Test
    void 인프라_요약은_고객_답변과_별개로_4000자에서_자른다() {
        Project registered = Project.builder().id(1L).answers(Map.of("DB", "MySQL")).build();
        registered.updateTerraform(new TerraformInfra(Resources.builder().ec2(manyInstances()).build(), List.of(), Instant.EPOCH));

        String result = facade.getInfraContext(registered);
        assertThat(result.substring(0, 4000)).startsWith("[Terraform]").endsWith("…(생략)");
        assertThat(result.substring(4000)).isEqualTo("\n[고객 답변]\nDB: MySQL");
    }

    @Test
    void 고객_답변은_2000자에서_생략_표시로_끝난다() {
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            answers.put("질문" + i, "답".repeat(2000));
        }
        Project registered = Project.builder().id(1L).answers(answers).build();
        registered.updateTerraform(new TerraformInfra(resources(), List.of(), Instant.EPOCH));

        String result = facade.getInfraContext(registered);
        String answersBlock = result.substring(result.indexOf("[고객 답변]"));
        assertThat(result).startsWith("[Terraform]");
        assertThat(answersBlock).hasSize(2000).endsWith("…(생략)");
    }

    @Test
    void 긴_요약은_4000자_안에서_생략_표시로_끝난다() {
        project.updateTerraform(new TerraformInfra(Resources.builder().ec2(manyInstances()).build(), List.of(), Instant.EPOCH));
        String result = facade.getInfraContext(project);
        assertThat(result).hasSize(4000).endsWith("…(생략)");
    }

    @Test
    void 같은_입력과_서로_다른_수집_순서는_같은_요약을_만든다() {
        Map<String, Map<String, MetricStats>> metrics = new LinkedHashMap<>();
        metrics.put("i-2", Map.of("CPU", new MetricStats(2.0, 3.0, 4.0, 1)));
        metrics.put("i-1", Map.of("CPU", new MetricStats(1.0, 2.0, 3.0, 1)));
        var snapshot = InfraSnapshot.builder().resources(resources())
            .usage(new Usage(14, 3600, "Average", Map.of("EC2", metrics))).build();
        store(snapshot);
        String first = facade.getInfraContext(project);
        assertThat(facade.getInfraContext(project)).isEqualTo(first);

        Map<String, Map<String, MetricStats>> reversed = new LinkedHashMap<>();
        reversed.put("i-1", metrics.get("i-1"));
        reversed.put("i-2", metrics.get("i-2"));
        var r = resources();
        store(InfraSnapshot.builder().resources(new Resources(r.ec2().reversed(), r.ebs(), r.rds(), r.s3(),
            r.lambda(), r.loadBalancers(), r.natGateways()))
            .usage(new Usage(14, 3600, "Average", Map.of("EC2", reversed))).build());
        assertThat(facade.getInfraContext(project)).isEqualTo(first);
    }

    private void store(InfraSnapshot snapshot) {
        AnalysisJob job = AnalysisJob.builder().project(project).build();
        job.succeed(mapper.writeValueAsString(snapshot));
        when(repository.findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(1L, AnalysisJobType.ANALYSIS, AnalysisStatus.SUCCEEDED))
            .thenReturn(Optional.of(job));
    }

    private List<Ec2Instance> manyInstances() {
        List<Ec2Instance> instances = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            instances.add(new Ec2Instance("i-" + i, "instance-type-" + i, "Linux/UNIX", "running", "ap-northeast-2a"));
        }
        return instances;
    }

    private Resources resources() {
        return new Resources(List.of(
            new Ec2Instance("i-2", "t3.small", "Linux/UNIX", "running", "ap-northeast-2a"),
            new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "stopped", "ap-northeast-2b")),
            List.of(new EbsVolume("vol-1", "gp3", 100)),
            List.of(new RdsInstance("db", "db.t3.small", "mysql", true, 100, "available")),
            List.of(), List.of(), List.of(), List.of());
    }
}
