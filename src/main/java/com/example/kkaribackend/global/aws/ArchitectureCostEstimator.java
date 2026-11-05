package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.AwsCostEstimator.EbsUsage;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
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
import java.util.stream.IntStream;

/**
 * AI 서버 설계안의 구성요소(kind)를 서울 리전 AWS Pricing Calculator 사용량 줄로 바꿔 월 비용을 계산한다.
 */
@Component
@RequiredArgsConstructor
public class ArchitectureCostEstimator {
    private static final String SEOUL = "APN2-";
    private static final double MILLION = 1_000_000;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String NOT_ESTIMABLE = " — 추정 불가";
    private static final Map<String, String> RDS_ENGINES = Map.of(
        "MySQL", "0002",
        "PostgreSQL", "0014",
        "MariaDB", "0018");
    // Aurora MySQL 스토리지 줄은 엔진 코드 없는 operation 만 받는다
    private static final Map<String, AuroraOperations> AURORA_ENGINES = Map.of(
        "Aurora MySQL", new AuroraOperations("CreateDBInstance:0016", "CreateDBInstance"),
        "Aurora PostgreSQL", new AuroraOperations("CreateDBInstance:0021", "CreateDBInstance:0021"));
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
        List<NodePlan> plans = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            plans.add(plan("n" + i, nodes.get(i)));
        }
        Map<String, LinePrice> prices = calculator.price(plans.stream().flatMap(plan -> plan.usageLines().stream()).toList());
        // 일부만 계산된 결과로 설계안을 비교하면 틀린 순위가 나오므로 전체를 실패시킨다
        if (prices.values().stream().anyMatch(LinePrice::callFailed)) {
            throw new AwsException(AwsErrorCode.AWS_REQUEST_FAILED);
        }

        List<CostReport.Item> items = plans.stream().map(plan -> plan.item(prices)).toList();
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

    private static NodePlan plan(String key, ArchitectureNode node) {
        try {
            return new NodePlan(key, node, lines(node.pricing()), null);
        } catch (UnsupportedSpecException e) {
            return new NodePlan(key, node, List.of(), e.getMessage());
        }
    }

    private static List<Line> lines(Pricing p) {
        double hours = p.hoursPerMonth() * p.count();
        double requests = p.requestsMillionMonth() * MILLION;
        return switch (p.kind()) {
            case "free" -> List.of();
            case "ec2" -> ec2(p, hours);
            case "rds" -> rds(p, hours);
            case "aurora" -> aurora(p, hours, requests);
            case "elasticache" -> List.of(seoul("AmazonElastiCache", "NodeUsage:" + p.spec(),
                pick(CACHE_ENGINES, or(p.engine(), "Valkey"), "ElastiCache 엔진"), hours));
            case "elasticache_serverless" -> List.of(
                seoul("AmazonElastiCache", "CachedData:Valkey", "CreateServerlessCache",
                    p.storageGb() * p.hoursPerMonth()),
                seoul("AmazonElastiCache", "ElastiCacheProcessingUnits:Valkey", "CreateServerlessCache", requests));
            case "alb" -> loadBalancer("LoadBalancing:Application", p, hours);
            case "nlb" -> loadBalancer("LoadBalancing:Network", p, hours);
            case "nat_gateway" -> List.of(
                seoul("AmazonEC2", "NatGateway-Hours", "NatGateway", hours),
                seoul("AmazonEC2", "NatGateway-Bytes", "NatGateway", p.dataGbMonth()));
            case "vpc_interface_endpoint" -> List.of(
                seoul("AmazonVPC", "VpcEndpoint-Hours", "VpcEndpoint", hours),
                seoul("AmazonVPC", "VpcEndpoint-Bytes", "VpcEndpoint", p.dataGbMonth()));
            case "data_transfer_out" -> List.of(
                seoul("AWSDataTransfer", "DataTransfer-Out-Bytes", "", p.dataGbMonth()));
            case "s3" -> List.of(seoul("AmazonS3",
                pick(S3_CLASSES, or(p.spec(), "standard"), "S3 스토리지 클래스"), "", p.storageGb()));
            case "efs" -> List.of(seoul("AmazonEFS",
                pick(EFS_CLASSES, or(p.spec(), "standard"), "EFS 스토리지 클래스"), "", p.storageGb()));
            case "ebs" -> {
                EbsUsage ebs = pick(AwsCostEstimator.EBS_USAGES, or(p.spec(), "gp3"), "EBS 볼륨 타입");
                yield List.of(seoul("AmazonEC2", ebs.usageType(), ebs.operation(), p.storageGb()));
            }
            case "lambda" -> {
                String arch = pick(ARCHITECTURES, or(p.spec(), "x86"), "아키텍처");
                double gbSeconds = requests * p.durationMs() / 1000 * p.memoryGb();
                yield List.of(
                    seoul("AWSLambda", "Request" + arch, "", requests),
                    seoul("AWSLambda", "Lambda-GB-Second" + arch, "", gbSeconds));
            }
            case "fargate" -> {
                String arch = pick(ARCHITECTURES, or(p.spec(), "x86"), "아키텍처");
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
            // lcu 에 규칙 수가 들어온다
            case "waf" -> List.of(
                seoul("awswaf", "WebACL", "", p.count()),
                seoul("awswaf", "Rule", "", p.lcu()),
                seoul("awswaf", "Request", "", requests));
            // Route 53 은 글로벌 서비스라 리전 코드가 없다
            case "route53" -> List.of(
                line("AmazonRoute53", "HostedZone", "", p.count()),
                line("AmazonRoute53", "DNS-Queries", "", requests));
            case "api_gateway" -> {
                ApiUsage api = pick(API_TYPES, or(p.spec(), "http"), "API Gateway 유형");
                yield List.of(seoul("AmazonApiGateway", api.usageType(), api.operation(), requests));
            }
            case "sqs" -> List.of(seoul("AWSQueueService", "Requests-Tier1", "", requests));
            default -> throw new UnsupportedSpecException("가격표에 없음");
        };
    }

    private static List<Line> ec2(Pricing p, double hours) {
        Line instance = seoul("AmazonEC2", "BoxUsage:" + p.spec(), "RunInstances", hours);
        if (p.storageGb() == 0) {
            return List.of(instance);
        }
        return List.of(instance,
            seoul("AmazonEC2", "EBS:VolumeUsage.gp3", "CreateVolume-Gp3", p.storageGb() * p.count()));
    }

    private static List<Line> rds(Pricing p, double hours) {
        String operation = "CreateDBInstance:" + pick(RDS_ENGINES, p.engine(), "RDS 엔진");
        String deployment = p.multiAz() ? "Multi-AZUsage:" : "InstanceUsage:";
        String storage = p.multiAz() ? "RDS:Multi-AZ-GP3-Storage" : "RDS:GP3-Storage";
        return List.of(
            rdsInstance(deployment + p.spec(), operation, hours),
            seoul("AmazonRDS", storage, operation, p.storageGb()));
    }

    // Aurora 는 Multi-AZ 요금이 따로 없고 writer·reader 가 count 에 함께 들어온다
    private static List<Line> aurora(Pricing p, double hours, double requests) {
        AuroraOperations operations = pick(AURORA_ENGINES, p.engine(), "Aurora 엔진");
        return List.of(
            rdsInstance("InstanceUsage:" + p.spec(), operations.instance(), hours),
            seoul("AmazonRDS", "Aurora:StorageUsage", operations.storage(), p.storageGb()),
            seoul("AmazonRDS", "Aurora:StorageIOUsage", operations.storage(), requests));
    }

    // RDS 인스턴스 usageType 은 xlarge 를 xl 로 줄여 쓰지만 줄이지 않는 패밀리도 있어,
    // 두 이름을 함께 보내고 가격이 나온 쪽을 쓴다. 한 타입에는 둘 중 하나만 있다
    private static Line rdsInstance(String usageType, String operation, double hours) {
        List<String> candidates = usageType.endsWith("xlarge")
            ? List.of(SEOUL + usageType, SEOUL + usageType.replaceFirst("xlarge$", "xl"))
            : List.of(SEOUL + usageType);
        return new Line("AmazonRDS", candidates, operation, hours);
    }

    private static List<Line> loadBalancer(String operation, Pricing p, double hours) {
        return List.of(
            seoul("AWSELB", "LoadBalancerUsage", operation, hours),
            seoul("AWSELB", "LCUUsage", operation, p.lcu() * hours));
    }

    private static Line seoul(String serviceCode, String usageType, String operation, double amount) {
        return line(serviceCode, SEOUL + usageType, operation, amount);
    }

    private static Line line(String serviceCode, String usageType, String operation, double amount) {
        return new Line(serviceCode, List.of(usageType), operation, amount);
    }

    private static <T> T pick(Map<String, T> table, String key, String what) {
        T value = table.get(key);
        if (value == null) {
            throw new UnsupportedSpecException("지원하지 않는 " + what + " '" + key + "'");
        }
        return value;
    }

    private static String or(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    // usageTypes: 같은 요금의 이름 후보. 가격이 나온 첫 후보를 쓴다
    private record Line(String serviceCode, List<String> usageTypes, String operation, double amount) {
    }

    private record AuroraOperations(String instance, String storage) {
    }

    private record ApiUsage(String usageType, String operation) {
    }

    // unsupported: 계산 전에 추정할 수 없다고 판단한 이유
    private record NodePlan(String key, ArchitectureNode node, List<Line> lines, String unsupported) {
        List<UsageLine> usageLines() {
            List<UsageLine> usage = new ArrayList<>();
            for (int l = 0; l < lines.size(); l++) {
                Line line = lines.get(l);
                for (int c = 0; c < line.usageTypes().size(); c++) {
                    usage.add(new UsageLine(lineKey(l, c), line.serviceCode(), line.usageTypes().get(c),
                        line.operation(), line.amount()));
                }
            }
            return usage;
        }

        CostReport.Item item(Map<String, LinePrice> prices) {
            if (unsupported != null) {
                return item(null, unsupported + NOT_ESTIMABLE);
            }
            if ("free".equals(node.pricing().kind())) {
                return item(BigDecimal.ZERO, "무료");
            }
            BigDecimal total = BigDecimal.ZERO;
            List<String> terms = new ArrayList<>();
            for (int l = 0; l < lines.size(); l++) {
                Line line = lines.get(l);
                int priced = firstPricedCandidate(l, prices);
                if (priced < 0) {
                    return item(null, prices.get(lineKey(l, 0)).failure() + NOT_ESTIMABLE);
                }
                BigDecimal usd = prices.get(lineKey(l, priced)).monthlyUsd();
                total = total.add(usd);
                terms.add(line.usageTypes().get(priced) + " " + plain(line.amount()) + " = " + usd + " $");
            }
            return item(total, String.join(" + ", terms));
        }

        private int firstPricedCandidate(int l, Map<String, LinePrice> prices) {
            return IntStream.range(0, lines.get(l).usageTypes().size())
                .filter(c -> prices.get(lineKey(l, c)).monthlyUsd() != null)
                .findFirst().orElse(-1);
        }

        // [a-zA-Z0-9]{1,10} 이어야 한다. 노드 100개·줄 3개·후보 2개면 n99l2c1 로 충분하다
        private String lineKey(int l, int c) {
            return key + "l" + l + "c" + c;
        }

        private CostReport.Item item(BigDecimal monthlyUsd, String formula) {
            return new CostReport.Item(node.id(), node.label(), node.pricing().kind(), monthlyUsd, formula);
        }

        private static String plain(double amount) {
            return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
        }
    }

    private static final class UnsupportedSpecException extends RuntimeException {
        UnsupportedSpecException(String reason) {
            super(reason);
        }
    }
}
