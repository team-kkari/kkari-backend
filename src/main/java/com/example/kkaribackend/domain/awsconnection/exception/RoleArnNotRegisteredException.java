package com.example.kkaribackend.domain.awsconnection.exception;

import com.example.kkaribackend.global.error.exception.KkariException;

public class RoleArnNotRegisteredException extends KkariException {
    public RoleArnNotRegisteredException() {
        super(AwsConnectionErrorCode.ROLE_ARN_NOT_REGISTERED);
    }
}
