package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.EbsVolume;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LambdaFunction;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LoadBalancer;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.NatGateway;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.RdsInstance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.S3Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsCostEstimatorTest {
    private static final BigDecimal PRICE = new BigDecimal("1.00");

    private final List<Missing> missing = new ArrayList<>();
    private final List<UsageLine> sentLines = new ArrayList<>();
    private AwsPricingCalculator calculator;

    // usageType 에 "unknown" 이 들어간 줄은 가격을 찾지 못한 것으로 응답한다
    @BeforeEach
    void setUp() {
        calculator = mock(AwsPricingCalculator.class);
        when(calculator.isConfigured()).thenReturn(true);
        when(calculator.price(anyList())).thenAnswer(invocation -> {
            List<UsageLine> lines = invocation.getArgument(0);
            sentLines.addAll(lines);
            return lines.stream().map(line -> line.primaryUsageType().contains("unknown")
                ? LinePrice.unpriced("AWS Price List 에 온디맨드 단가가 없습니다.")
                : LinePrice.priced(PRICE)).toList();
        });
    }

    @Test
    void EC2는_플랫폼별_operation으로_730시간을_계산한다() {
        Resources resources = Resources.builder()
            .ec2(List.of(
                new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a"),
                new Ec2Instance("i-2", "t3.micro", "Windows", "running", "ap-northeast-2a"),
                new Ec2Instance("i-3", "t3.micro", "Red Hat Enterprise Linux", "running", "ap-northeast-2a"),
                new Ec2Instance("i-4", "t3.micro", "SUSE Linux", "running", "ap-northeast-2a"),
                new Ec2Instance("i-5", "t3.micro", "Windows with SQL Server Standard", "running", "ap-northeast-2a"),
                new Ec2Instance("i-6", "t3.micro", "Linux/UNIX", "stopped", "ap-northeast-2a"),
                new Ec2Instance("i-7", "unknown.large", "Linux/UNIX", "running", "ap-northeast-2a")))
            .build();

        Cost cost = estimator("ap-northeast-2").estimate(resources, missing);

        assertThat(cost.currency()).isEqualTo("USD");
        assertThat(cost.basis()).isEqualTo("AWS_PRICE_LIST_ON_DEMAND");
        assertThat(sentLines).extracting(UsageLine::serviceCode, UsageLine::primaryUsageType, UsageLine::operation,
            UsageLine::amount).containsExactly(
            tuple("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", 730.0),
            tuple("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances:0002", 730.0),
            tuple("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances:0010", 730.0),
            tuple("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances:000g", 730.0),
            tuple("AmazonEC2", "APN2-BoxUsage:unknown.large", "RunInstances", 730.0));
        Map<String, BigDecimal> ec2 = cost.monthlyUsdByResource().get("EC2");
        assertThat(ec2.keySet()).containsExactly("i-1", "i-2", "i-3", "i-4", "i-5", "i-6", "i-7");
        assertThat(ec2).containsEntry("i-2", PRICE)
            .containsEntry("i-5", null)
            .containsEntry("i-6", BigDecimal.ZERO)
            .containsEntry("i-7", null);
        assertThat(missing).containsExactly(
            new Missing("COST EC2 i-5", "가격 계산을 지원하지 않는 플랫폼입니다: Windows with SQL Server Standard"),
            new Missing("COST EC2 i-7", "AWS Price List 에 온디맨드 단가가 없습니다."));
    }

    @Test
    void RDS는_Multi_AZ면_Multi_AZ_요금이고_Aurora는_인스턴스_요금이다() {
        Resources resources = Resources.builder()
            .rds(List.of(
                new RdsInstance("db-1", "db.t3.micro", "mysql", false, 20, "available"),
                new RdsInstance("db-2", "db.m5.large", "postgres", true, 100, "available"),
                new RdsInstance("db-3", "db.t3.micro", "mariadb", true, 20, "available"),
                new RdsInstance("db-4", "db.r5.large", "aurora-mysql", false, 1, "available"),
                new RdsInstance("db-5", "db.r5.large", "aurora-postgresql", true, 1, "available"),
                new RdsInstance("db-6", "db.t3.micro", "oracle-se2", false, 20, "available"),
                new RdsInstance("db-7", "db.t3.micro", "mysql", true, 20, "stopped"),
                new RdsInstance("db-8", "db.r6g.xlarge", "mysql", false, 20, "available")))
            .build();

        Cost cost = estimator("ap-northeast-2").estimate(resources, missing);

        assertThat(sentLines).extracting(UsageLine::serviceCode, UsageLine::primaryUsageType, UsageLine::operation)
            .containsExactly(
                tuple("AmazonRDS", "APN2-InstanceUsage:db.t3.micro", "CreateDBInstance:0002"),
                tuple("AmazonRDS", "APN2-Multi-AZUsage:db.m5.large", "CreateDBInstance:0014"),
                tuple("AmazonRDS", "APN2-Multi-AZUsage:db.t3.micro", "CreateDBInstance:0018"),
                tuple("AmazonRDS", "APN2-InstanceUsage:db.r5.large", "CreateDBInstance:0016"),
                tuple("AmazonRDS", "APN2-InstanceUsage:db.r5.large", "CreateDBInstance:0021"),
                tuple("AmazonRDS", "APN2-InstanceUsage:db.r6g.xlarge", "CreateDBInstance:0002"));
        assertThat(sentLines.getLast().usageTypes())
            .containsExactly("APN2-InstanceUsage:db.r6g.xlarge", "APN2-InstanceUsage:db.r6g.xl");
        assertThat(cost.monthlyUsdByResource().get("RDS"))
            .containsEntry("db-5", PRICE)
            .containsEntry("db-6", null)
            .containsEntry("db-7", BigDecimal.ZERO);
        assertThat(missing).containsExactly(
            new Missing("COST RDS db-6", "가격 계산을 지원하지 않는 엔진입니다: oracle-se2"));
    }

    @Test
    void 스토리지와_네트워크_리소스를_사용량_줄로_바꾼다() {
        Resources resources = Resources.builder()
            .ebs(List.of(
                new EbsVolume("vol-1", "gp3", 100),
                new EbsVolume("vol-2", "io1", 50),
                new EbsVolume("vol-3", "standard", 8),
                new EbsVolume("vol-4", "gp2", null)))
            .s3(List.of(new S3Bucket("app", 4L << 30), new S3Bucket("logs", null)))
            .loadBalancers(List.of(
                new LoadBalancer("alb", "application"),
                new LoadBalancer("nlb", "network"),
                new LoadBalancer("gwlb", "gateway")))
            .natGateways(List.of(new NatGateway("nat-1", "available")))
            .build();

        Cost cost = estimator("ap-northeast-2").estimate(resources, missing);

        assertThat(sentLines).extracting(UsageLine::serviceCode, UsageLine::primaryUsageType, UsageLine::operation,
            UsageLine::amount).containsExactly(
            tuple("AmazonEC2", "APN2-EBS:VolumeUsage.gp3", "", 100.0),
            tuple("AmazonEC2", "APN2-EBS:VolumeUsage.piops", "", 50.0),
            tuple("AmazonEC2", "APN2-EBS:VolumeUsage", "", 8.0),
            tuple("AmazonS3", "APN2-TimedStorage-ByteHrs", "", 4.0),
            tuple("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Application", 730.0),
            tuple("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Network", 730.0),
            tuple("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Gateway", 730.0),
            tuple("AmazonEC2", "APN2-NatGateway-Hours", "NatGateway", 730.0));
        assertThat(cost.monthlyUsdByResource()).containsOnlyKeys("EBS", "S3", "ELB", "NAT");
        assertThat(cost.monthlyUsdByResource().get("EBS")).containsEntry("vol-4", null);
        assertThat(cost.monthlyUsdByResource().get("S3")).containsEntry("app", PRICE).containsEntry("logs", null);
        assertThat(missing).extracting(Missing::target).containsExactly("COST EBS vol-4", "COST S3 logs");
    }

    @Test
    void 종류가_다르면_같은_이름이어도_비용이_따로_남는다() {
        Resources resources = Resources.builder()
            .s3(List.of(new S3Bucket("app", 4L << 30)))
            .lambda(List.of(new LambdaFunction("app", "java21", 512), new LambdaFunction("worker", "python3.12", 128)))
            .build();

        Cost cost = estimator("ap-northeast-2").estimate(resources, missing);

        assertThat(cost.monthlyUsdByResource().get("S3")).containsEntry("app", PRICE);
        assertThat(cost.monthlyUsdByResource().get("LAMBDA")).containsEntry("app", null).containsEntry("worker", null);
        assertThat(missing).extracting(Missing::target).containsExactly("COST LAMBDA");
    }

    @Test
    void 지원하지_않는_리전이면_비용은_null이고_missing에_남긴다() {
        Resources resources = Resources.builder()
            .natGateways(List.of(new NatGateway("nat-1", "available")))
            .build();

        Cost cost = estimator("eu-west-1").estimate(resources, missing);

        assertThat(cost.monthlyUsdByResource()).isNull();
        assertThat(missing).containsExactly(new Missing("COST", "가격 계산을 지원하지 않는 리전입니다: eu-west-1"));
        verify(calculator, never()).price(anyList());
    }

    @Test
    void 가격_조회_키가_없으면_비용은_null이고_missing에_남긴다() {
        Resources resources = Resources.builder()
            .ec2(List.of(new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a")))
            .build();

        when(calculator.isConfigured()).thenReturn(false);

        Cost cost = estimator("ap-northeast-2").estimate(resources, missing);

        assertThat(cost.monthlyUsdByResource()).isNull();
        assertThat(cost.formulas()).containsKeys("EC2", "EBS", "RDS", "S3", "LAMBDA", "ELB", "NAT");
        assertThat(missing).containsExactly(new Missing("COST", "가격 조회용 AWS 자격증명이 설정되지 않았습니다."));
    }

    private AwsCostEstimator estimator(String region) {
        return new AwsCostEstimator(region, calculator);
    }
}
