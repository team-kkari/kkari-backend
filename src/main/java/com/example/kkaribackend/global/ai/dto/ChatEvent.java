package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.JsonNode;

// 스트림 순서: Sources → Delta 여러 번 → Done 또는 Failure. 본문의 [n] 은 sources[].n 이다
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChatEvent.Sources.class, name = "sources"),
    @JsonSubTypes.Type(value = ChatEvent.Delta.class, name = "delta"),
    @JsonSubTypes.Type(value = ChatEvent.Done.class, name = "done"),
    @JsonSubTypes.Type(value = ChatEvent.Failure.class, name = "error")
})
public sealed interface ChatEvent {

    record Sources(JsonNode sources) implements ChatEvent {
    }

    record Delta(String text) implements ChatEvent {
    }

    // refused: 모델이 답변을 거절했다
    record Done(boolean refused) implements ChatEvent {
    }

    // 스트림 도중 실패. 답변 일부가 이미 나갔으므로 다시 요청하지 않는다
    record Failure(String code, String message, boolean retryable) implements ChatEvent {
    }
}
