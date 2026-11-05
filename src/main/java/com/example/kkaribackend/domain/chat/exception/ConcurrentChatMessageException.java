package com.example.kkaribackend.domain.chat.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ConcurrentChatMessageException extends KkariException {
    public ConcurrentChatMessageException() {
        super(ChatErrorCode.CONCURRENT_CHAT_MESSAGE);
    }
}
