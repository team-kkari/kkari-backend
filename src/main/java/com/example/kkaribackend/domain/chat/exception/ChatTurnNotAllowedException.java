package com.example.kkaribackend.domain.chat.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class ChatTurnNotAllowedException extends KkariException {
    public ChatTurnNotAllowedException() {
        super(ChatErrorCode.CHAT_TURN_NOT_ALLOWED);
    }
}
