package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.pricing.domain.UnitPrice;
import com.example.kkaribackend.domain.pricing.domain.UnitPrice.Tier;
import com.example.kkaribackend.domain.pricing.domain.repository.UnitPriceRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.FilterType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용량 줄마다 월 비용(USD)을 계산한다. 단가는 AWS Price List 에서 처음 쓸 때 받아 DB 에 두고,
 * 비용은 그 단가로 직접 계산한다. 가격 조회는 고객사 Role 도 서버 기본 자격증명도 아닌 별도 계정 키로 한다.
 */
@Slf4j
@Component
public class AwsPricingCalculator {
    static final Duration PRICE_TTL = Duration.ofDays(30);
    // 새로 나온 타입은 곧 Price List 에 올라오므로 없다는 결과는 짧게만 믿는다
    static final Duration MISSING_PRICE_TTL = Duration.ofDays(1);
    private static final String NO_PRICE = "AWS Price List 에 온디맨드 단가가 없습니다.";
    // 느린 응답이 요청 스레드와 분석 스레드를 오래 붙잡지 않게 한다
    private static final ClientOverrideConfiguration TIMEOUTS = ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(10))
        .apiCallAttemptTimeout(Duration.ofSeconds(5))
        .build();

    private final PricingClient client;
    private final UnitPriceRepository repository;

    @Autowired
    AwsPricingCalculator(@Value("${aws.pricing.access-key}") String accessKey,
                         @Value("${aws.pricing.secret-key}") String secretKey,
                         UnitPriceRepository repository) {
        this(newClient(accessKey, secretKey), repository);
    }

    AwsPricingCalculator(PricingClient client, UnitPriceRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    // Price List API 는 us-east-1 에서 모든 리전 가격을 준다
    private static PricingClient newClient(String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return null;
        }
        return PricingClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .overrideConfiguration(TIMEOUTS)
            .build();
    }

    public boolean isConfigured() {
        return client != null;
    }

    @PreDestroy
    void close() {
        if (isConfigured()) {
            client.close();
        }
    }

    // usageTypes: 같은 요금의 이름 후보. 가격이 나온 첫 후보를 쓴다
    public record UsageLine(String serviceCode, List<String> usageTypes, String operation, double amount) {
        public UsageLine(String serviceCode, String usageType, String operation, double amount) {
            this(serviceCode, List.of(usageType), operation, amount);
        }

        public String primaryUsageType() {
            return usageTypes.getFirst();
        }
    }

    public enum Status { PRICED, UNPRICED, CALL_FAILED }

    // CALL_FAILED 는 줄이 아니라 단가 조회가 실패한 것이라 다시 시도하면 값이 나올 수 있다
    public record LinePrice(Status status, BigDecimal monthlyUsd, String failure) {
        public static LinePrice priced(BigDecimal monthlyUsd) {
            return new LinePrice(Status.PRICED, monthlyUsd, null);
        }

        public static LinePrice unpriced(String failure) {
            return new LinePrice(Status.UNPRICED, null, failure);
        }

        public static LinePrice callFailed(String failure) {
            return new LinePrice(Status.CALL_FAILED, null, failure);
        }
    }

    private record PriceKey(String serviceCode, String usageType, String operation) {
    }

    // unitPrice 가 null 이면 조회에 실패한 것이고 failure 에 이유가 있다
    private record Lookup(UnitPrice unitPrice, String failure) {
        LinePrice price(double amount) {
            if (unitPrice == null) {
                return LinePrice.callFailed(failure);
            }
            return unitPrice.hasPrice() ? LinePrice.priced(unitPrice.cost(amount)) : LinePrice.unpriced(NO_PRICE);
        }
    }

    // lines 와 같은 순서로 결과를 돌려준다. 실패해도 예외를 던지지 않고 모든 줄에 이유를 남긴다
    public List<LinePrice> price(List<UsageLine> lines) {
        Map<PriceKey, Lookup> lookups = new HashMap<>();
        return lines.stream().map(line -> price(line, lookups)).toList();
    }

    private LinePrice price(UsageLine line, Map<PriceKey, Lookup> lookups) {
        List<LinePrice> failures = new ArrayList<>();
        for (String usageType : line.usageTypes()) {
            PriceKey key = new PriceKey(line.serviceCode(), usageType, line.operation());
            LinePrice price = lookups.computeIfAbsent(key, this::lookup).price(line.amount());
            if (price.status() == Status.PRICED) {
                return price;
            }
            failures.add(price);
        }
        return failures.stream().filter(price -> price.status() == Status.CALL_FAILED).findFirst()
            .orElse(failures.getFirst());
    }

    private Lookup lookup(PriceKey key) {
        Optional<UnitPrice> cached;
        try {
            cached = repository.findByServiceCodeAndUsageTypeAndOperation(
                key.serviceCode(), key.usageType(), key.operation());
        } catch (DataAccessException e) {
            log.warn("저장된 단가 조회 실패: {}", key, e);
            return new Lookup(null, "단가 조회 실패: " + AwsFailures.reason(e));
        }
        if (cached.isPresent() && !isStale(cached.get())) {
            return new Lookup(cached.get(), null);
        }

        UnitPrice fetched;
        try {
            fetched = fetch(key);
        } catch (RuntimeException e) {
            String failure = "단가 조회 실패: " + AwsFailures.reason(e);
            // 단가는 자주 바뀌지 않아, 갱신하지 못했으면 실패로 끝내기보다 이전 단가를 쓴다
            if (cached.isPresent()) {
                log.warn("{} — 이전 단가를 씁니다: {}", failure, key);
                return new Lookup(cached.get(), null);
            }
            log.warn("{}: {}", failure, key);
            return new Lookup(null, failure);
        }
        return new Lookup(save(cached, fetched), null);
    }

    private static boolean isStale(UnitPrice price) {
        Duration ttl = price.hasPrice() ? PRICE_TTL : MISSING_PRICE_TTL;
        return price.getFetchedAt().plus(ttl).isBefore(Instant.now());
    }

    // 다른 요청이 같은 단가를 동시에 처음 저장하면 유니크 제약으로 하나만 남는다. 받아 온 단가는 같으므로 그대로 쓴다
    private UnitPrice save(Optional<UnitPrice> cached, UnitPrice fetched) {
        try {
            if (cached.isPresent()) {
                cached.get().refresh(fetched);
                return repository.saveAndFlush(cached.get());
            }
            return repository.saveAndFlush(fetched);
        } catch (DataAccessException e) {
            log.info("단가를 저장하지 못해 받아 온 값만 씁니다: {} {}", fetched.getUsageType(), e.getClass().getSimpleName());
            return fetched;
        }
    }

    private UnitPrice fetch(PriceKey key) {
        List<Filter> filters = new ArrayList<>();
        filters.add(termMatch("usagetype", key.usageType()));
        // 빈 값으로는 걸러지지 않아 operation 이 없는 요금은 usagetype 으로만 찾는다
        if (!key.operation().isBlank()) {
            filters.add(termMatch("operation", key.operation()));
        }
        List<String> products = client.getProductsPaginator(r -> r.serviceCode(key.serviceCode()).filters(filters))
            .priceList().stream().toList();
        List<Tier> tiers = List.of();
        String unit = null;
        if (products.size() == 1) {
            List<JsonNode> dimensions = onDemandDimensions(products.getFirst());
            tiers = dimensions.stream().map(AwsPricingCalculator::tier).toList();
            unit = dimensions.isEmpty() ? null : dimensions.getFirst().path("unit").asString();
        } else if (products.size() > 1) {
            // 어느 상품이 맞는지 알 수 없으면 틀린 값을 내기보다 가격 없음으로 둔다
            log.warn("단가가 하나로 정해지지 않아 쓰지 않습니다: {} (상품 {}개)", key, products.size());
        }
        return UnitPrice.builder()
            .serviceCode(key.serviceCode())
            .usageType(key.usageType())
            .operation(key.operation())
            .unit(unit)
            .tiers(tiers)
            .fetchedAt(Instant.now())
            .build();
    }

    private static Filter termMatch(String field, String value) {
        return Filter.builder().type(FilterType.TERM_MATCH).field(field).value(value).build();
    }

    private static List<JsonNode> onDemandDimensions(String product) {
        JsonNode onDemand = JsonMapper.shared().readTree(product).path("terms").path("OnDemand");
        if (onDemand.size() != 1) {
            log.warn("온디맨드 요금 조건이 {}개라 쓰지 않습니다.", onDemand.size());
            return List.of();
        }
        return List.copyOf(onDemand.values().iterator().next().path("priceDimensions").values());
    }

    private static Tier tier(JsonNode dimension) {
        String end = dimension.path("endRange").asString();
        return new Tier(
            new BigDecimal(dimension.path("beginRange").asString()),
            "Inf".equals(end) ? null : new BigDecimal(end),
            new BigDecimal(dimension.path("pricePerUnit").path("USD").asString()));
    }
}
