package com.example.kkaribackend.global.aws.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 고객사 인프라 수집 결과.
 * 수집에 실패했거나 알 수 없는 값은 0 이 아니라 null 이고, 이유는 missing 에 남긴다.
 */
@Builder
public record InfraSnapshot(
    String source, // AWS(연결해서 수집) 또는 TERRAFORM(state 파일 업로드)
    String region,
    Instant collectedAt,
    Resources resources,
    Usage usage,
    Cost cost,
    List<Missing> missing
) {

    @Builder
    public record Resources(
        List<Ec2Instance> ec2,
        List<EbsVolume> ebs,
        List<RdsInstance> rds,
        List<S3Bucket> s3,
        List<LambdaFunction> lambda,
        List<LoadBalancer> loadBalancers,
        List<NatGateway> natGateways
    ) {
    }

    // platform: platformDetails (예: Linux/UNIX, Windows)
    public record Ec2Instance(String instanceId, String instanceType, String platform, String state,
                              String availabilityZone) {
    }

    public record EbsVolume(String volumeId, String volumeType, Integer sizeGiB) {
    }

    public record RdsInstance(String dbInstanceId, String instanceClass, String engine, Boolean multiAz,
                              Integer allocatedStorageGiB, String status) {
    }

    // sizeBytes: CloudWatch BucketSizeBytes(StandardStorage) 최근값
    public record S3Bucket(String name, Long sizeBytes) {
    }

    public record LambdaFunction(String functionName, String runtime, Integer memoryMb) {
    }

    public record LoadBalancer(String name, String type) {
    }

    public record NatGateway(String natGatewayId, String state) {
    }

    // byResource: 종류(EC2, RDS) -> 리소스 ID -> 지표 이름 -> 통계. 이름은 종류가 다르면 겹칠 수 있다
    public record Usage(int days, int periodSeconds, String statistic,
                        Map<String, Map<String, Map<String, MetricStats>>> byResource) {
    }

    // 1시간 평균값들의 평균, p95, 최대
    public record MetricStats(Double average, Double p95, Double max, int datapoints) {
    }

    // monthlyUsdByResource: 종류(EC2, EBS, RDS, S3, LAMBDA, ELB, NAT) -> 리소스 ID -> 월 예상 비용(USD)
    public record Cost(String currency, String basis, int hoursPerMonth, Map<String, String> formulas,
                       Map<String, Map<String, BigDecimal>> monthlyUsdByResource) {
    }

    // target: "<영역> <종류> [<리소스 ID>]", 영역은 STRUCTURE, USAGE, COST
    public record Missing(String target, String reason) {
    }
}
