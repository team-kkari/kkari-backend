package com.example.kkaribackend.domain.chat.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ChatMessageIdConflictException extends KkariException {
    public ChatMessageIdConflictException() {
        super(ChatErrorCode.CHAT_MESSAGE_ID_CONFLICT);
    }
}
