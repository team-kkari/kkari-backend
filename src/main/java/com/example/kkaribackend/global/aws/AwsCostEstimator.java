package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
import com.example.kkaribackend.global.aws.PricingCodes.EbsUsage;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.EbsVolume;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LoadBalancer;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.NatGateway;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.RdsInstance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.S3Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.example.kkaribackend.global.aws.PricingCodes.AMAZON_EC2;
import static com.example.kkaribackend.global.aws.PricingCodes.AWS_ELB;
import static com.example.kkaribackend.global.aws.PricingCodes.EBS_USAGES;
import static com.example.kkaribackend.global.aws.PricingCodes.LB_OPERATIONS;
import static com.example.kkaribackend.global.aws.PricingCodes.RDS_OPERATIONS;
import static com.example.kkaribackend.global.aws.PricingCodes.USAGE_TYPE_PREFIXES;

/**
 * 리소스를 AWS Pricing Calculator 사용량 줄로 바꿔 월 예상 비용(할인 전 정가)을 계산한다.
 */
@Component
public class AwsCostEstimator {
    private static final int HOURS_PER_MONTH = 730;
    private static final double BYTES_PER_GB = 1L << 30;
    private static final Map<String, String> FORMULAS = formulas();
    private static final Map<String, String> EC2_OPERATIONS = Map.of(
        "Linux/UNIX", "RunInstances",
        "Windows", "RunInstances:0002",
        "Red Hat Enterprise Linux", "RunInstances:0010",
        "SUSE Linux", "RunInstances:000g");

    private final String region;
    private final AwsPricingCalculator calculator;

    AwsCostEstimator(@Value("${aws.region}") String region, AwsPricingCalculator calculator) {
        this.region = region;
        this.calculator = calculator;
    }

    public Cost estimate(Resources resources, List<Missing> missing) {
        if (!calculator.isConfigured()) {
            missing.add(new Missing("COST", "가격 조회용 AWS 자격증명이 설정되지 않았습니다."));
            return cost(null);
        }
        String prefix = USAGE_TYPE_PREFIXES.get(region);
        if (prefix == null) {
            missing.add(new Missing("COST", "가격 계산을 지원하지 않는 리전입니다: " + region));
            return cost(null);
        }

        Sheet sheet = new Sheet(prefix, missing);
        sheet.section("EC2", resources.ec2(), sheet::addEc2);
        sheet.section("EBS", resources.ebs(), sheet::addEbs);
        sheet.section("RDS", resources.rds(), sheet::addRds);
        sheet.section("S3", resources.s3(), sheet::addS3);
        sheet.section("LAMBDA", resources.lambda(), function -> sheet.set(function.functionName(), null));
        if (resources.lambda() != null && !resources.lambda().isEmpty()) {
            missing.add(new Missing("COST LAMBDA", "요청 수·실행 시간에 따라 달라져 추정하지 않습니다."));
        }
        sheet.section("ELB", resources.loadBalancers(), sheet::addLoadBalancer);
        sheet.section("NAT", resources.natGateways(), sheet::addNatGateway);
        sheet.fill(calculator.price(sheet.lines()));
        return cost(sheet.monthly);
    }

    private static Cost cost(Map<String, Map<String, BigDecimal>> monthly) {
        return new Cost("USD", "AWS_PRICING_CALCULATOR_BEFORE_DISCOUNTS", HOURS_PER_MONTH, FORMULAS, monthly);
    }

    private record PricedResource(String type, String id, UsageLine line) {
    }

    // 한 번의 estimate 호출에서만 쓰는 작업 공간. 호출끼리 공유하지 않는다
    private static final class Sheet {
        private final String prefix;
        private final List<Missing> missing;
        private final Map<String, Map<String, BigDecimal>> monthly = new LinkedHashMap<>();
        private final List<PricedResource> priced = new ArrayList<>();
        private String type;

        Sheet(String prefix, List<Missing> missing) {
            this.prefix = prefix;
            this.missing = missing;
        }

        <T> void section(String type, List<T> resources, Consumer<T> add) {
            if (resources == null) {
                return;
            }
            this.type = type;
            monthly.put(type, new LinkedHashMap<>());
            resources.forEach(add);
        }

        void set(String id, BigDecimal monthlyUsd) {
            monthly.get(type).put(id, monthlyUsd);
        }

        void unpriced(String id, String reason) {
            set(id, null);
            missing.add(new Missing("COST " + type + " " + id, reason));
        }

        // 계산 결과가 오기 전까지 자리만 잡아 두어 리소스 순서를 유지한다
        void addLine(String id, UsageLine line) {
            set(id, null);
            priced.add(new PricedResource(type, id, line));
        }

        void addLine(String id, String serviceCode, String usageType, String operation, double amount) {
            addLine(id, new UsageLine(serviceCode, prefix + usageType, operation, amount));
        }

