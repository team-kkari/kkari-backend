package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.project.domain.CustomerAnswers;
import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.MetricStats;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Usage;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 분석 결과·Terraform·요구사항·고객 답변으로 AI 서버 입력을 만든다.
 * infra 는 한 줄에 리소스 하나로 쓰고, 결과에 없는 값은 만들지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ArchitectureInputFactory {

    private static final String CPU_METRIC = "CPUUtilization";
    private static final double BYTES_PER_GIB = 1L << 30;
    private static final String PERSONAL_DATA_NOTICE = "개인정보 포함";

    // 사용률·비용은 종류(kind)와 ID 로 찾는다
    private record Resource(String kind, String id, String specs) {
    }

    public static ArchitectureRequest create(InfraSnapshot snapshot, TerraformInfra terraform,
                                             Requirements requirements, Map<String, String> answers, int count) {
        return ArchitectureRequest.builder()
            .infra(describeInfra(snapshot, terraform))
            .goal(joinNonBlank("\n", describeGoal(requirements), CustomerAnswers.describe(answers)))
            .priorities(priorityNames(requirements))
            .count(count)
            .build();
    }

    private static String describeInfra(InfraSnapshot snapshot, TerraformInfra terraform) {
        List<String> lines = new ArrayList<>();
        lines.add(snapshot.region() == null ? "[AWS]" : "[AWS] 리전 " + snapshot.region());
        for (Resource resource : listResources(snapshot.resources())) {
            lines.add(describeWithUsageAndCost(resource, snapshot));
        }
        if (terraform != null) {
            lines.add("[Terraform]");
            for (Resource resource : listResources(terraform.resources())) {
                lines.add(describe(resource));
            }
        }
        return String.join("\n", lines);
    }

    private static List<Resource> listResources(Resources resources) {
        List<Resource> list = new ArrayList<>();
        if (resources == null) {
            return list;
        }
        orEmpty(resources.ec2()).forEach(instance -> list.add(resource("EC2", instance.instanceId(),
            instance.instanceType(), instance.platform(), instance.state(), instance.availabilityZone())));
        orEmpty(resources.ebs()).forEach(volume -> list.add(resource("EBS", volume.volumeId(),
            volume.volumeType(), withUnit(volume.sizeGiB(), "GiB"))));
        orEmpty(resources.rds()).forEach(db -> list.add(resource("RDS", db.dbInstanceId(),
            db.instanceClass(), db.engine(), availabilityMode(db.multiAz()),
            withUnit(db.allocatedStorageGiB(), "GiB"), db.status())));
        orEmpty(resources.s3()).forEach(bucket -> list.add(resource("S3", bucket.name(),
            gibFromBytes(bucket.sizeBytes()))));
        orEmpty(resources.lambda()).forEach(function -> list.add(resource("LAMBDA", function.functionName(),
            function.runtime(), withUnit(function.memoryMb(), "MB"))));
        orEmpty(resources.loadBalancers()).forEach(loadBalancer -> list.add(resource("ELB", loadBalancer.name(),
            loadBalancer.type())));
        orEmpty(resources.natGateways()).forEach(natGateway -> list.add(resource("NAT", natGateway.natGatewayId(),
            natGateway.state())));
        return list;
    }

    private static Resource resource(String kind, String id, String... specs) {
        return new Resource(kind, id, joinNonBlank(", ", specs));
    }

    // 예: "EC2 i-1: t3.micro, Linux/UNIX"
    private static String describe(Resource resource) {
        String name = joinNonBlank(" ", resource.kind(), resource.id());
        return resource.specs().isEmpty() ? name : name + ": " + resource.specs();
    }

    // 예: "EC2 i-1: t3.micro, Linux/UNIX / CPU 14일 평균 12.5%, p95 40.0% / 예상 월 7.59 USD"
    private static String describeWithUsageAndCost(Resource resource, InfraSnapshot snapshot) {
        return joinNonBlank(" / ", describe(resource), cpuUsage(snapshot.usage(), resource),
            monthlyCost(snapshot.cost(), resource));
    }

    private static String cpuUsage(Usage usage, Resource resource) {
        MetricStats cpu = findCpuStats(usage, resource);
        if (cpu == null) {
            return null;
        }
        String stats = joinNonBlank(", ",
            cpu.average() == null ? null : format("평균 %.1f%%", cpu.average()),
            cpu.p95() == null ? null : format("p95 %.1f%%", cpu.p95()));
        return stats.isEmpty() ? null : "CPU " + usage.days() + "일 " + stats;
    }

    private static MetricStats findCpuStats(Usage usage, Resource resource) {
        if (usage == null || usage.byResource() == null) {
            return null;
        }
        Map<String, Map<String, MetricStats>> metricsById = usage.byResource().get(resource.kind());
        Map<String, MetricStats> metrics = metricsById == null ? null : metricsById.get(resource.id());
        return metrics == null ? null : metrics.get(CPU_METRIC);
    }

    private static String monthlyCost(Cost cost, Resource resource) {
        if (cost == null || cost.monthlyUsdByResource() == null) {
            return null;
        }
        Map<String, BigDecimal> usdById = cost.monthlyUsdByResource().get(resource.kind());
        BigDecimal usd = usdById == null ? null : usdById.get(resource.id());
        return usd == null ? null : "예상 월 " + usd.setScale(2, RoundingMode.HALF_UP).toPlainString() + " USD";
    }

    private static String describeGoal(Requirements requirements) {
        if (requirements == null) {
            return "";
        }
        return joinNonBlank("\n",
            requirements.content(),
            isBlank(requirements.desiredArchitecture()) ? null : "원하는 구조: " + requirements.desiredArchitecture(),
            monthlyBudget(requirements),
            requirements.containsPersonalData() ? PERSONAL_DATA_NOTICE : null,
            frozenResources(requirements.frozenResources()));
    }

    private static String monthlyBudget(Requirements requirements) {
        BigDecimal budget = requirements.monthlyBudget();
        return budget == null ? null : "월 예산: " + joinNonBlank(" ", budget.toPlainString(), requirements.currency());
    }

    private static String frozenResources(List<String> frozenResources) {
        if (frozenResources == null || frozenResources.isEmpty()) {
            return null;
        }
        return "변경 금지 리소스: " + String.join(", ", frozenResources);
    }

    private static List<String> priorityNames(Requirements requirements) {
        if (requirements == null || requirements.priorities() == null) {
            return List.of();
        }
        return requirements.priorities().stream()
            .map(priority -> priority.name().toLowerCase(Locale.ROOT))
            .toList();
    }

    private static String withUnit(Integer value, String unit) {
        return value == null ? null : value + unit;
    }

    private static String availabilityMode(Boolean multiAz) {
        if (multiAz == null) {
            return null;
        }
        return multiAz ? "Multi-AZ" : "Single-AZ";
    }

    private static String gibFromBytes(Long bytes) {
        return bytes == null ? null : format("%.2fGiB", bytes / BYTES_PER_GIB);
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    private static String joinNonBlank(String delimiter, String... parts) {
        return Arrays.stream(parts)
            .filter(part -> !isBlank(part))
            .collect(Collectors.joining(delimiter));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
