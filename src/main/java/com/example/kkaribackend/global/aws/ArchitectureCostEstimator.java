package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.Status;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
import com.example.kkaribackend.global.aws.PricingCodes.EbsUsage;
import com.example.kkaribackend.global.aws.dto.ArchitectureNode;
import com.example.kkaribackend.global.aws.dto.ArchitectureNode.Pricing;
import com.example.kkaribackend.global.aws.dto.CostReport;
import com.example.kkaribackend.global.aws.exception.AwsErrorCode;
import com.example.kkaribackend.global.aws.exception.AwsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.kkaribackend.global.aws.PricingCodes.AMAZON_EC2;
import static com.example.kkaribackend.global.aws.PricingCodes.AMAZON_RDS;
import static com.example.kkaribackend.global.aws.PricingCodes.AWS_ELB;
import static com.example.kkaribackend.global.aws.PricingCodes.EBS_USAGES;
import static com.example.kkaribackend.global.aws.PricingCodes.LB_OPERATIONS;
import static com.example.kkaribackend.global.aws.PricingCodes.RDS_OPERATIONS;
import static com.example.kkaribackend.global.aws.PricingCodes.SEOUL;

/**
 * AI 서버 설계안의 구성요소(kind)를 서울 리전 AWS Pricing Calculator 사용량 줄로 바꿔 월 비용을 계산한다.
 */
@Component
@RequiredArgsConstructor
public class ArchitectureCostEstimator {
    private static final double MILLION = 1_000_000;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String NOT_ESTIMABLE = " — 추정 불가";
    private static final Map<String, String> RDS_ENGINES = Map.of(
        "MySQL", RDS_OPERATIONS.get("mysql"),
        "PostgreSQL", RDS_OPERATIONS.get("postgres"),
        "MariaDB", RDS_OPERATIONS.get("mariadb"));
    // Aurora MySQL 스토리지 줄은 엔진 코드 없는 operation 만 받는다
    private static final Map<String, AuroraOperations> AURORA_ENGINES = Map.of(
        "Aurora MySQL", new AuroraOperations(RDS_OPERATIONS.get("aurora-mysql"), "CreateDBInstance"),
        "Aurora PostgreSQL", new AuroraOperations(RDS_OPERATIONS.get("aurora-postgresql"),
            RDS_OPERATIONS.get("aurora-postgresql")));
    private static final Map<String, String> CACHE_ENGINES = Map.of(
        "Valkey", "CreateCacheCluster:Valkey",
        "Redis", "CreateCacheCluster:0002",
        "Memcached", "CreateCacheCluster:0001");
    private static final Map<String, String> S3_CLASSES = Map.of(
        "standard", "TimedStorage-ByteHrs",
        "standard_ia", "TimedStorage-SIA-ByteHrs",
        "onezone_ia", "TimedStorage-ZIA-ByteHrs",
        "glacier_ir", "TimedStorage-GIR-ByteHrs",
        "glacier_flexible", "TimedStorage-GlacierByteHrs",
        "intelligent_tiering", "TimedStorage-INT-FA-ByteHrs");
    private static final Map<String, String> EFS_CLASSES = Map.of(
        "standard", "TimedStorage-ByteHrs",
        "ia", "IATimedStorage-ByteHrs",
        "one_zone", "TimedStorage-Z-ByteHrs");
    private static final Map<String, String> ARCHITECTURES = Map.of("x86", "", "arm", "-ARM");
    private static final Map<String, ApiUsage> API_TYPES = Map.of(
        "http", new ApiUsage("ApiGatewayHttpRequest", "ApiGatewayHttpApi"),
        "rest", new ApiUsage("ApiGatewayRequest", "ApiGatewayRequest"));

    private final AwsPricingCalculator calculator;

