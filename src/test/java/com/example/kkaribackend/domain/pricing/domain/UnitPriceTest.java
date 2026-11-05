package com.example.kkaribackend.domain.pricing.domain;

import com.example.kkaribackend.domain.pricing.domain.UnitPrice.Tier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnitPriceTest {
    // AWS Price List 의 서울 S3 Standard 구간. 순서가 섞여 와도 결과는 같다
    private static final UnitPrice S3_STANDARD = unitPrice(
        tier("51200", "512000", "0.024"),
        tier("0", "51200", "0.025"),
        tier("512000", null, "0.023"));

    @ParameterizedTest(name = "{0} GB -> {1} $")
    @CsvSource({
        "0, 0.00",
        "50, 1.25",
        "51200, 1280.00",
        "600000, 14363.20"})
    void 사용량을_구간마다_나눠_그_구간_단가로_더한다(double gb, String expected) {
        // 600000 GB = 51200 × 0.025 + 460800 × 0.024 + 88000 × 0.023
        assertThat(S3_STANDARD.cost(gb)).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "{0} -> {1} $")
    @CsvSource({
        "0.0225, 16.43",
        "0.0130, 9.49",
        "0.0222, 16.21"})
    void 시간_요금은_730시간을_곱해_소수_둘째_자리에서_반올림한다(String hourly, String expected) {
        assertThat(unitPrice(tier("0", null, hourly)).cost(730)).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "{0} GB -> {1} $")
    @CsvSource({
        "20, 0.00",
        "100, 20.31"})
    void 무료_구간은_0원으로_계산한다(double gb, String expected) {
        UnitPrice dynamoStorage = unitPrice(tier("0", "25", "0"), tier("25", null, "0.27075"));

        assertThat(dynamoStorage.cost(gb)).isEqualTo(new BigDecimal(expected));
    }

    private static UnitPrice unitPrice(Tier... tiers) {
        return UnitPrice.builder().serviceCode("AmazonS3").usageType("APN2-TimedStorage-ByteHrs").operation("")
            .unit("GB-Mo").tiers(List.of(tiers)).fetchedAt(Instant.now()).build();
    }

    private static Tier tier(String begin, String end, String usd) {
        return new Tier(new BigDecimal(begin), end == null ? null : new BigDecimal(end), new BigDecimal(usd));
    }
}
