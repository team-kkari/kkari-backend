package com.example.kkaribackend.global.aws;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

final class AwsFailures {
    private AwsFailures() {
    }

    // 결과에 저장할 짧은 실패 이유. AWS 메시지에는 ARN·계정 ID 가 들어 있을 수 있어 에러 코드만 남긴다
    static String reason(RuntimeException e) {
        if (e instanceof AwsServiceException ase) {
            String code = ase.awsErrorDetails() == null ? null : ase.awsErrorDetails().errorCode();
            return (code == null ? "AWS 오류" : code) + " (HTTP " + ase.statusCode() + ")";
        }
        if (e instanceof SdkClientException) {
            return "AWS 호출 실패(네트워크 또는 클라이언트 오류)";
        }
        return e.getClass().getSimpleName();
    }
}
