package com.example.kkaribackend.global.aws;

import com.example.kkaribackend.domain.pricing.domain.UnitPrice;
import com.example.kkaribackend.domain.pricing.domain.UnitPrice.Tier;
import com.example.kkaribackend.domain.pricing.domain.repository.UnitPriceRepository;
import com.example.kkaribackend.global.aws.AwsUnitPriceCatalog.LinePrice;
import com.example.kkaribackend.global.aws.AwsUnitPriceCatalog.UsageLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@SpringBootTest
class AwsUnitPriceCatalogConcurrencyTest {
    private static final List<UsageLine> S3_LINE = List.of(
        new UsageLine("AmazonS3", "APN2-TimedStorage-ByteHrs", "", 600000));

    @Autowired
    private UnitPriceRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void 같은_단가를_동시에_처음_저장해도_둘_다_계산되고_한_행만_남는다() throws Exception {
        // 두 요청이 모두 DB 에서 못 찾고 Price List 응답을 받은 뒤에야 저장하게 맞춘다
        CyclicBarrier bothFetched = new CyclicBarrier(2);
        PricingClient client = mock(PricingClient.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            bothFetched.await(5, TimeUnit.SECONDS);
            return GetProductsResponse.builder().priceList(AwsUnitPriceCatalogTest.product("GB-Mo",
                "51200,512000,0.0240000000", "0,51200,0.0250000000", "512000,Inf,0.0230000000")).build();
        }).when(client).getProducts(any(GetProductsRequest.class));
        AwsUnitPriceCatalog catalog = new AwsUnitPriceCatalog(client, repository);

        CompletableFuture<List<LinePrice>> a = CompletableFuture.supplyAsync(() -> catalog.price(S3_LINE));
        CompletableFuture<List<LinePrice>> b = CompletableFuture.supplyAsync(() -> catalog.price(S3_LINE));

        LinePrice expected = LinePrice.priced("APN2-TimedStorage-ByteHrs", new BigDecimal("14363.20"));
        assertThat(a.get(10, TimeUnit.SECONDS)).containsExactly(expected);
        assertThat(b.get(10, TimeUnit.SECONDS)).containsExactly(expected);
        List<UnitPrice> rows = repository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getTiers()).containsExactlyInAnyOrder(
            new Tier(new BigDecimal("51200"), new BigDecimal("512000"), new BigDecimal("0.0240000000")),
            new Tier(new BigDecimal("0"), new BigDecimal("51200"), new BigDecimal("0.0250000000")),
            new Tier(new BigDecimal("512000"), null, new BigDecimal("0.0230000000")));
        assertThat(catalog.price(S3_LINE)).containsExactly(expected);
    }
}
