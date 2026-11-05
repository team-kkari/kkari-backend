package com.example.kkaribackend.domain.project.domain;

public enum AwsConnectionStatus {
    NOT_CONNECTED,      // Role ARN 미등록
    UNTESTED,           // Role ARN 등록, 연결 테스트 전
    CONNECTED,          // 연결 성공
    TRUST_POLICY_ERROR, // AssumeRole 거부 (신뢰 정책·External ID·Role 확인 필요)
    PERMISSION_DENIED   // AssumeRole은 성공했지만 읽기 권한 부족
}
