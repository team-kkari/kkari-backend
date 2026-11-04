package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LambdaFunction;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.S3Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsCostEstimatorTest {
    private static final String EC2_PRODUCT = product("Hrs", "0.0130000000");
    private static final String S3_PRODUCT = product("GB-Mo", "0.0250000000");

    private final List<Missing> missing = new ArrayList<>();
    private PricingClient pricingClient;

    @BeforeEach
    void setUp() {
        pricingClient = mock(PricingClient.class, invocation ->
            invocation.getMethod().getName().endsWith("Paginator")
                ? invocation.callRealMethod()
                : RETURNS_DEFAULTS.answer(invocation));
        when(pricingClient.getProducts(any(GetProductsRequest.class))).thenAnswer(invocation ->
            GetProductsResponse.builder().priceList(
                "AmazonS3".equals(invocation.<GetProductsRequest>getArgument(0).serviceCode())
                    ? S3_PRODUCT : EC2_PRODUCT).build());
    }

    @Test
    void EC2_월_비용은_시간당_단가_곱하기_730() {
        Resources resources = Resources.builder()
            .ec2(List.of(
                new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a"),
                new Ec2Instance("i-2", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2c"),
                new Ec2Instance("i-3", "t3.micro", "Linux/UNIX", "stopped", "ap-northeast-2a"),
                new Ec2Instance("i-4", "t3.micro", "Windows", "running", "ap-northeast-2a")))
            .build();

        Cost cost = estimator().estimate(resources, missing);

        assertThat(cost.currency()).isEqualTo("USD");
        assertThat(cost.basis()).isEqualTo("ON_DEMAND_LIST_PRICE");
        assertThat(cost.monthlyUsdByResource().get("EC2"))
            .containsEntry("i-1", new BigDecimal("9.49"))
            .containsEntry("i-2", new BigDecimal("9.49"))
            .containsEntry("i-3", BigDecimal.ZERO)
            .containsEntry("i-4", null);
        assertThat(missing).extracting(Missing::target).containsExactly("COST EC2 i-4");

        // 같은 조건의 단가는 한 번만 조회한다
        ArgumentCaptor<GetProductsRequest> captor = ArgumentCaptor.forClass(GetProductsRequest.class);
        verify(pricingClient, times(1)).getProducts(captor.capture());
        assertThat(captor.getValue().serviceCode()).isEqualTo("AmazonEC2");
        assertThat(captor.getValue().filters()).extracting(Filter::field, Filter::value).contains(
            tuple("regionCode", "ap-northeast-2"),
            tuple("instanceType", "t3.micro"),
            tuple("operatingSystem", "Linux"),
            tuple("tenancy", "Shared"),
            tuple("preInstalledSw", "NA"),
            tuple("capacitystatus", "Used"));
    }

    @Test
    void 종류가_다르면_같은_이름이어도_비용이_따로_남는다() {
        Resources resources = Resources.builder()
            .s3(List.of(new S3Bucket("app", 4L << 30)))
            .lambda(List.of(new LambdaFunction("app", "java21", 512), new LambdaFunction("worker", "python3.12", 128)))
            .build();

        Cost cost = estimator().estimate(resources, missing);

        assertThat(cost.monthlyUsdByResource().get("S3")).containsEntry("app", new BigDecimal("0.10"));
        assertThat(cost.monthlyUsdByResource().get("LAMBDA")).containsEntry("app", null).containsEntry("worker", null);
        assertThat(missing).extracting(Missing::target).containsExactly("COST LAMBDA");
    }

    @Test
    void 가격_조회_키가_없으면_비용은_null이고_missing에_남긴다() {
        Resources resources = Resources.builder()
            .ec2(List.of(new Ec2Instance("i-1", "t3.micro", "Linux/UNIX", "running", "ap-northeast-2a")))
            .build();

        Cost cost = new AwsCostEstimator("ap-northeast-2", null, new ObjectMapper()).estimate(resources, missing);

        assertThat(cost.monthlyUsdByResource()).isNull();
        assertThat(cost.formulas()).containsKeys("EC2", "EBS", "RDS", "S3", "LAMBDA", "ELB", "NAT");
        assertThat(missing).containsExactly(new Missing("COST", "가격 조회용 AWS 자격증명이 설정되지 않았습니다."));
    }

    private AwsCostEstimator estimator() {
        return new AwsCostEstimator("ap-northeast-2", pricingClient, new ObjectMapper());
    }

    private static String product(String unit, String usd) {
        return """
            {"terms":{"OnDemand":{"ABC.JRTCKXETXF":{"priceDimensions":{
              "ABC.JRTCKXETXF.6YS6EN2CT7":{"unit":"%s","beginRange":"0","endRange":"Inf","pricePerUnit":{"USD":"%s"}}
            }}}}}""".formatted(unit, usd);
    }
}
