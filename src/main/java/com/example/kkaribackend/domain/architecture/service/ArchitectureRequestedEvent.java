package com.example.kkaribackend.domain.architecture.service;

import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;

// 작업 저장이 커밋된 뒤 비동기 실행을 시작하기 위한 이벤트. 입력은 시작할 때 만든 것을 그대로 쓴다
public record ArchitectureRequestedEvent(Long jobId, ArchitectureRequest request) {
}
