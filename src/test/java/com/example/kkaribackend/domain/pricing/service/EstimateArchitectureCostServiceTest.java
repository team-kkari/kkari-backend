package com.example.kkaribackend.domain.pricing.service;

import com.example.kkaribackend.domain.pricing.presentation.dto.request.ArchitectureNode;
import com.example.kkaribackend.domain.pricing.presentation.dto.request.ArchitectureNode.Pricing;
import com.example.kkaribackend.domain.pricing.presentation.dto.request.EstimateCostRequest;
import com.example.kkaribackend.domain.pricing.presentation.dto.response.CostReport;
import com.example.kkaribackend.global.aws.AwsPricingCalculator;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
import com.example.kkaribackend.global.aws.exception.AwsErrorCode;
import com.example.kkaribackend.global.aws.exception.AwsException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstimateArchitectureCostServiceTest {
    private static final BigDecimal PRICE = new BigDecimal("1.25");

    private final List<UsageLine> sentLines = new ArrayList<>();
    private AwsPricingCalculator calculator;
    private EstimateArchitectureCostService service;
    private Function<UsageLine, LinePrice> priceOf = line -> LinePrice.priced(PRICE);

    @BeforeEach
    void setUp() {
        calculator = mock(AwsPricingCalculator.class);
        when(calculator.isConfigured()).thenReturn(true);
        when(calculator.price(anyList())).thenAnswer(invocation -> {
            List<UsageLine> lines = invocation.getArgument(0);
            sentLines.addAll(lines);
            return lines.stream().map(priceOf).toList();
        });
        service = new EstimateArchitectureCostService(calculator);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kinds")
    void kind별로_서울_리전_사용량_줄을_보낸다(String kind, Pricing pricing, List<Tuple> expected) {
        CostReport report = estimate(node("n", pricing));

        assertThat(sentLines).extracting(UsageLine::serviceCode, UsageLine::primaryUsageType, UsageLine::operation,
            UsageLine::amount).containsExactlyElementsOf(expected);
        assertThat(report.items().getFirst().monthlyUsd())
            .isEqualByComparingTo(PRICE.multiply(BigDecimal.valueOf(expected.size())));
    }

    // count 2, 730h, 100GB, 50GB 전송, 요청 3백만, 쓰기 4백만, vCPU 1, 메모리 2GB, 100ms, LCU 5
    static Stream<Arguments> kinds() {
        return Stream.of(
            kind("ec2", p -> p.spec("m7g.large"),
                tuple("AmazonEC2", "APN2-BoxUsage:m7g.large", "RunInstances", 1460.0),
                tuple("AmazonEC2", "APN2-EBS:VolumeUsage.gp3", "CreateVolume-Gp3", 200.0)),
            kind("rds", p -> p.spec("db.m5.large").engine("PostgreSQL").multiAz(true),
                tuple("AmazonRDS", "APN2-Multi-AZUsage:db.m5.large", "CreateDBInstance:0014", 1460.0),
                tuple("AmazonRDS", "APN2-RDS:Multi-AZ-GP3-Storage", "CreateDBInstance:0014", 100.0)),
            kind("aurora", p -> p.spec("db.r6g.large").engine("Aurora MySQL"),
                tuple("AmazonRDS", "APN2-InstanceUsage:db.r6g.large", "CreateDBInstance:0016", 1460.0),
                tuple("AmazonRDS", "APN2-Aurora:StorageUsage", "CreateDBInstance", 100.0),
                tuple("AmazonRDS", "APN2-Aurora:StorageIOUsage", "CreateDBInstance", 3e6)),
            kind("elasticache", p -> p.spec("cache.r7g.large"),
                tuple("AmazonElastiCache", "APN2-NodeUsage:cache.r7g.large", "CreateCacheCluster:Valkey", 1460.0)),
            kind("elasticache_serverless", p -> p,
                tuple("AmazonElastiCache", "APN2-CachedData:Valkey", "CreateServerlessCache", 73000.0),
                tuple("AmazonElastiCache", "APN2-ElastiCacheProcessingUnits:Valkey", "CreateServerlessCache", 3e6)),
            kind("alb", p -> p,
                tuple("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Application", 1460.0),
                tuple("AWSELB", "APN2-LCUUsage", "LoadBalancing:Application", 7300.0)),
            kind("nlb", p -> p,
                tuple("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Network", 1460.0),
                tuple("AWSELB", "APN2-LCUUsage", "LoadBalancing:Network", 7300.0)),
            kind("nat_gateway", p -> p,
                tuple("AmazonEC2", "APN2-NatGateway-Hours", "NatGateway", 1460.0),
                tuple("AmazonEC2", "APN2-NatGateway-Bytes", "NatGateway", 50.0)),
            kind("vpc_interface_endpoint", p -> p,
                tuple("AmazonVPC", "APN2-VpcEndpoint-Hours", "VpcEndpoint", 1460.0),
                tuple("AmazonVPC", "APN2-VpcEndpoint-Bytes", "VpcEndpoint", 50.0)),
            kind("data_transfer_out", p -> p,
                tuple("AWSDataTransfer", "APN2-DataTransfer-Out-Bytes", "", 50.0)),
            kind("s3", p -> p.spec("standard_ia"),
                tuple("AmazonS3", "APN2-TimedStorage-SIA-ByteHrs", "", 100.0)),
            kind("efs", p -> p,
                tuple("AmazonEFS", "APN2-TimedStorage-ByteHrs", "", 100.0)),
            kind("ebs", p -> p.spec("io1"),
                tuple("AmazonEC2", "APN2-EBS:VolumeUsage.piops", "CreateVolume-P-IOPS", 100.0)),
            kind("lambda", p -> p.spec("arm"),
                tuple("AWSLambda", "APN2-Request-ARM", "", 3e6),
                tuple("AWSLambda", "APN2-Lambda-GB-Second-ARM", "", 600000.0)),
            kind("fargate", p -> p,
                tuple("AmazonECS", "APN2-Fargate-vCPU-Hours:perCPU", "", 1460.0),
                tuple("AmazonECS", "APN2-Fargate-GB-Hours", "", 2920.0)),
            kind("eks", p -> p,
                tuple("AmazonEKS", "APN2-AmazonEKS-Hours:perCluster", "CreateOperation", 1460.0)),
            kind("dynamodb", p -> p,
                tuple("AmazonDynamoDB", "APN2-ReadRequestUnits", "PayPerRequestThroughput", 3e6),
                tuple("AmazonDynamoDB", "APN2-WriteRequestUnits", "PayPerRequestThroughput", 4e6),
                tuple("AmazonDynamoDB", "APN2-TimedStorage-ByteHrs", "", 100.0)),
            kind("cloudfront", p -> p,
                tuple("AmazonCloudFront", "AP-DataTransfer-Out-Bytes", "", 50.0),
                tuple("AmazonCloudFront", "AP-Requests-Tier2-HTTPS", "", 3e6)),
            kind("waf", p -> p,
                tuple("awswaf", "APN2-WebACL", "", 2.0),
                tuple("awswaf", "APN2-Rule", "", 5.0),
                tuple("awswaf", "APN2-Request", "", 3e6)),
            kind("route53", p -> p,
                tuple("AmazonRoute53", "HostedZone", "", 2.0),
                tuple("AmazonRoute53", "DNS-Queries", "", 3e6)),
            kind("api_gateway", p -> p.spec("rest"),
                tuple("AmazonApiGateway", "APN2-ApiGatewayRequest", "ApiGatewayRequest", 3e6)),
            kind("sqs", p -> p,
                tuple("AWSQueueService", "APN2-Requests-Tier1", "", 3e6)));
    }

    @Test
    void RDS_xlarge는_xl_이름도_후보로_보낸다() {
        estimate(
            node("db", pricing("rds").spec("db.r6g.xlarge").engine("MySQL").build()),
            node("aurora", pricing("aurora").spec("db.r6g.2xlarge").engine("Aurora PostgreSQL").build()));

        assertThat(sentLines).extracting(UsageLine::usageTypes).contains(
            List.of("APN2-InstanceUsage:db.r6g.xlarge", "APN2-InstanceUsage:db.r6g.xl"),
            List.of("APN2-InstanceUsage:db.r6g.2xlarge", "APN2-InstanceUsage:db.r6g.2xl"));
    }

    @Test
    void 사용량이_모두_0이거나_시간_요금의_시간이_0이면_0달러가_아니라_추정_불가다() {
        CostReport report = estimate(
            node("web", pricing("ec2").spec("t3.micro").count(0).build()),
            node("alb", pricing("alb").hoursPerMonth(0).build()),
            node("bucket", pricing("s3").storageGb(0).build()),
            node("egress", pricing("data_transfer_out").dataGbMonth(0).build()));

        assertThat(report.items()).extracting(CostReport.Item::monthlyUsd, CostReport.Item::formula).containsOnly(
            tuple(null, "사용량 0 — 추정 불가"));
        assertThat(report.unpriced()).containsExactly("web 라벨", "alb 라벨", "bucket 라벨", "egress 라벨");
        assertThat(report.monthlyTotalUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sentLines).isEmpty();
    }

    @Test
    void 시간_요금이_없는_kind는_일부_사용량만_있어도_계산한다() {
        CostReport report = estimate(
            node("table", pricing("dynamodb").requestsMillionMonth(0).writeRequestsMillionMonth(0).build()),
            node("dns", pricing("route53").count(0).build()));

        assertThat(report.items()).extracting(CostReport.Item::monthlyUsd).doesNotContainNull();
        assertThat(report.unpriced()).isEmpty();
    }

    @Test
    void 추정할_수_없는_구성요소는_null이고_합계에서_빠진다() {
        priceOf = line -> line.primaryUsageType().contains("nope")
            ? LinePrice.unpriced("가격 계산 실패: BAD_REQUEST") : LinePrice.priced(PRICE);

        CostReport report = estimate(
            node("users", pricing("free").build()),
            node("web", pricing("ec2").spec("t3.micro").storageGb(0).build()),
            node("bad-type", pricing("ec2").spec("nope.large").build()),
            node("s3", pricing("s3").spec("deep_archive").build()),
            node("db", pricing("rds").spec("db.t3.micro").engine("Oracle").build()),
            node("shield", pricing("unpriced").build()),
            node("x", pricing("kinesis").build()));

        assertThat(report.items()).extracting(CostReport.Item::nodeId, CostReport.Item::monthlyUsd,
            CostReport.Item::formula).containsExactly(
            tuple("users", BigDecimal.ZERO, "무료"),
            tuple("web", PRICE, "APN2-BoxUsage:t3.micro 1460 = 1.25 $"),
            tuple("bad-type", null, "가격 계산 실패: BAD_REQUEST — 추정 불가"),
            tuple("s3", null, "지원하지 않는 S3 스토리지 클래스 'deep_archive' — 추정 불가"),
            tuple("db", null, "지원하지 않는 RDS 엔진 'Oracle' — 추정 불가"),
            tuple("shield", null, "가격표에 없음 — 추정 불가"),
            tuple("x", null, "가격표에 없음 — 추정 불가"));
        assertThat(report.monthlyTotalUsd()).isEqualByComparingTo(PRICE);
        assertThat(report.unpriced()).containsExactly("bad-type 라벨", "s3 라벨", "db 라벨", "shield 라벨", "x 라벨");
        assertThat(report.assumptions()).hasSize(4).last().asString().startsWith("계산일 ");
        assertThat(sentLines).extracting(UsageLine::primaryUsageType)
            .containsExactly("APN2-BoxUsage:t3.micro", "APN2-BoxUsage:nope.large", "APN2-EBS:VolumeUsage.gp3");
    }

    @Test
    void 가격_계산_호출이_실패하면_일부_결과_대신_502로_실패한다() {
        priceOf = line -> LinePrice.callFailed("가격 계산 실패: ThrottlingException (HTTP 400)");

        assertThatThrownBy(() -> estimate(node("nat", pricing("nat_gateway").build())))
            .isInstanceOfSatisfying(AwsException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AwsErrorCode.AWS_REQUEST_FAILED));
    }

    @Test
    void 가격_조회_키가_없으면_503으로_실패하고_API를_부르지_않는다() {
        when(calculator.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> estimate(node("nat", pricing("nat_gateway").build())))
            .isInstanceOfSatisfying(AwsException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AwsErrorCode.AWS_CREDENTIALS_UNAVAILABLE));
        verify(calculator, never()).price(anyList());
    }

    private static Arguments kind(String kind, Function<Pricing.PricingBuilder, Pricing.PricingBuilder> spec,
                                  Tuple... expected) {
        return Arguments.of(kind, spec.apply(pricing(kind)).build(), List.of(expected));
    }

    private static Pricing.PricingBuilder pricing(String kind) {
        return Pricing.builder().kind(kind).spec("").engine("")
            .count(2).hoursPerMonth(730).storageGb(100).dataGbMonth(50)
            .requestsMillionMonth(3).writeRequestsMillionMonth(4)
            .vcpu(1).memoryGb(2).durationMs(100).lcu(5);
    }

    private CostReport estimate(ArchitectureNode... nodes) {
        return service.execute(new EstimateCostRequest(List.of(nodes)));
    }

    private static ArchitectureNode node(String id, Pricing pricing) {
        return new ArchitectureNode(id, id + " 라벨", pricing);
    }
}
