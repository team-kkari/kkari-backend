package com.example.kkaribackend.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextTruncator {
    private static final String OMITTED = "\n…(생략)";

    public static String truncate(CharSequence text, int max) {
        if (text.length() <= max) {
            return text.toString();
        }
        int end = max - OMITTED.length();
        // 보조 평면 문자를 중간에서 자르지 않는다
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.subSequence(0, end) + OMITTED;
    }
}
