package com.example.kkaribackend.global.aws;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bcmpricingcalculator.BcmPricingCalculatorClient;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageCode;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageEntry;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageError;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageItem;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageResponse;
import software.amazon.awssdk.services.bcmpricingcalculator.model.WorkloadEstimateCostStatus;
import software.amazon.awssdk.services.bcmpricingcalculator.model.WorkloadEstimateRateType;
import software.amazon.awssdk.services.sts.StsClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * AWS Pricing Calculator 로 사용량 줄마다 월 비용(USD)을 계산한다.
 * 호출마다 임시 견적을 따로 만들고 끝나면 지워서, 동시에 호출해도 서로 섞이지 않는다.
 * 가격 조회는 고객사 Role 도 서버 기본 자격증명도 아닌 별도 계정 키로 한다.
 */
@Slf4j
@Component
class AwsPricingCalculator {
    static final int MAX_USAGE_PER_BATCH = 25;
    // 느린 응답이 요청 스레드와 분석 스레드를 오래 붙잡지 않게 한다
    private static final ClientOverrideConfiguration TIMEOUTS = ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(10))
        .apiCallAttemptTimeout(Duration.ofSeconds(5))
        .build();

    private final BcmPricingCalculatorClient client;
    private final StsClient sts;
    private final Supplier<String> accountId;

    @Autowired
    AwsPricingCalculator(@Value("${aws.pricing.access-key}") String accessKey,
                         @Value("${aws.pricing.secret-key}") String secretKey) {
        this(newClient(BcmPricingCalculatorClient.builder(), accessKey, secretKey),
            newClient(StsClient.builder(), accessKey, secretKey));
    }

    AwsPricingCalculator(BcmPricingCalculatorClient client, StsClient sts) {
        this.client = client;
        this.sts = sts;
        this.accountId = SingletonSupplier.of(() -> sts.getCallerIdentity().account());
    }

    // Pricing Calculator API 는 us-east-1 에만 있다
    private static <B extends AwsClientBuilder<B, C>, C> C newClient(B builder, String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return null;
        }
        return builder.region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .overrideConfiguration(TIMEOUTS)
            .build();
    }

    boolean isConfigured() {
        return client != null;
    }

    @PreDestroy
    void close() {
        if (isConfigured()) {
            client.close();
            sts.close();
        }
    }

    // usageTypes: 같은 요금의 이름 후보. 가격이 나온 첫 후보를 쓴다
    record UsageLine(String serviceCode, List<String> usageTypes, String operation, double amount) {
        UsageLine(String serviceCode, String usageType, String operation, double amount) {
            this(serviceCode, List.of(usageType), operation, amount);
        }

        String usageType() {
            return usageTypes.getFirst();
        }
    }

    enum Status { PRICED, UNPRICED, CALL_FAILED }

    // CALL_FAILED 는 줄이 아니라 API 호출이 실패한 것이라 다시 시도하면 값이 나올 수 있다
    record LinePrice(Status status, BigDecimal monthlyUsd, String failure) {
        static LinePrice priced(BigDecimal monthlyUsd) {
            return new LinePrice(Status.PRICED, monthlyUsd, null);
        }

        static LinePrice unpriced(String failure) {
            return new LinePrice(Status.UNPRICED, null, failure);
        }

        static LinePrice callFailed(String failure) {
            return new LinePrice(Status.CALL_FAILED, null, failure);
        }
    }

    // API 에 보내는 이름 후보 하나. key 는 [a-zA-Z0-9]{1,10} 이고 한 호출 안에서 겹치지 않아야 한다
    private record Candidate(String key, UsageLine line, String usageType) {
    }

    // lines 와 같은 순서로 결과를 돌려준다. 실패해도 예외를 던지지 않고 모든 줄에 이유를 남긴다
    List<LinePrice> price(List<UsageLine> lines) {
        List<Candidate> candidates = new ArrayList<>();
        List<List<Candidate>> candidatesByLine = new ArrayList<>();
        for (UsageLine line : lines) {
            List<Candidate> lineCandidates = new ArrayList<>();
            for (String usageType : line.usageTypes()) {
                Candidate candidate = new Candidate("k" + candidates.size(), line, usageType);
                lineCandidates.add(candidate);
                candidates.add(candidate);
            }
            candidatesByLine.add(lineCandidates);
        }
        Map<String, LinePrice> prices = priceCandidates(candidates);
        return candidatesByLine.stream().map(lineCandidates -> best(lineCandidates, prices)).toList();
    }

    private static LinePrice best(List<Candidate> candidates, Map<String, LinePrice> prices) {
        List<LinePrice> results = candidates.stream().map(candidate -> prices.get(candidate.key())).toList();
        return results.stream().filter(price -> price.status() == Status.PRICED).findFirst()
            .or(() -> results.stream().filter(price -> price.status() == Status.CALL_FAILED).findFirst())
            .orElse(results.getFirst());
    }

    private Map<String, LinePrice> priceCandidates(List<Candidate> candidates) {
        Map<String, LinePrice> prices = new HashMap<>();
        if (candidates.isEmpty()) {
            return prices;
        }
        String account;
        String estimateId;
        try {
            account = accountId.get();
            estimateId = createEstimate();
        } catch (RuntimeException e) {
            failAll(prices, candidates, "가격 계산 준비 실패: " + AwsFailures.reason(e));
            return prices;
        }
        try {
            for (int from = 0; from < candidates.size(); from += MAX_USAGE_PER_BATCH) {
                List<Candidate> batch = candidates.subList(from, Math.min(from + MAX_USAGE_PER_BATCH, candidates.size()));
                try {
                    prices.putAll(priceBatch(estimateId, account, batch));
                } catch (RuntimeException e) {
                    failAll(prices, batch, "가격 계산 실패: " + AwsFailures.reason(e));
                }
            }
            return prices;
        } finally {
            deleteEstimate(estimateId);
        }
    }

    private String createEstimate() {
        return client.createWorkloadEstimate(r -> r
            .name("kkari-" + UUID.randomUUID())
            .rateType(WorkloadEstimateRateType.BEFORE_DISCOUNTS)).id();
    }

    private Map<String, LinePrice> priceBatch(String estimateId, String account, List<Candidate> batch) {
        BatchCreateWorkloadEstimateUsageResponse response = client.batchCreateWorkloadEstimateUsage(r -> r
            .workloadEstimateId(estimateId)
            .usage(batch.stream().map(candidate -> entry(candidate, account)).toList()));

        Map<String, LinePrice> prices = new HashMap<>();
        for (BatchCreateWorkloadEstimateUsageItem item : response.items()) {
            prices.put(item.key(), item.status() == WorkloadEstimateCostStatus.VALID && item.cost() != null
                ? LinePrice.priced(BigDecimal.valueOf(item.cost()).setScale(2, RoundingMode.HALF_UP))
                : LinePrice.unpriced("가격 계산 결과가 유효하지 않습니다: " + item.statusAsString()));
        }
        for (BatchCreateWorkloadEstimateUsageError error : response.errors()) {
            String failure = "가격 계산 실패: " + error.errorCodeAsString();
            prices.put(error.key(), error.errorCode() == BatchCreateWorkloadEstimateUsageCode.BAD_REQUEST
                ? LinePrice.unpriced(failure) : LinePrice.callFailed(failure));
        }
        for (Candidate candidate : batch) {
            prices.putIfAbsent(candidate.key(), LinePrice.callFailed("가격 계산 응답에 결과가 없습니다."));
        }
        return prices;
    }

    private static BatchCreateWorkloadEstimateUsageEntry entry(Candidate candidate, String account) {
        return BatchCreateWorkloadEstimateUsageEntry.builder()
            .key(candidate.key())
            .serviceCode(candidate.line().serviceCode())
            .usageType(candidate.usageType())
            .operation(candidate.line().operation())
            .amount(candidate.line().amount())
            .usageAccountId(account)
            .build();
    }

    // 지우지 못한 견적은 결과에 영향이 없어 로그만 남긴다
    private void deleteEstimate(String estimateId) {
        try {
            client.deleteWorkloadEstimate(r -> r.identifier(estimateId));
        } catch (RuntimeException e) {
            log.warn("임시 가격 견적 삭제 실패: {} {}", estimateId, AwsFailures.reason(e));
        }
    }

    private static void failAll(Map<String, LinePrice> prices, List<Candidate> candidates, String failure) {
        log.warn("{}", failure);
        candidates.forEach(candidate -> prices.put(candidate.key(), LinePrice.callFailed(failure)));
    }
}
