package com.example.kkaribackend.global.terraform.exception;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TerraformErrorCode implements ErrorProperty {
    INVALID_TERRAFORM_STATE(HttpStatus.BAD_REQUEST, "TF001", "Terraform state 파일 형식이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
