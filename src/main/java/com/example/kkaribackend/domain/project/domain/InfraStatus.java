package com.example.kkaribackend.domain.project.domain;

public enum InfraStatus {
    EXISTING, // 이미 AWS 인프라를 구축함. 계정 연결로 정보를 가져오고 가치·예산만 묻는다
    NONE      // 아직 인프라가 없음(신규 구축). AWS 연결 없이 질문으로 정보를 얻는다
}
