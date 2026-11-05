package com.example.kkaribackend.domain.architecture.presentation;

import com.example.kkaribackend.domain.architecture.presentation.dto.request.StartArchitectureRequest;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureResponse;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.StartArchitectureResponse;
import com.example.kkaribackend.domain.architecture.service.QueryArchitectureService;
import com.example.kkaribackend.domain.architecture.service.StartArchitectureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Architecture")
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/architectures")
@RestController
public class ArchitectureController {
    private final StartArchitectureService startArchitectureService;
    private final QueryArchitectureService queryArchitectureService;

    @Operation(summary = "추천 아키텍처 생성 시작",
        description = "최근 인프라 분석 결과·Terraform·요구사항으로 AI 추천 아키텍처를 비동기로 생성한다(1~2.5분). "
            + "count는 추천 개수(1~5, 기본 5)이고 본문은 생략할 수 있다. 성공한 인프라 분석이 없으면 409(AR001). "
            + "이미 대기·실행 중인 생성 작업이 있으면 그 작업 ID를 돌려준다. 진행 상황은 GET /v1/jobs/{jobId}로 확인.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping
    public StartArchitectureResponse start(@PathVariable Long projectId,
                                           @Valid @RequestBody(required = false) StartArchitectureRequest request) {
        return startArchitectureService.execute(projectId, request);
    }

    @Operation(summary = "최근 추천 아키텍처 조회",
        description = "마지막으로 성공한 생성 결과. result는 AI 서버 응답 JSON 그대로다. 없으면 404(AR002).")
    @GetMapping
    public ArchitectureResponse get(@PathVariable Long projectId) {
        return queryArchitectureService.execute(projectId);
    }
}
