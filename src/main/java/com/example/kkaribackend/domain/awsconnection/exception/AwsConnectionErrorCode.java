package com.example.kkaribackend.domain.awsconnection.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AwsConnectionErrorCode implements ErrorProperty {
    ROLE_ARN_NOT_REGISTERED(HttpStatus.CONFLICT, "W001", "Role ARN을 먼저 등록해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
