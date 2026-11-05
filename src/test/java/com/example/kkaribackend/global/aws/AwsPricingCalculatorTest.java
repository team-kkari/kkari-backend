package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.bcmpricingcalculator.BcmPricingCalculatorClient;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageCode;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageError;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageItem;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageRequest;
import software.amazon.awssdk.services.bcmpricingcalculator.model.BatchCreateWorkloadEstimateUsageResponse;
import software.amazon.awssdk.services.bcmpricingcalculator.model.CreateWorkloadEstimateRequest;
import software.amazon.awssdk.services.bcmpricingcalculator.model.CreateWorkloadEstimateResponse;
import software.amazon.awssdk.services.bcmpricingcalculator.model.DeleteWorkloadEstimateRequest;
import software.amazon.awssdk.services.bcmpricingcalculator.model.WorkloadEstimateCostStatus;
import software.amazon.awssdk.services.bcmpricingcalculator.model.WorkloadEstimateRateType;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsPricingCalculatorTest {
    private static final String ESTIMATE_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ACCOUNT_ID = "123456789012";

    private BcmPricingCalculatorClient client;
    private StsClient sts;
    private AwsPricingCalculator calculator;

    @BeforeEach
    void setUp() {
        client = mockWithConsumerOverloads(BcmPricingCalculatorClient.class);
        sts = mockWithConsumerOverloads(StsClient.class);
        when(sts.getCallerIdentity(any(GetCallerIdentityRequest.class)))
            .thenReturn(GetCallerIdentityResponse.builder().account(ACCOUNT_ID).build());
        when(client.createWorkloadEstimate(any(CreateWorkloadEstimateRequest.class)))
            .thenReturn(CreateWorkloadEstimateResponse.builder().id(ESTIMATE_ID).build());
        when(client.batchCreateWorkloadEstimateUsage(any(BatchCreateWorkloadEstimateUsageRequest.class)))
            .thenAnswer(invocation -> validItems(invocation.getArgument(0), 9.49));
        calculator = new AwsPricingCalculator(client, sts);
    }

    @Test
    void 줄을_25개씩_나눠_보내고_견적은_한_번_만들고_지운다() {
        List<UsageLine> lines = IntStream.range(0, 26)
            .mapToObj(i -> new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", 730))
            .toList();

        List<LinePrice> prices = calculator.price(lines);

        assertThat(prices).hasSize(26).containsOnly(LinePrice.priced(new BigDecimal("9.49")));
        ArgumentCaptor<BatchCreateWorkloadEstimateUsageRequest> batches =
            ArgumentCaptor.forClass(BatchCreateWorkloadEstimateUsageRequest.class);
        verify(client, times(2)).batchCreateWorkloadEstimateUsage(batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.usage().size()).containsExactly(25, 1);
        assertThat(batches.getAllValues()).allSatisfy(batch -> {
            assertThat(batch.workloadEstimateId()).isEqualTo(ESTIMATE_ID);
            assertThat(batch.usage()).allSatisfy(entry -> {
                assertThat(entry.usageAccountId()).isEqualTo(ACCOUNT_ID);
                assertThat(entry.key()).matches("[a-zA-Z0-9]{1,10}");
            });
        });

        ArgumentCaptor<CreateWorkloadEstimateRequest> create = ArgumentCaptor.forClass(CreateWorkloadEstimateRequest.class);
        verify(client).createWorkloadEstimate(create.capture());
        assertThat(create.getValue().rateType()).isEqualTo(WorkloadEstimateRateType.BEFORE_DISCOUNTS);
        assertThat(create.getValue().name()).matches("kkari-[0-9a-f-]{36}");
        verify(client).deleteWorkloadEstimate(DeleteWorkloadEstimateRequest.builder().identifier(ESTIMATE_ID).build());
    }

    @Test
    void 계정_ID는_처음_한_번만_조회한다() {
        List<UsageLine> lines = List.of(new UsageLine("AmazonEC2", "APN2-NatGateway-Hours", "NatGateway", 730));

        calculator.price(lines);
        calculator.price(lines);

        verify(sts, times(1)).getCallerIdentity(any(GetCallerIdentityRequest.class));
        verify(client, times(2)).createWorkloadEstimate(any(CreateWorkloadEstimateRequest.class));
        verify(client, times(2)).deleteWorkloadEstimate(any(DeleteWorkloadEstimateRequest.class));
    }

    @Test
    void 줄마다_비용이나_실패_이유를_돌려준다() {
        when(client.batchCreateWorkloadEstimateUsage(any(BatchCreateWorkloadEstimateUsageRequest.class)))
            .thenReturn(BatchCreateWorkloadEstimateUsageResponse.builder()
                .items(
                    BatchCreateWorkloadEstimateUsageItem.builder().key("k0").cost(16.425)
                        .status(WorkloadEstimateCostStatus.VALID).build(),
                    BatchCreateWorkloadEstimateUsageItem.builder().key("k1")
                        .status(WorkloadEstimateCostStatus.STALE).build())
                .errors(
                    BatchCreateWorkloadEstimateUsageError.builder().key("k2")
                        .errorCode(BatchCreateWorkloadEstimateUsageCode.BAD_REQUEST).build(),
                    BatchCreateWorkloadEstimateUsageError.builder().key("k3")
                        .errorCode(BatchCreateWorkloadEstimateUsageCode.INTERNAL_SERVER_ERROR).build())
                .build());

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Application", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:nope", "RunInstances", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.nano", "RunInstances", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.small", "RunInstances", 730)));

        assertThat(prices).containsExactly(
            LinePrice.priced(new BigDecimal("16.43")),
            LinePrice.unpriced("가격 계산 결과가 유효하지 않습니다: STALE"),
            LinePrice.unpriced("가격 계산 실패: BAD_REQUEST"),
            LinePrice.callFailed("가격 계산 실패: INTERNAL_SERVER_ERROR"),
            LinePrice.callFailed("가격 계산 응답에 결과가 없습니다."));
    }

    @Test
    void 이름_후보_중_가격이_나온_것을_쓴다() {
        when(client.batchCreateWorkloadEstimateUsage(any(BatchCreateWorkloadEstimateUsageRequest.class)))
            .thenReturn(BatchCreateWorkloadEstimateUsageResponse.builder()
                .items(BatchCreateWorkloadEstimateUsageItem.builder().key("k1").cost(372.3)
                    .status(WorkloadEstimateCostStatus.VALID).build())
                .errors(
                    BatchCreateWorkloadEstimateUsageError.builder().key("k0")
                        .errorCode(BatchCreateWorkloadEstimateUsageCode.BAD_REQUEST).build(),
                    BatchCreateWorkloadEstimateUsageError.builder().key("k2")
                        .errorCode(BatchCreateWorkloadEstimateUsageCode.BAD_REQUEST).build(),
                    BatchCreateWorkloadEstimateUsageError.builder().key("k3")
                        .errorCode(BatchCreateWorkloadEstimateUsageCode.BAD_REQUEST).build())
                .build());

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonRDS", List.of("APN2-InstanceUsage:db.r6g.xlarge", "APN2-InstanceUsage:db.r6g.xl"),
                "CreateDBInstance:0002", 730),
            new UsageLine("AmazonRDS", List.of("APN2-InstanceUsage:db.x.xlarge", "APN2-InstanceUsage:db.x.xl"),
                "CreateDBInstance:0002", 730)));

        assertThat(prices).containsExactly(
            LinePrice.priced(new BigDecimal("372.30")),
            LinePrice.unpriced("가격 계산 실패: BAD_REQUEST"));
    }

    @Test
    void 배치_호출이_실패하면_남은_배치는_보내지_않고_견적을_지운다() {
        when(client.batchCreateWorkloadEstimateUsage(any(BatchCreateWorkloadEstimateUsageRequest.class)))
            .thenThrow(serviceException("ThrottlingException", 400));
        List<UsageLine> lines = IntStream.range(0, 26)
            .mapToObj(i -> new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", 730))
            .toList();

        List<LinePrice> prices = calculator.price(lines);

        assertThat(prices).hasSize(26)
            .containsOnly(LinePrice.callFailed("가격 계산 실패: ThrottlingException (HTTP 400)"));
        verify(client).batchCreateWorkloadEstimateUsage(any(BatchCreateWorkloadEstimateUsageRequest.class));
        verify(client).deleteWorkloadEstimate(any(DeleteWorkloadEstimateRequest.class));
    }

    @Test
    void 견적을_만들지_못하면_모든_줄이_실패하고_지울_것도_없다() {
        when(client.createWorkloadEstimate(any(CreateWorkloadEstimateRequest.class)))
            .thenThrow(serviceException("AccessDeniedException", 403));

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonEC2", "APN2-NatGateway-Hours", "NatGateway", 730)));

        assertThat(prices).containsExactly(
            LinePrice.callFailed("가격 계산 준비 실패: AccessDeniedException (HTTP 403)"));
        verify(client, never()).batchCreateWorkloadEstimateUsage(any(BatchCreateWorkloadEstimateUsageRequest.class));
        verify(client, never()).deleteWorkloadEstimate(any(DeleteWorkloadEstimateRequest.class));
    }

    @Test
    void 견적_삭제가_실패해도_결과는_돌려준다() {
        when(client.deleteWorkloadEstimate(any(DeleteWorkloadEstimateRequest.class)))
            .thenThrow(serviceException("InternalServerException", 500));

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonEC2", "APN2-NatGateway-Hours", "NatGateway", 730)));

        assertThat(prices).containsExactly(LinePrice.priced(new BigDecimal("9.49")));
    }

    @Test
    void 줄이_없으면_API를_부르지_않는다() {
        assertThat(calculator.price(List.of())).isEmpty();

        verify(client, never()).createWorkloadEstimate(any(CreateWorkloadEstimateRequest.class));
        verify(sts, never()).getCallerIdentity(any(GetCallerIdentityRequest.class));
    }

    private static BatchCreateWorkloadEstimateUsageResponse validItems(BatchCreateWorkloadEstimateUsageRequest request,
                                                                       double cost) {
        return BatchCreateWorkloadEstimateUsageResponse.builder()
            .items(request.usage().stream()
                .map(entry -> BatchCreateWorkloadEstimateUsageItem.builder()
                    .key(entry.key()).cost(cost).status(WorkloadEstimateCostStatus.VALID).build())
                .toList())
            .build();
    }

    private static AwsServiceException serviceException(String code, int status) {
        return AwsServiceException.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
            .statusCode(status)
            .build();
    }

    // SDK 의 인자 없는·Consumer<Builder> 오버로드는 요청 객체 버전을 부르는 기본 메서드라 실제 구현을 탄다
    private static <T> T mockWithConsumerOverloads(Class<T> type) {
        Answer<Object> answer = invocation -> invocation.getMethod().isDefault()
            && (invocation.getArguments().length == 0 || invocation.getArgument(0) instanceof Consumer<?>)
            ? invocation.callRealMethod()
            : RETURNS_DEFAULTS.answer(invocation);
        return mock(type, answer);
    }
}