        void addEc2(Ec2Instance instance) {
            String operation = EC2_OPERATIONS.get(instance.platform());
            if ("stopped".equals(instance.state())) {
                set(instance.instanceId(), BigDecimal.ZERO);
            } else if (operation == null) {
                unpriced(instance.instanceId(), "가격 계산을 지원하지 않는 플랫폼입니다: " + instance.platform());
            } else {
                addLine(instance.instanceId(), AMAZON_EC2, "BoxUsage:" + instance.instanceType(), operation,
                    HOURS_PER_MONTH);
            }
        }

        void addEbs(EbsVolume volume) {
            EbsUsage usage = EBS_USAGES.get(volume.volumeType());
            if (usage == null) {
                unpriced(volume.volumeId(), "가격 계산을 지원하지 않는 볼륨 타입입니다: " + volume.volumeType());
            } else if (volume.sizeGiB() == null) {
                unpriced(volume.volumeId(), "볼륨 크기를 알 수 없습니다.");
            } else {
                addLine(volume.volumeId(), AMAZON_EC2, usage.usageType(), usage.operation(), volume.sizeGiB());
            }
        }

        // Aurora 는 Multi-AZ 요금이 따로 없고 복제본도 인스턴스마다 따로 과금된다
        void addRds(RdsInstance db) {
            String operation = RDS_OPERATIONS.get(db.engine());
            if ("stopped".equals(db.status())) {
                set(db.dbInstanceId(), BigDecimal.ZERO);
            } else if (operation == null) {
                unpriced(db.dbInstanceId(), "가격 계산을 지원하지 않는 엔진입니다: " + db.engine());
            } else {
                boolean multiAz = Boolean.TRUE.equals(db.multiAz()) && !db.engine().startsWith("aurora");
                String deployment = multiAz ? "Multi-AZUsage:" : "InstanceUsage:";
                addLine(db.dbInstanceId(),
                    PricingCodes.rdsInstance(prefix, deployment, db.instanceClass(), operation, HOURS_PER_MONTH));
            }
        }

        void addS3(S3Bucket bucket) {
            if (bucket.sizeBytes() == null) {
                unpriced(bucket.name(), "버킷 크기를 알 수 없습니다.");
            } else {
                addLine(bucket.name(), "AmazonS3", "TimedStorage-ByteHrs", "", bucket.sizeBytes() / BYTES_PER_GB);
            }
        }

        void addLoadBalancer(LoadBalancer lb) {
            String operation = LB_OPERATIONS.get(lb.type());
            if (operation == null) {
                unpriced(lb.name(), "가격 계산을 지원하지 않는 타입입니다: " + lb.type());
            } else {
                addLine(lb.name(), AWS_ELB, "LoadBalancerUsage", operation, HOURS_PER_MONTH);
            }
        }

        void addNatGateway(NatGateway nat) {
            addLine(nat.natGatewayId(), AMAZON_EC2, "NatGateway-Hours", "NatGateway", HOURS_PER_MONTH);
        }

        List<UsageLine> lines() {
            return priced.stream().map(PricedResource::line).toList();
        }

        void fill(List<LinePrice> prices) {
            for (int i = 0; i < priced.size(); i++) {
                PricedResource resource = priced.get(i);
                LinePrice price = prices.get(i);
                monthly.get(resource.type()).put(resource.id(), price.monthlyUsd());
                if (price.failure() != null) {
                    missing.add(new Missing("COST " + resource.type() + " " + resource.id(), price.failure()));
                }
            }
        }
    }

    private static Map<String, String> formulas() {
        Map<String, String> formulas = new LinkedHashMap<>();
        formulas.put("EC2", "AWS Pricing Calculator 할인 전 온디맨드 요금. 인스턴스 타입·OS(Linux, Windows, RHEL, SUSE)별 730h. "
            + "Shared tenancy 기준이며 라이선스 포함 SQL Server 등 기타 OS 는 null, 중지된 인스턴스는 0");
        formulas.put("EBS", "AWS Pricing Calculator 볼륨 타입별 GB-월 요금 × 크기(GiB). 프로비저닝 IOPS·처리량·스냅샷 요금 제외");
        formulas.put("RDS", "AWS Pricing Calculator 인스턴스 클래스·엔진·Single/Multi-AZ별 730h. Aurora 는 인스턴스마다 따로 계산. "
            + "스토리지·백업·I/O 요금 제외, 중지된 인스턴스는 0");
        formulas.put("S3", "AWS Pricing Calculator Standard 스토리지 GB-월 요금 × 버킷 크기(StandardStorage). 요청·전송 요금 제외");
        formulas.put("LAMBDA", "요청 수·실행 시간에 따라 달라져 추정하지 않음(null)");
        formulas.put("ELB", "AWS Pricing Calculator 시간당 기본 요금 × 730h. LCU 요금 제외");
        formulas.put("NAT", "AWS Pricing Calculator 시간당 기본 요금 × 730h. 데이터 처리 요금 제외");
        return Collections.unmodifiableMap(formulas);
    }
}