    public CostReport estimate(List<ArchitectureNode> nodes) {
        if (!calculator.isConfigured()) {
            throw new AwsException(AwsErrorCode.AWS_CREDENTIALS_UNAVAILABLE);
        }
        List<NodePlan> plans = nodes.stream().map(ArchitectureCostEstimator::plan).toList();
        List<UsageLine> usageLines = plans.stream().flatMap(plan -> plan.lines().stream()).toList();
        List<LinePrice> prices = calculator.price(usageLines);
        // 일부만 계산된 결과로 설계안을 비교하면 틀린 순위가 나오므로 전체를 실패시킨다
        if (prices.stream().anyMatch(price -> price.status() == Status.CALL_FAILED)) {
            throw new AwsException(AwsErrorCode.AWS_REQUEST_FAILED);
        }

        List<CostReport.Item> items = new ArrayList<>();
        int next = 0;
        for (NodePlan plan : plans) {
            items.add(plan.item(prices.subList(next, next + plan.lines().size())));
            next += plan.lines().size();
        }
        return CostReport.builder()
            .monthlyTotalUsd(items.stream().map(CostReport.Item::monthlyUsd).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .items(items)
            .unpriced(items.stream().filter(item -> item.monthlyUsd() == null).map(CostReport.Item::label).toList())
            .assumptions(List.of(
                "서울 리전(ap-northeast-2) 온디맨드 기준",
                "RI·Savings Plans·프리티어 미적용",
                "AWS Pricing Calculator 기준 (구간 요금은 총량이 속한 구간 단가 적용)",
                "계산일 " + LocalDate.now(KST)))
            .build();
    }

    private static NodePlan plan(ArchitectureNode node) {
        try {
            List<UsageLine> lines = lines(node.pricing());
            if (lacksMainUsage(lines)) {
                return new NodePlan(node, List.of(), "사용량 0");
            }
            return new NodePlan(node, lines, null);
        } catch (UnsupportedSpecException e) {
            return new NodePlan(node, List.of(), e.getMessage());
        }
    }

    // kind 마다 첫 줄이 꼭 있어야 하는 사용량이다. AI 가 값을 빠뜨리면 0 이 와서 0 달러로 보이므로 추정 불가로 둔다
    private static boolean lacksMainUsage(List<UsageLine> lines) {
        return !lines.isEmpty() && lines.getFirst().amount() == 0;
    }

    private static List<UsageLine> lines(Pricing p) {
        double hours = p.hoursPerMonth() * p.count();
        double requests = p.requestsMillionMonth() * MILLION;
        return switch (p.kind()) {
            case "free" -> List.of();
            case "ec2" -> ec2(p, hours);
            case "rds" -> rds(p, hours);
            case "aurora" -> aurora(p, hours, requests);
            case "elasticache" -> List.of(seoul("AmazonElastiCache", "NodeUsage:" + p.spec(),
                pick(CACHE_ENGINES, orDefault(p.engine(), "Valkey"), "ElastiCache 엔진"), hours));
            case "elasticache_serverless" -> List.of(
                seoul("AmazonElastiCache", "CachedData:Valkey", "CreateServerlessCache",
                    p.storageGb() * p.hoursPerMonth()),
                seoul("AmazonElastiCache", "ElastiCacheProcessingUnits:Valkey", "CreateServerlessCache", requests));
            case "alb" -> loadBalancer(LB_OPERATIONS.get("application"), p, hours);
            case "nlb" -> loadBalancer(LB_OPERATIONS.get("network"), p, hours);
            case "nat_gateway" -> List.of(
                seoul(AMAZON_EC2, "NatGateway-Hours", "NatGateway", hours),
                seoul(AMAZON_EC2, "NatGateway-Bytes", "NatGateway", p.dataGbMonth()));
            case "vpc_interface_endpoint" -> List.of(
                seoul("AmazonVPC", "VpcEndpoint-Hours", "VpcEndpoint", hours),
                seoul("AmazonVPC", "VpcEndpoint-Bytes", "VpcEndpoint", p.dataGbMonth()));
            case "data_transfer_out" -> List.of(
                seoul("AWSDataTransfer", "DataTransfer-Out-Bytes", "", p.dataGbMonth()));
            case "s3" -> List.of(seoul("AmazonS3",
                pick(S3_CLASSES, orDefault(p.spec(), "standard"), "S3 스토리지 클래스"), "", p.storageGb()));
            case "efs" -> List.of(seoul("AmazonEFS",
                pick(EFS_CLASSES, orDefault(p.spec(), "standard"), "EFS 스토리지 클래스"), "", p.storageGb()));
            case "ebs" -> {
                EbsUsage ebs = pick(EBS_USAGES, orDefault(p.spec(), "gp3"), "EBS 볼륨 타입");
                yield List.of(seoul(AMAZON_EC2, ebs.usageType(), ebs.operation(), p.storageGb()));
            }
            case "lambda" -> {
                String arch = pick(ARCHITECTURES, orDefault(p.spec(), "x86"), "아키텍처");
                double gbSeconds = requests * p.durationMs() / 1000 * p.memoryGb();
                yield List.of(
                    seoul("AWSLambda", "Request" + arch, "", requests),
                    seoul("AWSLambda", "Lambda-GB-Second" + arch, "", gbSeconds));
            }
            case "fargate" -> {
                String arch = pick(ARCHITECTURES, orDefault(p.spec(), "x86"), "아키텍처");
                yield List.of(
                    seoul("AmazonECS", "Fargate" + arch + "-vCPU-Hours:perCPU", "", hours * p.vcpu()),
                    seoul("AmazonECS", "Fargate" + arch + "-GB-Hours", "", hours * p.memoryGb()));
            }
            case "eks" -> List.of(seoul("AmazonEKS", "AmazonEKS-Hours:perCluster", "CreateOperation", hours));
            case "dynamodb" -> List.of(
                seoul("AmazonDynamoDB", "ReadRequestUnits", "PayPerRequestThroughput", requests),
                seoul("AmazonDynamoDB", "WriteRequestUnits", "PayPerRequestThroughput",
                    p.writeRequestsMillionMonth() * MILLION),
                seoul("AmazonDynamoDB", "TimedStorage-ByteHrs", "", p.storageGb()));
            // CloudFront 는 리전이 아니라 대륙(AP) 단위 요금이다
            case "cloudfront" -> List.of(
                line("AmazonCloudFront", "AP-DataTransfer-Out-Bytes", "", p.dataGbMonth()),
                line("AmazonCloudFront", "AP-Requests-Tier2-HTTPS", "", requests));
            case "waf" -> waf(p, requests);
            // Route 53 은 글로벌 서비스라 리전 코드가 없다
            case "route53" -> List.of(
                line("AmazonRoute53", "HostedZone", "", p.count()),
                line("AmazonRoute53", "DNS-Queries", "", requests));
            case "api_gateway" -> {
                ApiUsage api = pick(API_TYPES, orDefault(p.spec(), "http"), "API Gateway 유형");
                yield List.of(seoul("AmazonApiGateway", api.usageType(), api.operation(), requests));
            }
            case "sqs" -> List.of(seoul("AWSQueueService", "Requests-Tier1", "", requests));
            default -> throw new UnsupportedSpecException("가격표에 없음");
        };
    }

    private static List<UsageLine> ec2(Pricing p, double hours) {
        UsageLine instance = seoul(AMAZON_EC2, "BoxUsage:" + p.spec(), "RunInstances", hours);
        if (p.storageGb() == 0) {
            return List.of(instance);
        }
        EbsUsage gp3 = EBS_USAGES.get("gp3");
        return List.of(instance, seoul(AMAZON_EC2, gp3.usageType(), gp3.operation(), p.storageGb() * p.count()));
    }

    private static List<UsageLine> rds(Pricing p, double hours) {
        String operation = pick(RDS_ENGINES, p.engine(), "RDS 엔진");
        String deployment = p.multiAz() ? "Multi-AZUsage:" : "InstanceUsage:";
        String storage = p.multiAz() ? "RDS:Multi-AZ-GP3-Storage" : "RDS:GP3-Storage";
        return List.of(
            PricingCodes.rdsInstance(SEOUL, deployment, p.spec(), operation, hours),
            seoul(AMAZON_RDS, storage, operation, p.storageGb()));
    }

    // Aurora 는 Multi-AZ 요금이 따로 없고 writer·reader 가 count 에 함께 들어온다
    private static List<UsageLine> aurora(Pricing p, double hours, double ioRequests) {
        AuroraOperations operations = pick(AURORA_ENGINES, p.engine(), "Aurora 엔진");
        return List.of(
            PricingCodes.rdsInstance(SEOUL, "InstanceUsage:", p.spec(), operations.instance(), hours),
            seoul(AMAZON_RDS, "Aurora:StorageUsage", operations.storage(), p.storageGb()),
            seoul(AMAZON_RDS, "Aurora:StorageIOUsage", operations.storage(), ioRequests));
    }

    private static List<UsageLine> loadBalancer(String operation, Pricing p, double hours) {
        return List.of(
            seoul(AWS_ELB, "LoadBalancerUsage", operation, hours),
            seoul(AWS_ELB, "LCUUsage", operation, p.lcu() * hours));
    }

    private static List<UsageLine> waf(Pricing p, double requests) {
        double webAcls = p.count();
        double rules = p.lcu();
        return List.of(
            seoul("awswaf", "WebACL", "", webAcls),
            seoul("awswaf", "Rule", "", rules),
            seoul("awswaf", "Request", "", requests));
    }

    private static UsageLine seoul(String serviceCode, String usageType, String operation, double amount) {
        return line(serviceCode, SEOUL + usageType, operation, amount);
    }

    private static UsageLine line(String serviceCode, String usageType, String operation, double amount) {
        return new UsageLine(serviceCode, usageType, operation, amount);
    }

    private static <T> T pick(Map<String, T> table, String key, String what) {
        T value = table.get(key);
        if (value == null) {
            throw new UnsupportedSpecException("지원하지 않는 " + what + " '" + key + "'");
        }
        return value;
    }

    private static String orDefault(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private record AuroraOperations(String instance, String storage) {
    }

    private record ApiUsage(String usageType, String operation) {
    }

    // unsupported: 계산 전에 추정할 수 없다고 판단한 이유
    private record NodePlan(ArchitectureNode node, List<UsageLine> lines, String unsupported) {
        CostReport.Item item(List<LinePrice> prices) {
            if (unsupported != null) {
                return item(null, unsupported + NOT_ESTIMABLE);
            }
            if (lines.isEmpty()) {
                return item(BigDecimal.ZERO, "무료");
            }
            BigDecimal total = BigDecimal.ZERO;
            List<String> terms = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                LinePrice price = prices.get(i);
                if (price.monthlyUsd() == null) {
                    return item(null, price.failure() + NOT_ESTIMABLE);
                }
                total = total.add(price.monthlyUsd());
                terms.add(term(lines.get(i), price.monthlyUsd()));
            }
            return item(total, String.join(" + ", terms));
        }

        private CostReport.Item item(BigDecimal monthlyUsd, String formula) {
            return new CostReport.Item(node.id(), node.label(), node.pricing().kind(), monthlyUsd, formula);
        }

        private static String term(UsageLine line, BigDecimal monthlyUsd) {
            String amount = BigDecimal.valueOf(line.amount()).stripTrailingZeros().toPlainString();
            return line.usageType() + " " + amount + " = " + monthlyUsd + " $";
        }
    }

    private static final class UnsupportedSpecException extends RuntimeException {
        UnsupportedSpecException(String reason) {
            super(reason);
        }
    }
}
