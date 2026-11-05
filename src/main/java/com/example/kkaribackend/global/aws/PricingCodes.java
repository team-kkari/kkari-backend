package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;

import java.util.List;
import java.util.Map;

/**
 * 인프라 견적과 설계안 견적이 함께 쓰는 AWS Pricing Calculator 요금 코드.
 */
public final class PricingCodes {
    public static final String AMAZON_EC2 = "AmazonEC2";
    public static final String AMAZON_RDS = "AmazonRDS";
    public static final String AWS_ELB = "AWSELB";
    // usageType 앞에 붙는 리전 코드
    static final Map<String, String> USAGE_TYPE_PREFIXES = Map.of(
        "ap-northeast-2", "APN2-",
        "ap-northeast-1", "APN1-");
    public static final String SEOUL = USAGE_TYPE_PREFIXES.get("ap-northeast-2");
    public static final Map<String, EbsUsage> EBS_USAGES = Map.of(
        "gp2", new EbsUsage("EBS:VolumeUsage.gp2", "CreateVolume-Gp2"),
        "gp3", new EbsUsage("EBS:VolumeUsage.gp3", "CreateVolume-Gp3"),
        "io1", new EbsUsage("EBS:VolumeUsage.piops", "CreateVolume-P-IOPS"),
        "io2", new EbsUsage("EBS:VolumeUsage.io2", "CreateVolume-io2"),
        "st1", new EbsUsage("EBS:VolumeUsage.st1", "CreateVolume-Throughput"),
        "sc1", new EbsUsage("EBS:VolumeUsage.sc1", "CreateVolume-SC1"),
        "standard", new EbsUsage("EBS:VolumeUsage", "CreateVolume"));
    // 키는 RDS API 의 엔진 이름이다
    public static final Map<String, String> RDS_OPERATIONS = Map.of(
        "mysql", "CreateDBInstance:0002",
        "postgres", "CreateDBInstance:0014",
        "mariadb", "CreateDBInstance:0018",
        "aurora-mysql", "CreateDBInstance:0016",
        "aurora-postgresql", "CreateDBInstance:0021");
    public static final Map<String, String> LB_OPERATIONS = Map.of(
        "application", "LoadBalancing:Application",
        "network", "LoadBalancing:Network",
        "gateway", "LoadBalancing:Gateway");

    private PricingCodes() {
    }

    public record EbsUsage(String usageType, String operation) {
    }

    // RDS 인스턴스 usageType 은 대부분 xlarge 를 xl 로 줄여 쓰지만 db.t4g 처럼 줄이지 않는 패밀리도 있어 둘 다 보낸다
    public static UsageLine rdsInstance(String prefix, String deployment, String instanceClass, String operation,
                                        double hours) {
        String usageType = prefix + deployment + instanceClass;
        List<String> candidates = usageType.endsWith("xlarge")
            ? List.of(usageType, usageType.replaceFirst("xlarge$", "xl"))
            : List.of(usageType);
        return new UsageLine(AMAZON_RDS, candidates, operation, hours);
    }
}
