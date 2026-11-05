package com.example.kkaribackend.domain.project.domain;

public enum ProjectStage {
    RECEIVED,    // 정보 입력
    ANALYZED,    // 분석 완료
    RECOMMENDED; // 추천 완료

    public boolean isAfter(ProjectStage other) {
        return compareTo(other) > 0;
    }
}
