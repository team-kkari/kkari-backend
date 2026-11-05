package com.example.kkaribackend.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResponseTime {
    // 기존 DB 값은 서버 로컬 시각이다. UTC로 단정하지 않고 실제 서버 시간대의 offset을 붙인다.
    public static OffsetDateTime withOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
