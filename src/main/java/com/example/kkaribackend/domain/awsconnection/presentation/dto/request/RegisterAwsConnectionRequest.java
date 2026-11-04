package com.example.kkaribackend.domain.awsconnection.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterAwsConnectionRequest(
    @NotBlank(message = "Role ARN은 비어있을 수 없습니다.")
    @Pattern(regexp = "^arn:aws:iam::\\d{12}:role/[\\w+=,.@/-]{1,512}$", message = "Role ARN 형식이 올바르지 않습니다.")
    String roleArn
) {
}
