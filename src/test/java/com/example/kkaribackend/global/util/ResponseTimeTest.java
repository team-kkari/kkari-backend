package com.example.kkaribackend.global.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTimeTest {
    @Test
    void 서버_로컬_시각에_UTC를_단정하지_않고_실제_offset을_붙인다() {
        TimeZone original = TimeZone.getDefault();
        try {
            LocalDateTime local = LocalDateTime.of(2026, 11, 5, 18, 0);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            assertThat(ResponseTime.withOffset(local).getOffset()).isEqualTo(ZoneOffset.ofHours(9));
            assertThat(ResponseTime.withOffset(local).toLocalDateTime()).isEqualTo(local);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertThat(ResponseTime.withOffset(local).getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(ResponseTime.withOffset(null)).isNull();
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
