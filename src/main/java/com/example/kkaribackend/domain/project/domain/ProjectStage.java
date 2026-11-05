package com.example.kkaribackend.domain.project.domain;

// 선언 순서가 진행 순서다. Project 는 이 순서로만 앞으로 나아간다
public enum ProjectStage {
    RECEIVED,   // 정보 입력
    ANALYZED,   // 분석 완료
    RECOMMENDED // 추천 완료
}
