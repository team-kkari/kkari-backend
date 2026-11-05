package com.example.kkaribackend.domain.user.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class EngineerNotFoundException extends KkariException {
    public EngineerNotFoundException() {
        super(UserErrorCode.ENGINEER_NOT_FOUND);
    }
}
