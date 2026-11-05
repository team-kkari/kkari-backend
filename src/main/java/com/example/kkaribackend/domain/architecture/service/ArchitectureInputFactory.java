package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.MetricStats;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 분석 결과·Terraform·요구사항으로 AI 서버 입력을 만든다.
 * infra 는 한 줄에 리소스 하나로 쓰고, 결과에 없는 값은 만들지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ArchitectureInputFactory {

    public static ArchitectureRequest create(InfraSnapshot snapshot, TerraformInfra terraform,
                                             Requirements requirements, int count) {
        return ArchitectureRequest.builder()
            .infra(infra(snapshot, terraform))
            .goal(goal(requirements))
            .priorities(requirements == null || requirements.priorities() == null ? List.of()
                : requirements.priorities().stream().map(p -> p.name().toLowerCase(Locale.ROOT)).toList())
            .count(count)
            .build();
    }

    private static String infra(InfraSnapshot snapshot, TerraformInfra terraform) {
        List<String> lines = new ArrayList<>();
        lines.add(snapshot.region() == null ? "[AWS]" : "[AWS] 리전 " + snapshot.region());
        resourceLines(snapshot.resources(), snapshot, lines::add);
        if (terraform != null) {
            lines.add("[Terraform]");
            resourceLines(terraform.resources(), null, lines::add);
        }
        return String.join("\n", lines);
    }

    // snapshot 이 있으면 CPU 사용률·예상 비용을 덧붙인다
    private static void resourceLines(Resources r, InfraSnapshot snapshot, Consumer<String> out) {
        if (r == null) {
            return;
        }
        each(r.ec2()).forEach(i -> out.accept(line(snapshot, "EC2", i.instanceId(),
            i.instanceType(), i.platform(), i.state(), i.availabilityZone())));
        each(r.ebs()).forEach(v -> out.accept(line(snapshot, "EBS", v.volumeId(),
            v.volumeType(), v.sizeGiB() == null ? null : v.sizeGiB() + "GiB")));
        each(r.rds()).forEach(d -> out.accept(line(snapshot, "RDS", d.dbInstanceId(),
            d.instanceClass(), d.engine(), d.multiAz() == null ? null : d.multiAz() ? "Multi-AZ" : "Single-AZ",
            d.allocatedStorageGiB() == null ? null : d.allocatedStorageGiB() + "GiB", d.status())));
        each(r.s3()).forEach(b -> out.accept(line(snapshot, "S3", b.name(),
            b.sizeBytes() == null ? null : String.format(Locale.ROOT, "%.2fGiB", b.sizeBytes() / (double) (1L << 30)))));
        each(r.lambda()).forEach(f -> out.accept(line(snapshot, "LAMBDA", f.functionName(),
            f.runtime(), f.memoryMb() == null ? null : f.memoryMb() + "MB")));
        each(r.loadBalancers()).forEach(lb -> out.accept(line(snapshot, "ELB", lb.name(), lb.type())));
        each(r.natGateways()).forEach(n -> out.accept(line(snapshot, "NAT", n.natGatewayId(), n.state())));
    }

    // 예: "EC2 i-1: t3.micro, Linux/UNIX / CPU 14일 평균 12.5%, p95 40.0% / 예상 월 7.59 USD"
    private static String line(InfraSnapshot snapshot, String kind, String id, String... specs) {
        StringBuilder line = new StringBuilder(join(" ", Stream.of(kind, id)));
        String spec = join(", ", Stream.of(specs));
        if (!spec.isEmpty()) {
            line.append(": ").append(spec);
        }
        if (snapshot != null) {
            Stream.of(cpu(snapshot, kind, id), cost(snapshot, kind, id))
                .filter(Objects::nonNull)
                .forEach(part -> line.append(" / ").append(part));
        }
        return line.toString();
    }

    private static String cpu(InfraSnapshot snapshot, String kind, String id) {
        if (snapshot.usage() == null || snapshot.usage().byResource() == null) {
            return null;
        }
        Map<String, Map<String, MetricStats>> byId = snapshot.usage().byResource().get(kind);
        Map<String, MetricStats> metrics = byId == null ? null : byId.get(id);
        MetricStats stats = metrics == null ? null : metrics.get("CPUUtilization");
        if (stats == null || (stats.average() == null && stats.p95() == null)) {
            return null;
        }
        return "CPU " + snapshot.usage().days() + "일 " + join(", ", Stream.of(
            stats.average() == null ? null : String.format(Locale.ROOT, "평균 %.1f%%", stats.average()),
            stats.p95() == null ? null : String.format(Locale.ROOT, "p95 %.1f%%", stats.p95())));
    }

    private static String cost(InfraSnapshot snapshot, String kind, String id) {
        if (snapshot.cost() == null || snapshot.cost().monthlyUsdByResource() == null) {
            return null;
        }
        Map<String, BigDecimal> byId = snapshot.cost().monthlyUsdByResource().get(kind);
        BigDecimal usd = byId == null ? null : byId.get(id);
        return usd == null ? null : "예상 월 " + usd.setScale(2, RoundingMode.HALF_UP).toPlainString() + " USD";
    }

    // 요구사항 본문 뒤에 원하는 구조·예산·변경 금지 리소스를 한 줄씩 붙인다
    private static String goal(Requirements requirements) {
        if (requirements == null) {
            return "";
        }
        BigDecimal budget = requirements.monthlyBudget();
        List<String> frozen = requirements.frozenResources();
        return join("\n", Stream.of(
            requirements.content(),
            blank(requirements.desiredArchitecture()) ? null : "원하는 구조: " + requirements.desiredArchitecture(),
            budget == null ? null : "월 예산: " + join(" ", Stream.of(budget.toPlainString(), requirements.currency())),
            frozen == null || frozen.isEmpty() ? null : "변경 금지 리소스: " + String.join(", ", frozen)));
    }

    private static String join(String delimiter, Stream<String> parts) {
        return parts.filter(part -> !blank(part)).collect(Collectors.joining(delimiter));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> each(List<T> list) {
        return list == null ? List.of() : list;
    }
}
