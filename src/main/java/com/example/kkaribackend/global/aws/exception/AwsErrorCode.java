package com.example.kkaribackend.global.aws.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AwsErrorCode implements ErrorProperty {
    AWS_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AW001", "AWS 요청에 실패했습니다."),
    AWS_CREDENTIALS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AW002", "서버의 AWS 자격증명을 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
