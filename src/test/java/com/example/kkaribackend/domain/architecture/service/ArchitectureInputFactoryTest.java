package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.project.domain.Priority;
import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.*;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureInputFactoryTest {

    @Test
    void 분석_결과와_Terraform과_요구사항으로_입력을_만든다() {
        Map<String, BigDecimal> natCost = new HashMap<>();
        natCost.put("nat-1", null); // 가격을 못 구한 리소스
        InfraSnapshot snapshot = InfraSnapshot.builder()
            .region("ap-northeast-2")
            .resources(Resources.builder()
                .ec2(List.of(new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a")))
                .ebs(List.of(new EbsVolume("vol-1", "gp3", 100)))
                .rds(List.of(new RdsInstance("db-1", "db.t3.micro", "mysql", true, 20, "available")))
                .s3(List.of(new S3Bucket("logs", 1_610_612_736L)))
                .lambda(List.of(new LambdaFunction("fn", "python3.12", 128)))
                .loadBalancers(List.of(new LoadBalancer("web-alb", "application")))
                .natGateways(List.of(new NatGateway("nat-1", "available")))
                .build())
            .usage(new Usage(14, 3600, "Average", Map.of(
                "EC2", Map.of("i-1", Map.of("CPUUtilization", new MetricStats(12.34, 40.0, 55.0, 336))),
                "RDS", Map.of("db-1", Map.of("CPUUtilization", new MetricStats(5.0, null, null, 336),
                    "DatabaseConnections", new MetricStats(3.0, 4.0, 5.0, 336))))))
            .cost(new Cost("USD", "온디맨드", 730, Map.of(), Map.of(
                "EC2", Map.of("i-1", new BigDecimal("7.592")),
                "EBS", Map.of("vol-1", new BigDecimal("8")),
                "NAT", natCost)))
            .build();
        TerraformInfra terraform = new TerraformInfra(Resources.builder()
            .ec2(List.of(new Ec2Instance("i-1", "t3.micro", null, null, null)))
            .rds(List.of(new RdsInstance("db-1", "db.t3.micro", "mysql", null, null, null)))
            .build(), List.of(), null);
        Requirements requirements = Requirements.builder()
            .content("트래픽 급증 대비")
            .priorities(List.of(Priority.SECURITY, Priority.COST))
            .desiredArchitecture("EKS")
            .monthlyBudget(new BigDecimal("1500.50"))
            .currency("USD")
            .frozenResources(List.of("prod-rds", "logs"))
            .build();

        ArchitectureRequest request = ArchitectureInputFactory.create(snapshot, terraform, requirements, 3);

        assertThat(request.infra()).isEqualTo("""
            [AWS] 리전 ap-northeast-2
            EC2 i-1: t3.micro, Linux/UNIX, running, ap-northeast-2a / CPU 14일 평균 12.3%, p95 40.0% / 예상 월 7.59 USD
            EBS vol-1: gp3, 100GiB / 예상 월 8.00 USD
            RDS db-1: db.t3.micro, mysql, Multi-AZ, 20GiB, available / CPU 14일 평균 5.0%
            S3 logs: 1.50GiB
            LAMBDA fn: python3.12, 128MB
            ELB web-alb: application
            NAT nat-1: available
            [Terraform]
            EC2 i-1: t3.micro
            RDS db-1: db.t3.micro, mysql""");
        assertThat(request.goal()).isEqualTo("""
            트래픽 급증 대비
            원하는 구조: EKS
            월 예산: 1500.50 USD
            변경 금지 리소스: prod-rds, logs""");
        assertThat(request.priorities()).containsExactly("security", "cost");
        assertThat(request.count()).isEqualTo(3);
    }

    @Test
    void 비어_있는_값은_빼고_요구사항이_없으면_goal과_priorities를_비운다() {
        InfraSnapshot snapshot = InfraSnapshot.builder()
            .resources(Resources.builder()
                .ec2(List.of(new Ec2Instance("i-1", "t3.micro", null, null, null)))
                .build())
            .build();

        ArchitectureRequest request = ArchitectureInputFactory.create(snapshot, null, null, 5);

        assertThat(request.infra()).isEqualTo("[AWS]\nEC2 i-1: t3.micro");
        assertThat(request.goal()).isEmpty();
        assertThat(request.priorities()).isEmpty();
    }

    @Test
    void 선택_항목이_없는_요구사항은_본문만_goal로_쓴다() {
        Requirements requirements = Requirements.builder()
            .content("비용 절감")
            .priorities(List.of(Priority.COST))
            .desiredArchitecture(" ")
            .frozenResources(List.of())
            .build();

        ArchitectureRequest request = ArchitectureInputFactory.create(
            InfraSnapshot.builder().build(), null, requirements, 5);

        assertThat(request.infra()).isEqualTo("[AWS]");
        assertThat(request.goal()).isEqualTo("비용 절감");
        assertThat(request.priorities()).containsExactly("cost");
    }
}
