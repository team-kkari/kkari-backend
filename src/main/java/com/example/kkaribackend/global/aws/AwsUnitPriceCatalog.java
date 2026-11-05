package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.pricing.domain.UnitPrice;
import com.example.kkaribackend.domain.pricing.domain.UnitPrice.Tier;
import com.example.kkaribackend.domain.pricing.domain.repository.UnitPriceRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.FilterType;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;
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
public class AwsUnitPriceCatalog {
    static final Duration PRICE_TTL = Duration.ofDays(30);
    static final Duration MAX_PRICE_AGE = PRICE_TTL.multipliedBy(3);
    private static final String NO_PRICE = "AWS Price List 에 온디맨드 단가가 없습니다.";
    // 느린 응답이 요청 스레드와 분석 스레드를 오래 붙잡지 않게 한다
    private static final ClientOverrideConfiguration TIMEOUTS = ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(10))
        .apiCallAttemptTimeout(Duration.ofSeconds(5))
        .build();

    private final PricingClient client;
    private final UnitPriceRepository repository;

    @Autowired
    AwsUnitPriceCatalog(@Value("${aws.pricing.access-key}") String accessKey,
                        @Value("${aws.pricing.secret-key}") String secretKey,
                        UnitPriceRepository repository) {
        this(newClient(accessKey, secretKey), repository);
    }

    AwsUnitPriceCatalog(PricingClient client, UnitPriceRepository repository) {
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
    }

    public enum Status { PRICED, UNPRICED, CALL_FAILED }

    // usageType 은 후보 중 실제로 가격이 나온 이름이다. CALL_FAILED 는 단가 조회가 실패한 것이라 다시 시도하면 값이 나올 수 있다
    public record LinePrice(Status status, String usageType, BigDecimal monthlyUsd, String failure) {
        public static LinePrice priced(String usageType, BigDecimal monthlyUsd) {
            return new LinePrice(Status.PRICED, usageType, monthlyUsd, null);
        }

        public static LinePrice unpriced(String failure) {
            return new LinePrice(Status.UNPRICED, null, null, failure);
        }

        public static LinePrice callFailed(String failure) {
            return new LinePrice(Status.CALL_FAILED, null, null, failure);
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
            return unitPrice.hasPrice()
                ? LinePrice.priced(unitPrice.getUsageType(), unitPrice.cost(amount))
                : LinePrice.unpriced(NO_PRICE);
        }
    }

    // price() 한 번에서만 쓴다. 같은 단가는 한 번만 찾고, Price List 호출이 한 번 실패하면 남은 줄은 부르지 않는다
    private static final class Run {
        private final Map<PriceKey, Lookup> lookups = new HashMap<>();
        private String apiFailure;
    }

    // lines 와 같은 순서로 결과를 돌려준다. 실패해도 예외를 던지지 않고 모든 줄에 이유를 남긴다
    public List<LinePrice> price(List<UsageLine> lines) {
        Run run = new Run();
        return lines.stream().map(line -> price(line, run)).toList();
    }

    private LinePrice price(UsageLine line, Run run) {
        List<LinePrice> failures = new ArrayList<>();
        for (String usageType : line.usageTypes()) {
            PriceKey key = new PriceKey(line.serviceCode(), usageType, line.operation());
            LinePrice price = run.lookups.computeIfAbsent(key, k -> lookup(k, run)).price(line.amount());
            if (price.status() == Status.PRICED) {
                return price;
            }
            failures.add(price);
        }
        return failures.stream().filter(price -> price.status() == Status.CALL_FAILED).findFirst()
            .orElse(failures.getFirst());
    }

    private Lookup lookup(PriceKey key, Run run) {
        Optional<UnitPrice> cached;
        try {
            cached = repository.findByServiceCodeAndUsageTypeAndOperation(
                key.serviceCode(), key.usageType(), key.operation());
        } catch (DataAccessException e) {
            log.warn("저장된 단가 조회 실패: {}", key, e);
            return new Lookup(null, "단가 조회 실패: " + AwsFailures.reason(e));
        }
        if (cached.isPresent() && isFresh(cached.get())) {
            return new Lookup(cached.get(), null);
        }
        // 단가는 자주 바뀌지 않아, 새 단가를 받지 못했으면 실패로 끝내기보다 이전 단가를 쓴다
        Optional<UnitPrice> fallback = cached.filter(AwsUnitPriceCatalog::isUsable);

        UnitPrice fetched = null;
        if (run.apiFailure == null) {
            try {
                fetched = fetch(key);
            } catch (RuntimeException e) {
                run.apiFailure = "단가 조회 실패: " + AwsFailures.reason(e);
                log.warn("{}: {}", run.apiFailure, key);
            }
        }
        if (fetched != null && fetched.hasPrice()) {
            return new Lookup(save(cached, fetched), null);
        }
        if (fallback.isPresent()) {
            log.warn("새 단가를 받지 못해 이전 단가를 씁니다: {}", key);
            return new Lookup(fallback.get(), null);
        }
        // 없다는 결과는 저장하지 않는다. AI 가 보낸 임의의 이름마다 행이 쌓이지 않게 한다
        return fetched != null ? new Lookup(fetched, null) : new Lookup(null, run.apiFailure);
    }

    private static boolean isFresh(UnitPrice price) {
        return price.hasPrice() && price.getFetchedAt().plus(PRICE_TTL).isAfter(Instant.now());
    }

    // 갱신이 계속 실패해도 너무 오래된 단가로 조용히 계산하지 않는다
    private static boolean isUsable(UnitPrice price) {
        return price.hasPrice() && price.getFetchedAt().plus(MAX_PRICE_AGE).isAfter(Instant.now());
    }

    // 다른 요청이 같은 단가를 동시에 처음 저장하면 유니크 제약으로 하나만 남는다. 받아 온 단가는 같으므로 그대로 쓴다
    private UnitPrice save(Optional<UnitPrice> cached, UnitPrice fetched) {
        try {
            if (cached.isPresent()) {
                cached.get().refresh(fetched);
                return repository.saveAndFlush(cached.get());
            }
            return repository.saveAndFlush(fetched);
        } catch (DataIntegrityViolationException e) {
            log.info("다른 요청이 먼저 저장한 단가라 받아 온 값만 씁니다: {}", fetched.getUsageType());
            return fetched;
        } catch (DataAccessException e) {
            log.warn("단가를 저장하지 못해 받아 온 값만 씁니다: {}", fetched.getUsageType(), e);
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
        // 맞는 상품은 하나뿐이어야 하므로 둘까지만 받아 본다
        GetProductsResponse response = client.getProducts(r -> r.serviceCode(key.serviceCode()).filters(filters)
            .maxResults(2));
        List<String> products = response.priceList();
        List<Tier> tiers = List.of();
        String unit = null;
        if (products.size() > 1 || response.nextToken() != null) {
            // 어느 상품이 맞는지 알 수 없으면 틀린 값을 내기보다 가격 없음으로 둔다
            log.warn("단가가 하나로 정해지지 않아 쓰지 않습니다: {}", key);
        } else if (products.size() == 1) {
            List<JsonNode> dimensions = onDemandDimensions(products.getFirst());
            tiers = dimensions.stream().map(AwsUnitPriceCatalog::tier).toList();
            unit = dimensions.isEmpty() ? null : dimensions.getFirst().path("unit").asString();
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
