package com.example.kkaribackend.domain.analysis.facade;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJobType;
import com.example.kkaribackend.domain.analysis.domain.AnalysisStatus;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.project.domain.CustomerAnswersFormatter;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.kkaribackend.global.util.TextTruncator.truncate;

@Component
@RequiredArgsConstructor
public class AnalysisFacade {
    private static final int MAX_LENGTH = 4000;
    private static final List<String> KINDS = List.of("EC2", "EBS", "RDS", "S3", "LAMBDA", "ELB", "NAT");

    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String getInfraContext(Project project) {
        StringBuilder text = new StringBuilder();
        analysisJobRepository.findFirstByProjectIdAndTypeAndStatusOrderByIdDesc(project.getId(),
                AnalysisJobType.ANALYSIS, AnalysisStatus.SUCCEEDED)
            .ifPresent(job -> appendSnapshot(text, objectMapper.readValue(job.getResult(), InfraSnapshot.class)));
        if (project.getTerraform() != null) {
            var terraform = project.getTerraform();
            text.append("[Terraform] 업로드 시각: ").append(orUnknown(terraform.uploadedAt()))
                .append("; 사용률·비용 없음\n");
            appendResources(text, terraform.resources());
            text.append("누락 항목: ").append(size(terraform.missing())).append("개\n");
        }
        String infra = truncate(text, MAX_LENGTH);
        String answers = CustomerAnswersFormatter.describe(project.getAnswers());
        if (answers.isEmpty()) {
            return infra;
        }
        return infra.isEmpty() ? answers : infra + "\n" + answers;
    }

    private void appendSnapshot(StringBuilder text, InfraSnapshot snapshot) {
        text.append("[AWS 분석] 리전: ").append(orUnknown(snapshot.region()))
            .append("; 수집 시각: ").append(orUnknown(snapshot.collectedAt())).append('\n');
        appendResources(text, snapshot.resources());
        text.append("월 예상 비용(USD):\n");
        var costs = snapshot.cost() == null ? null : snapshot.cost().monthlyUsdByResource();
        for (String kind : KINDS) {
            Map<String, BigDecimal> values = costs == null ? null : costs.get(kind);
            String total = values == null || values.values().stream().anyMatch(v -> v == null)
                ? "추정 불가" : values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .stripTrailingZeros().toPlainString();
            text.append(kind).append(": ").append(total).append('\n');
        }
        text.append("누락 항목: ").append(size(snapshot.missing())).append("개\n");
        if (snapshot.usage() != null && snapshot.usage().byResource() != null) {
            text.append("사용률(").append(snapshot.usage().days()).append("일):\n");
            for (String kind : List.of("EC2", "RDS")) {
                var resources = snapshot.usage().byResource().get(kind);
                if (resources == null) {
                    continue;
                }
                new TreeMap<>(resources).forEach((id, metrics) -> {
                    if (metrics != null) {
                        new TreeMap<>(metrics).forEach((name, stats) -> text.append(kind).append(' ')
                            .append(id).append(' ').append(name).append(": ")
                            .append(stats == null ? "알 수 없음" : "평균=" + orUnknown(stats.average())
                                + ", p95=" + orUnknown(stats.p95()))
                            .append('\n'));
                    }
                });
            }
        }
    }

    private void appendResources(StringBuilder text, Resources resources) {
        if (resources == null) {
            text.append("리소스 구조: 알 수 없음\n");
            return;
        }
        appendGroup(text, "EC2", resources.ec2(), r -> specs(r.instanceType(), r.state(), r.platform(),
            r.availabilityZone()));
        appendGroup(text, "EBS", resources.ebs(), r -> specs(r.volumeType(), withUnit(r.sizeGiB(), "GiB")));
        appendGroup(text, "RDS", resources.rds(), r -> specs(r.instanceClass(), r.engine(),
            r.multiAz() == null ? null : "Multi-AZ=" + r.multiAz(), withUnit(r.allocatedStorageGiB(), "GiB"),
            r.status()));
        appendGroup(text, "S3", resources.s3(), r -> specs(r.sizeBytes() == null ? null : "크기=" + r.sizeBytes() + " bytes"));
        appendGroup(text, "LAMBDA", resources.lambda(), r -> specs(r.runtime(), withUnit(r.memoryMb(), "MB")));
        appendGroup(text, "ELB", resources.loadBalancers(), r -> specs(r.type()));
        appendGroup(text, "NAT", resources.natGateways(), r -> specs(r.state()));
    }

    // 모르는 값은 "null" 문자열로 AI 에 넘기지 않고 뺀다
    private String specs(String... values) {
        String joined = Arrays.stream(values).filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(", "));
        return joined.isEmpty() ? "스펙 알 수 없음" : joined;
    }

    private String withUnit(Number value, String unit) {
        return value == null ? null : value + " " + unit;
    }

    private <T> void appendGroup(StringBuilder text, String kind, List<T> resources, Function<T, String> spec) {
        text.append(kind).append(": ");
        if (resources == null) {
            text.append("개수 알 수 없음\n");
            return;
        }
        text.append(resources.size()).append("개");
        // 동일 스펙은 묶고 정렬해 수집 순서에 따라 해시가 바뀌지 않게 한다
        var groups = resources.stream().collect(Collectors.groupingBy(
            r -> String.valueOf(spec.apply(r)), TreeMap::new, Collectors.counting()));
        groups.forEach((description, count) -> text.append("; ").append(description).append(" × ").append(count));
        text.append('\n');
    }

    private String orUnknown(Object value) {
        return value == null ? "알 수 없음" : value.toString();
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
