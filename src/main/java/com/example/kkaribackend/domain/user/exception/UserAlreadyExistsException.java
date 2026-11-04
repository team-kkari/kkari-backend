package com.example.kkaribackend.domain.user.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class UserAlreadyExistsException extends KkariException {
    public UserAlreadyExistsException() {
        super(UserErrorCode.USER_ALREADY_EXISTS);
    }
}
