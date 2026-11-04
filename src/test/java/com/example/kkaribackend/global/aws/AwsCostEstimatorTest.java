package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Cost;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Ec2Instance;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.LambdaFunction;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Missing;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot.Resources;
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
    private static final String EC2_PRODUCT = """
        {"product":{"attributes":{"instanceType":"t3.micro"}},
         "terms":{"OnDemand":{"ABC.JRTCKXETXF":{"priceDimensions":{
           "ABC.JRTCKXETXF.6YS6EN2CT7":{"unit":"Hrs","beginRange":"0","endRange":"Inf","pricePerUnit":{"USD":"0.0130000000"}}
         }}}}}""";

    private final Resources resources = Resources.builder()
        .ec2(List.of(
            new Ec2Instance("i-1", "t3.micro", "running", "ap-northeast-2a"),
            new Ec2Instance("i-2", "t3.micro", "running", "ap-northeast-2c"),
            new Ec2Instance("i-3", "t3.micro", "stopped", "ap-northeast-2a")))
        .lambda(List.of(new LambdaFunction("fn", "java21", 512)))
        .build();

    @Test
    void EC2_월_비용은_시간당_단가_곱하기_730() {
        PricingClient pricingClient = mock(PricingClient.class, invocation ->
            invocation.getMethod().getName().endsWith("Paginator")
                ? invocation.callRealMethod()
                : RETURNS_DEFAULTS.answer(invocation));
        when(pricingClient.getProducts(any(GetProductsRequest.class)))
            .thenReturn(GetProductsResponse.builder().priceList(EC2_PRODUCT).build());
        List<Missing> missing = new ArrayList<>();

        Cost cost = new AwsCostEstimator("ap-northeast-2", pricingClient, new ObjectMapper()).estimate(resources, missing);

        assertThat(cost.currency()).isEqualTo("USD");
        assertThat(cost.basis()).isEqualTo("ON_DEMAND_LIST_PRICE");
        assertThat(cost.monthlyUsdByResource())
            .containsEntry("i-1", new BigDecimal("9.49"))
            .containsEntry("i-2", new BigDecimal("9.49"))
            .containsEntry("i-3", BigDecimal.ZERO)
            .containsEntry("fn", null);
        assertThat(missing).isEmpty();

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
    void 가격_조회_키가_없으면_비용은_null이고_missing에_남긴다() {
        List<Missing> missing = new ArrayList<>();

        Cost cost = new AwsCostEstimator("ap-northeast-2", null, new ObjectMapper()).estimate(resources, missing);

        assertThat(cost.monthlyUsdByResource()).isNull();
        assertThat(cost.formulas()).containsKeys("EC2", "EBS", "RDS", "S3", "LAMBDA", "ELB", "NAT");
        assertThat(missing).containsExactly(new Missing("COST", "가격 조회용 AWS 자격증명이 설정되지 않았습니다."));
    }
}
