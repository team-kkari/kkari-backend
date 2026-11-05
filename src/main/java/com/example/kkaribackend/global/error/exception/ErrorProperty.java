package com.example.kkaribackend.global.error.exception;

import org.springframework.http.HttpStatus;

public interface ErrorProperty {
    String getMessage();
    HttpStatus getStatus();
    String getCode();
}
