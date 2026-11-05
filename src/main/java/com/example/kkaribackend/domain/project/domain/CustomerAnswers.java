package com.example.kkaribackend.domain.project.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.TreeMap;

/**
 * AI 입력에 붙이는 고객 답변 블록. 한 줄에 "질문: 답" 하나.
 * DB 가 키 순서를 보존하지 않아 질문 순으로 정렬해, 같은 답변이면 같은 문자열이 되게 한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CustomerAnswers {
    private static final String TITLE = "[고객 답변]";

    public static String describe(Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(TITLE);
        new TreeMap<>(answers).forEach((question, answer) ->
            text.append('\n').append(oneLine(question)).append(": ").append(oneLine(answer)));
        return text.toString();
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\R+", " ");
    }
}
