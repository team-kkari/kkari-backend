package com.example.kkaribackend.domain.user.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class UserNotFoundException extends KkariException {
    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
