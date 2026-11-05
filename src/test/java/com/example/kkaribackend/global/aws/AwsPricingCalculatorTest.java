package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.pricing.domain.UnitPrice;
import com.example.kkaribackend.domain.pricing.domain.UnitPrice.Tier;
import com.example.kkaribackend.domain.pricing.domain.repository.UnitPriceRepository;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.LinePrice;
import com.example.kkaribackend.global.aws.AwsPricingCalculator.UsageLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.FilterType;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsPricingCalculatorTest {
    private static final UsageLine T3_MICRO = new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", 730);

    // usageType -> Price List 가 돌려줄 상품 JSON. 없으면 상품 0개
    private final Map<String, List<String>> products = new HashMap<>();
    private PricingClient client;
    private UnitPriceRepository repository;
    private AwsPricingCalculator calculator;

    @BeforeEach
    void setUp() {
        // 페이지네이터는 SDK 의 기본 메서드라 실제 구현이 getProducts 를 부르게 둔다
        client = mock(PricingClient.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            GetProductsRequest request = invocation.getArgument(0);
            return GetProductsResponse.builder()
                .priceList(products.getOrDefault(filterValue(request, "usagetype"), List.of()))
                .build();
        }).when(client).getProducts(any(GetProductsRequest.class));
        repository = mock(UnitPriceRepository.class);
        when(repository.findByServiceCodeAndUsageTypeAndOperation(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UnitPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        calculator = new AwsPricingCalculator(client, repository);
    }

    @Test
    void 저장된_단가가_있으면_API를_부르지_않고_계산한다() {
        cached("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Application", "0.0225", Duration.ofDays(29));
        cached("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances:0002", "0.0222", Duration.ofDays(1));
        cached("AmazonRDS", "APN2-InstanceUsage:db.t3.micro", "CreateDBInstance:0002", "0.026", Duration.ZERO);

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AWSELB", "APN2-LoadBalancerUsage", "LoadBalancing:Application", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances:0002", 730),
            new UsageLine("AmazonRDS", "APN2-InstanceUsage:db.t3.micro", "CreateDBInstance:0002", 730)));

        assertThat(prices).containsExactly(
            LinePrice.priced(new BigDecimal("16.43")),
            LinePrice.priced(new BigDecimal("16.21")),
            LinePrice.priced(new BigDecimal("18.98")));
        verify(client, never()).getProducts(any(GetProductsRequest.class));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 저장된_단가가_없으면_Price_List에서_받아_저장한다() {
        products.put("APN2-BoxUsage:t3.micro", List.of(product("Hrs", "0,Inf,0.0130000000")));

        List<LinePrice> prices = calculator.price(List.of(T3_MICRO));

        assertThat(prices).containsExactly(LinePrice.priced(new BigDecimal("9.49")));
        GetProductsRequest request = sentRequest();
        assertThat(request.serviceCode()).isEqualTo("AmazonEC2");
        assertThat(request.filters()).extracting(Filter::type, Filter::field, Filter::value).containsExactly(
            tuple(FilterType.TERM_MATCH, "usagetype", "APN2-BoxUsage:t3.micro"),
            tuple(FilterType.TERM_MATCH, "operation", "RunInstances"));
        UnitPrice saved = savedPrice();
        assertThat(saved.getId()).isNull();
        assertThat(saved).extracting(UnitPrice::getServiceCode, UnitPrice::getUsageType, UnitPrice::getOperation,
            UnitPrice::getUnit).containsExactly("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", "Hrs");
        assertThat(saved.getTiers()).containsExactly(new Tier(BigDecimal.ZERO, null, new BigDecimal("0.0130000000")));
    }

    @Test
    void operation이_없는_요금은_usagetype으로만_찾고_구간_요금을_누적한다() {
        products.put("APN2-TimedStorage-ByteHrs", List.of(product("GB-Mo",
            "51200,512000,0.0240000000", "0,51200,0.0250000000", "512000,Inf,0.0230000000")));

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonS3", "APN2-TimedStorage-ByteHrs", "", 600000),
            new UsageLine("AmazonS3", "APN2-TimedStorage-ByteHrs", "", 50)));

        assertThat(prices).containsExactly(
            LinePrice.priced(new BigDecimal("14363.20")),
            LinePrice.priced(new BigDecimal("1.25")));
        assertThat(sentRequest().filters()).extracting(Filter::field).containsExactly("usagetype");
        assertThat(savedPrice().getOperation()).isEmpty();
    }

    @Test
    void 저장한_지_30일이_지난_단가는_다시_받아_갱신한다() {
        UnitPrice stale = cached("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", "0.0100",
            AwsPricingCalculator.PRICE_TTL.plusMinutes(1));
        products.put("APN2-BoxUsage:t3.micro", List.of(product("Hrs", "0,Inf,0.0130000000")));

        List<LinePrice> prices = calculator.price(List.of(T3_MICRO));

        assertThat(prices).containsExactly(LinePrice.priced(new BigDecimal("9.49")));
        UnitPrice saved = savedPrice();
        assertThat(saved).isSameAs(stale);
        assertThat(saved.getTiers()).extracting(Tier::usdPerUnit).containsExactly(new BigDecimal("0.0130000000"));
        assertThat(saved.getFetchedAt()).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    void 갱신_호출이_실패하면_이전_단가를_쓴다() {
        cached("AmazonEC2", "APN2-BoxUsage:t3.micro", "RunInstances", "0.0130",
            AwsPricingCalculator.PRICE_TTL.plusDays(5));
        doThrow(serviceException("ThrottlingException", 400)).when(client).getProducts(any(GetProductsRequest.class));

        List<LinePrice> prices = calculator.price(List.of(T3_MICRO));

        assertThat(prices).containsExactly(LinePrice.priced(new BigDecimal("9.49")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 저장된_단가도_없는데_호출이_실패하면_CALL_FAILED다() {
        doThrow(serviceException("ThrottlingException", 400)).when(client).getProducts(any(GetProductsRequest.class));

        List<LinePrice> prices = calculator.price(List.of(T3_MICRO,
            new UsageLine("AmazonEC2", "APN2-NatGateway-Hours", "NatGateway", 730)));

        assertThat(prices).containsOnly(LinePrice.callFailed("단가 조회 실패: ThrottlingException (HTTP 400)"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 상품이_없거나_여러_개면_가격_없음으로_저장한다() {
        products.put("APN2-BoxUsage:twice.large", List.of(
            product("Hrs", "0,Inf,0.1"), product("Hrs", "0,Inf,0.2")));

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonEC2", "APN2-BoxUsage:nope.large", "RunInstances", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:twice.large", "RunInstances", 730)));

        assertThat(prices).containsOnly(LinePrice.unpriced("AWS Price List 에 온디맨드 단가가 없습니다."));
        ArgumentCaptor<UnitPrice> saved = ArgumentCaptor.forClass(UnitPrice.class);
        verify(repository, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(price -> assertThat(price.hasPrice()).isFalse());
    }

    @Test
    void 가격_없음은_하루_동안만_믿고_다시_묻는다() {
        cachedMissing("APN2-BoxUsage:new.large", Duration.ofHours(23));
        cachedMissing("APN2-BoxUsage:newer.large", AwsPricingCalculator.MISSING_PRICE_TTL.plusMinutes(1));
        products.put("APN2-BoxUsage:newer.large", List.of(product("Hrs", "0,Inf,0.1")));

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonEC2", "APN2-BoxUsage:new.large", "RunInstances", 730),
            new UsageLine("AmazonEC2", "APN2-BoxUsage:newer.large", "RunInstances", 730)));

        assertThat(prices).containsExactly(
            LinePrice.unpriced("AWS Price List 에 온디맨드 단가가 없습니다."),
            LinePrice.priced(new BigDecimal("73.00")));
        assertThat(sentRequest().filters().getFirst().value()).isEqualTo("APN2-BoxUsage:newer.large");
    }

    @Test
    void 이름_후보_중_단가가_있는_첫_후보를_쓴다() {
        products.put("APN2-InstanceUsage:db.r6g.xl", List.of(product("Hrs", "0,Inf,0.5100000000")));

        List<LinePrice> prices = calculator.price(List.of(
            new UsageLine("AmazonRDS", List.of("APN2-InstanceUsage:db.r6g.xlarge", "APN2-InstanceUsage:db.r6g.xl"),
                "CreateDBInstance:0002", 730),
            new UsageLine("AmazonRDS", List.of("APN2-InstanceUsage:db.x.xlarge", "APN2-InstanceUsage:db.x.xl"),
                "CreateDBInstance:0002", 730)));

        assertThat(prices).containsExactly(
            LinePrice.priced(new BigDecimal("372.30")),
            LinePrice.unpriced("AWS Price List 에 온디맨드 단가가 없습니다."));
    }

    @Test
    void 한_번의_계산에서_같은_단가는_한_번만_찾는다() {
        products.put("APN2-BoxUsage:t3.micro", List.of(product("Hrs", "0,Inf,0.0130000000")));

        List<LinePrice> prices = calculator.price(IntStream.range(0, 26).mapToObj(i -> T3_MICRO).toList());

        assertThat(prices).hasSize(26).containsOnly(LinePrice.priced(new BigDecimal("9.49")));
        verify(client, times(1)).getProducts(any(GetProductsRequest.class));
        verify(repository, times(1)).findByServiceCodeAndUsageTypeAndOperation(anyString(), anyString(), anyString());
    }

    @Test
    void 저장이_실패해도_받아_온_단가로_계산한다() {
        products.put("APN2-BoxUsage:t3.micro", List.of(product("Hrs", "0,Inf,0.0130000000")));
        when(repository.saveAndFlush(any(UnitPrice.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(calculator.price(List.of(T3_MICRO))).containsExactly(LinePrice.priced(new BigDecimal("9.49")));
    }

    @Test
    void 줄이_없으면_아무것도_부르지_않는다() {
        assertThat(calculator.price(List.of())).isEmpty();

        verify(client, never()).getProducts(any(GetProductsRequest.class));
        verify(repository, never()).findByServiceCodeAndUsageTypeAndOperation(anyString(), anyString(), anyString());
    }

    private UnitPrice cached(String serviceCode, String usageType, String operation, String hourly, Duration age) {
        return cached(serviceCode, usageType, operation,
            List.of(new Tier(BigDecimal.ZERO, null, new BigDecimal(hourly))), age);
    }

    private void cachedMissing(String usageType, Duration age) {
        cached("AmazonEC2", usageType, "RunInstances", List.of(), age);
    }

    private UnitPrice cached(String serviceCode, String usageType, String operation, List<Tier> tiers, Duration age) {
        UnitPrice price = UnitPrice.builder().id(1L).serviceCode(serviceCode).usageType(usageType)
            .operation(operation).unit("Hrs").tiers(tiers).fetchedAt(Instant.now().minus(age)).build();
        when(repository.findByServiceCodeAndUsageTypeAndOperation(serviceCode, usageType, operation))
            .thenReturn(Optional.of(price));
        return price;
    }

    private GetProductsRequest sentRequest() {
        ArgumentCaptor<GetProductsRequest> request = ArgumentCaptor.forClass(GetProductsRequest.class);
        verify(client).getProducts(request.capture());
        return request.getValue();
    }

    private UnitPrice savedPrice() {
        ArgumentCaptor<UnitPrice> saved = ArgumentCaptor.forClass(UnitPrice.class);
        verify(repository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private static String filterValue(GetProductsRequest request, String field) {
        return request.filters().stream().filter(filter -> filter.field().equals(field))
            .map(Filter::value).findFirst().orElseThrow();
    }

    // tiers: "beginRange,endRange,USD"
    static String product(String unit, String... tiers) {
        StringBuilder dimensions = new StringBuilder();
        for (int i = 0; i < tiers.length; i++) {
            String[] t = tiers[i].split(",");
            dimensions.append(i == 0 ? "" : ",").append("""
                "SKU.TERM.R%d": {"unit": "%s", "beginRange": "%s", "endRange": "%s", "pricePerUnit": {"USD": "%s"}}
                """.formatted(i, unit, t[0], t[1], t[2]));
        }
        return """
            {"product": {"sku": "SKU", "attributes": {}},
             "terms": {"OnDemand": {"SKU.TERM": {"priceDimensions": {%s}}},
                       "Reserved": {"SKU.RI": {"priceDimensions": {}}}}}
            """.formatted(dimensions);
    }

    private static AwsServiceException serviceException(String code, int status) {
        return AwsServiceException.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
            .statusCode(status)
            .build();
    }
}
