package com.example.kkaribackend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// errors 는 VALIDATION_ERROR 일 때만 온다
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiErrorResponse(String code, String message, boolean retryable, List<FieldError> errors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldError(String field, String message) {
    }
}
