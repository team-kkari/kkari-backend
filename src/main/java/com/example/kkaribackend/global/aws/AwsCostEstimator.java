package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.FilterType;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Price List API 의 온디맨드 정가로 월 예상 비용을 계산한다.
 * 가격 조회는 고객사 Role 도 서버 기본 자격증명도 아닌 별도 계정 키로 한다.
 */
@Slf4j
@Component
public class AwsCostEstimator {
    private static final int HOURS_PER_MONTH = 730;
    private static final String LINUX = "Linux/UNIX";
    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1L << 30);
    private static final Map<String, String> FORMULAS = formulas();
    private static final Map<String, String> RDS_ENGINES = Map.of(
        "mysql", "MySQL",
        "postgres", "PostgreSQL",
        "mariadb", "MariaDB",
        "aurora-mysql", "Aurora MySQL",
        "aurora-postgresql", "Aurora PostgreSQL");
    private static final Map<String, String> LB_FAMILIES = Map.of(
        "application", "Load Balancer-Application",
        "network", "Load Balancer-Network",
        "gateway", "Load Balancer-Gateway");

    private final String region;
    private final PricingClient pricingClient; // 키가 설정되지 않으면 null
    private final ObjectMapper objectMapper;

    @Autowired
    public AwsCostEstimator(@Value("${aws.region}") String region,
                            @Value("${aws.pricing.access-key}") String accessKey,
                            @Value("${aws.pricing.secret-key}") String secretKey,
                            ObjectMapper objectMapper) {
        this(region, accessKey.isBlank() || secretKey.isBlank() ? null : PricingClient.builder()
            .region(Region.US_EAST_1) // Price List API 엔드포인트
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .build(), objectMapper);
    }

    AwsCostEstimator(String region, PricingClient pricingClient, ObjectMapper objectMapper) {
        this.region = region;
        this.pricingClient = pricingClient;
        this.objectMapper = objectMapper;
    }

    public Cost estimate(Resources resources, List<Missing> missing) {
        if (pricingClient == null) {
            missing.add(new Missing("COST", "가격 조회용 AWS 자격증명이 설정되지 않았습니다."));
            return cost(null);
        }

        PriceLookup prices = new PriceLookup(missing);
        Map<String, Map<String, BigDecimal>> monthly = new LinkedHashMap<>();

        if (resources.ec2() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "EC2");
            resources.ec2().forEach(i -> costs.put(i.instanceId(),
                "stopped".equals(i.state()) ? BigDecimal.ZERO
                    : !LINUX.equals(i.platform()) ? unpriced(missing, "EC2", i.instanceId(),
                        "Linux/UNIX 가 아닌 플랫폼은 추정하지 않습니다: " + i.platform())
                    : perMonth(prices.find("EC2", i.instanceId(), "AmazonEC2", "Hrs", Map.of(
                        "instanceType", i.instanceType(),
                        "operatingSystem", "Linux",
                        "tenancy", "Shared",
                        "preInstalledSw", "NA",
                        "capacitystatus", "Used")))));
        }
        if (resources.ebs() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "EBS");
            resources.ebs().forEach(v -> costs.put(v.volumeId(), multiply(
                prices.find("EBS", v.volumeId(), "AmazonEC2", "GB-Mo", Map.of(
                    "productFamily", "Storage",
                    "volumeApiName", v.volumeType())),
                v.sizeGiB() == null ? null : BigDecimal.valueOf(v.sizeGiB()))));
        }
        if (resources.rds() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "RDS");
            resources.rds().forEach(db -> {
                String engine = RDS_ENGINES.get(db.engine());
                costs.put(db.dbInstanceId(), "stopped".equals(db.status()) ? BigDecimal.ZERO
                    : engine == null ? unpriced(missing, "RDS", db.dbInstanceId(),
                        "가격 조회를 지원하지 않는 엔진입니다: " + db.engine())
                    : perMonth(prices.find("RDS", db.dbInstanceId(), "AmazonRDS", "Hrs", Map.of(
                        "instanceType", db.instanceClass(),
                        "databaseEngine", engine,
                        "deploymentOption", Boolean.TRUE.equals(db.multiAz()) ? "Multi-AZ" : "Single-AZ"))));
            });
        }
        if (resources.s3() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "S3");
            resources.s3().forEach(b -> costs.put(b.name(), b.sizeBytes() == null
                ? unpriced(missing, "S3", b.name(), "버킷 크기를 알 수 없습니다.")
                : multiply(prices.find("S3", b.name(), "AmazonS3", "GB-Mo", Map.of(
                    "productFamily", "Storage",
                    "volumeType", "Standard")),
                    BigDecimal.valueOf(b.sizeBytes()).divide(BYTES_PER_GB, 6, RoundingMode.HALF_UP))));
        }
        if (resources.lambda() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "LAMBDA");
            resources.lambda().forEach(f -> costs.put(f.functionName(),
                unpriced(missing, "LAMBDA", f.functionName(), "요청 수·실행 시간에 따라 달라져 추정하지 않습니다.")));
        }
        if (resources.loadBalancers() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "ELB");
            resources.loadBalancers().forEach(lb -> {
                String family = LB_FAMILIES.get(lb.type());
                costs.put(lb.name(), family == null
                    ? unpriced(missing, "ELB", lb.name(), "가격 조회를 지원하지 않는 타입입니다: " + lb.type())
                    : perMonth(prices.find("ELB", lb.name(), "AWSELB", "Hrs", Map.of("productFamily", family))));
            });
        }
        if (resources.natGateways() != null) {
            Map<String, BigDecimal> costs = typeMap(monthly, "NAT");
            resources.natGateways().forEach(nat -> costs.put(nat.natGatewayId(), perMonth(
                prices.find("NAT", nat.natGatewayId(), "AmazonEC2", "Hrs", Map.of("productFamily", "NAT Gateway")))));
        }
        return cost(monthly);
    }

    private static Map<String, BigDecimal> typeMap(Map<String, Map<String, BigDecimal>> monthly, String type) {
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        monthly.put(type, costs);
        return costs;
    }

    private static BigDecimal unpriced(List<Missing> missing, String type, String id, String reason) {
        missing.add(new Missing("COST " + type + " " + id, reason));
        return null;
    }

    private static Cost cost(Map<String, Map<String, BigDecimal>> monthly) {
        return new Cost("USD", "ON_DEMAND_LIST_PRICE", HOURS_PER_MONTH, FORMULAS, monthly);
    }

    private static BigDecimal perMonth(BigDecimal hourly) {
        return multiply(hourly, BigDecimal.valueOf(HOURS_PER_MONTH));
    }

    private static BigDecimal multiply(BigDecimal price, BigDecimal quantity) {
        if (price == null || quantity == null) {
            return null;
        }
        return price.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    // 한 번의 분석 안에서 같은 조건의 단가는 한 번만 조회한다
    private class PriceLookup {
        private final List<Missing> missing;
        private final Map<String, Result> cache = new HashMap<>();

        PriceLookup(List<Missing> missing) {
            this.missing = missing;
        }

        BigDecimal find(String type, String id, String serviceCode, String unit, Map<String, String> filters) {
            String key = serviceCode + unit + new TreeMap<>(filters);
            Result result = cache.computeIfAbsent(key, k -> request(serviceCode, unit, filters));
            return result.price() != null ? result.price() : unpriced(missing, type, id, result.failure());
        }

        private Result request(String serviceCode, String unit, Map<String, String> filters) {
            List<Filter> requestFilters = new ArrayList<>(List.of(termMatch("regionCode", region)));
            filters.forEach((field, value) -> requestFilters.add(termMatch(field, value)));
            try {
                for (GetProductsResponse page : pricingClient.getProductsPaginator(GetProductsRequest.builder()
                    .serviceCode(serviceCode)
                    .filters(requestFilters)
                    .build())) {
                    for (String product : page.priceList()) {
                        BigDecimal price = onDemandPrice(objectMapper.readTree(product), unit);
                        if (price != null) {
                            return new Result(price, null);
                        }
                    }
                }
                return new Result(null, "일치하는 가격 정보가 없습니다.");
            } catch (RuntimeException e) {
                log.warn("가격 조회 실패: {} {} {}", serviceCode, filters, AwsFailures.reason(e));
                return new Result(null, "가격 조회 실패: " + AwsFailures.reason(e));
            }
        }
    }

    // terms.OnDemand.*.priceDimensions.* 중 단위가 맞는 첫 구간(beginRange 0)의 USD 단가
    private static BigDecimal onDemandPrice(JsonNode product, String unit) {
        for (JsonNode term : product.path("terms").path("OnDemand").values()) {
            for (JsonNode dimension : term.path("priceDimensions").values()) {
                if (unit.equals(dimension.path("unit").asString(""))
                    && "0".equals(dimension.path("beginRange").asString("0"))) {
                    String usd = dimension.path("pricePerUnit").path("USD").asString("");
                    if (!usd.isEmpty()) {
                        return new BigDecimal(usd);
                    }
                }
            }
        }
        return null;
    }

    private static Filter termMatch(String field, String value) {
        return Filter.builder().type(FilterType.TERM_MATCH).field(field).value(value).build();
    }

    private record Result(BigDecimal price, String failure) {
    }

    private static Map<String, String> formulas() {
        Map<String, String> formulas = new LinkedHashMap<>();
        formulas.put("EC2", "시간당 온디맨드 단가(Linux, Shared, 사전 설치 SW 없음) × 730h. 중지된 인스턴스는 0, Linux 외 플랫폼은 null");
        formulas.put("EBS", "볼륨 타입별 GB-월 단가 × 크기(GiB). IOPS·처리량 추가 요금 제외");
        formulas.put("RDS", "인스턴스 클래스·엔진·Single/Multi-AZ별 시간당 단가 × 730h. 스토리지·백업·I/O 제외, 중지된 인스턴스는 0");
        formulas.put("S3", "Standard 첫 구간 GB-월 단가 × 버킷 크기(StandardStorage). 요청·전송 요금 제외");
        formulas.put("LAMBDA", "요청 수·실행 시간에 따라 달라져 추정하지 않음(null)");
        formulas.put("ELB", "시간당 기본 요금 × 730h. LCU 요금 제외");
        formulas.put("NAT", "시간당 기본 요금 × 730h. 데이터 처리 요금 제외");
        return Collections.unmodifiableMap(formulas);
    }
}
