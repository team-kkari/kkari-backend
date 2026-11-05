package com.example.kkaribackend.global.aws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.function.SingletonSupplier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bcmpricingcalculator.BcmPricingCalculatorClient;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageEntry;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageError;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageItem;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageResponse;
import software.amazon.awssdk.services.bcmpricingcalculator.model.WorkloadEstimateCostStatus;
import software.amazon.awssdk.services.bcmpricingcalculator.model.WorkloadEstimateRateType;
import software.amazon.awssdk.services.sts.StsClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * AWS Pricing Calculator 로 사용량 줄마다 월 비용(USD)을 계산한다.
 * 호출마다 임시 견적을 따로 만들고 끝나면 지워서, 동시에 호출해도 서로 섞이지 않는다.
 */
@Slf4j
class AwsPricingCalculator implements AutoCloseable {
    static final int MAX_USAGE_PER_BATCH = 25;

    private final BcmPricingCalculatorClient client;
    private final StsClient sts;
    private final Supplier<String> accountId;

    AwsPricingCalculator(BcmPricingCalculatorClient client, StsClient sts) {
        this.client = client;
        this.sts = sts;
        this.accountId = SingletonSupplier.of(() -> sts.getCallerIdentity().account());
    }

    // Pricing Calculator API 는 us-east-1 에만 있다
    static AwsPricingCalculator create(String accessKey, String secretKey) {
        StaticCredentialsProvider credentials =
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        return new AwsPricingCalculator(
            BcmPricingCalculatorClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build(),
            StsClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build());
    }

    @Override
    public void close() {
        client.close();
        sts.close();
    }

    // key 는 [a-zA-Z0-9]{1,10} 이고 한 호출 안에서 겹치지 않아야 한다
    record UsageLine(String key, String serviceCode, String usageType, String operation, double amount) {
    }

    // monthlyUsd 와 failure 중 하나만 값이 있다
    record LinePrice(BigDecimal monthlyUsd, String failure) {
        static LinePrice failed(String failure) {
            return new LinePrice(null, failure);
        }
    }

    // UsageLine.key -> 결과. 실패해도 예외를 던지지 않고 모든 줄에 이유를 남긴다
    Map<String, LinePrice> price(List<UsageLine> lines) {
        Map<String, LinePrice> prices = new HashMap<>();
        if (lines.isEmpty()) {
            return prices;
        }
        String account;
        String estimateId;
        try {
            account = accountId.get();
            estimateId = createEstimate();
        } catch (RuntimeException e) {
            failAll(prices, lines, "가격 계산 준비 실패: " + AwsFailures.reason(e));
            return prices;
        }
        try {
            for (int from = 0; from < lines.size(); from += MAX_USAGE_PER_BATCH) {
                List<UsageLine> batch = lines.subList(from, Math.min(from + MAX_USAGE_PER_BATCH, lines.size()));
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

    private Map<String, LinePrice> priceBatch(String estimateId, String account, List<UsageLine> batch) {
        BatchCreateWorkloadEstimateUsageResponse response = client.batchCreateWorkloadEstimateUsage(r -> r
            .workloadEstimateId(estimateId)
            .usage(batch.stream().map(line -> entry(line, account)).toList()));

        Map<String, LinePrice> prices = new HashMap<>();
        for (BatchCreateWorkloadEstimateUsageItem item : response.items()) {
            prices.put(item.key(), item.status() == WorkloadEstimateCostStatus.VALID && item.cost() != null
                ? new LinePrice(BigDecimal.valueOf(item.cost()).setScale(2, RoundingMode.HALF_UP), null)
                : LinePrice.failed("가격 계산 결과가 유효하지 않습니다: " + item.statusAsString()));
        }
        for (BatchCreateWorkloadEstimateUsageError error : response.errors()) {
            prices.put(error.key(), LinePrice.failed("가격 계산 실패: " + error.errorCodeAsString()));
        }
        for (UsageLine line : batch) {
            prices.putIfAbsent(line.key(), LinePrice.failed("가격 계산 응답에 결과가 없습니다."));
        }
        return prices;
    }

    private static BatchCreateWorkloadEstimateUsageEntry entry(UsageLine line, String account) {
        return BatchCreateWorkloadEstimateUsageEntry.builder()
            .key(line.key())
            .serviceCode(line.serviceCode())
            .usageType(line.usageType())
            .operation(line.operation())
            .amount(line.amount())
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

    private static void failAll(Map<String, LinePrice> prices, List<UsageLine> lines, String failure) {
        log.warn("{}", failure);
        lines.forEach(line -> prices.put(line.key(), LinePrice.failed(failure)));
    }
}
