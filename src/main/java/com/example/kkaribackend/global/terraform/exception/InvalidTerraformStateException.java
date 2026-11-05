package com.example.kkaribackend.global.terraform.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

// 이유만 담고 파일 내용은 넣지 않는다
public class InvalidTerraformStateException extends KkariException {
    public InvalidTerraformStateException(String reason) {
        super(TerraformErrorCode.INVALID_TERRAFORM_STATE, TerraformErrorCode.INVALID_TERRAFORM_STATE.getMessage() + " " + reason);
    }
}
