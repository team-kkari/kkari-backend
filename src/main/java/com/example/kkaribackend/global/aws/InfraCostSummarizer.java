package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InfraCostSummarizer {
    // 부분 합을 전체 비용으로 표시하지 않는다. 0은 모든 알려진 비용이 실제로 0일 때만 반환한다.
    public static BigDecimal monthlyUsd(InfraSnapshot snapshot) {
        if (snapshot == null || snapshot.cost() == null || !"USD".equals(snapshot.cost().currency())) {
            return null;
        }
        var costs = snapshot.cost().monthlyUsdByResource();
        if (costs == null || costs.isEmpty() || hasMissingCostOrStructure(snapshot)) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var group : costs.values()) {
            if (group == null) {
                return null;
            }
            for (BigDecimal value : group.values()) {
                if (value == null || value.signum() < 0) {
                    return null;
                }
                total = total.add(value);
            }
        }
        var resources = snapshot.resources();
        if (resources != null && (
            unpriced(resources.ec2(), costs.get("EC2"), InfraSnapshot.Ec2Instance::instanceId)
            || unpriced(resources.ebs(), costs.get("EBS"), InfraSnapshot.EbsVolume::volumeId)
            || unpriced(resources.rds(), costs.get("RDS"), InfraSnapshot.RdsInstance::dbInstanceId)
            || unpriced(resources.s3(), costs.get("S3"), InfraSnapshot.S3Bucket::name)
            || unpriced(resources.lambda(), costs.get("LAMBDA"), InfraSnapshot.LambdaFunction::functionName)
            || unpriced(resources.loadBalancers(), costs.get("ELB"), InfraSnapshot.LoadBalancer::name)
            || unpriced(resources.natGateways(), costs.get("NAT"), InfraSnapshot.NatGateway::natGatewayId))) {
            return null;
        }
        return total;
    }

    private static boolean hasMissingCostOrStructure(InfraSnapshot snapshot) {
        return snapshot.missing() != null && snapshot.missing().stream().anyMatch(missing ->
            missing.target() != null && (missing.target().equals("COST") || missing.target().startsWith("COST ")
                || missing.target().equals("STRUCTURE") || missing.target().startsWith("STRUCTURE ")));
    }

    private static <T> boolean unpriced(List<T> resources, Map<String, BigDecimal> prices, Function<T, String> id) {
        return resources != null && resources.stream().anyMatch(resource ->
            prices == null || !prices.containsKey(id.apply(resource)));
    }
}
