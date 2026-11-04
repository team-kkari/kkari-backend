package com.example.kkaribackend.domain.analysis.service;

// 작업 저장이 커밋된 뒤 비동기 실행을 시작하기 위한 이벤트
public record AnalysisRequestedEvent(Long jobId) {
}
