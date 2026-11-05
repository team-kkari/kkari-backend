package com.example.kkaribackend.global.error.response;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.Builder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ValidErrorResponse (
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    List<FieldErrorDetail> errors
    ) {
    public static ValidErrorResponse of(ErrorProperty errorCode, BindingResult br) {
        return ValidErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.getStatus().value())
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .errors(br.getFieldErrors().stream().map(FieldErrorDetail::from).toList())
            .build();
    }

    @Builder
    public record FieldErrorDetail(
        String field,
        String value,
        String reason
    ) {
        public static FieldErrorDetail from(FieldError fieldError) {
            return FieldErrorDetail.builder()
                .field(fieldError.getField())
                .value(isPrivateField(fieldError) || fieldError.getRejectedValue() == null
                    ? null : fieldError.getRejectedValue().toString())
                .reason(fieldError.getDefaultMessage()).build();
        }

        private static boolean isPrivateField(FieldError error) {
            return error.getField().equals("password") || error.getObjectName().equals("sendChatMessageRequestDto");
        }
    }
}
