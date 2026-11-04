package com.example.kkaribackend.domain.analysis.presentation;

import com.example.kkaribackend.domain.analysis.presentation.dto.response.AnalysisJobResponse;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.DiscoveryResponse;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.StartAnalysisResponse;
import com.example.kkaribackend.domain.analysis.service.QueryAnalysisJobService;
import com.example.kkaribackend.domain.analysis.service.QueryDiscoveryService;
import com.example.kkaribackend.domain.analysis.service.StartAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Infra Analysis")
@RequiredArgsConstructor
@RequestMapping("/v1")
@RestController
public class AnalysisController {
    private final StartAnalysisService startAnalysisService;
    private final QueryAnalysisJobService queryAnalysisJobService;
    private final QueryDiscoveryService queryDiscoveryService;

    @Operation(summary = "인프라 분석 시작",
        description = "고객사 AWS의 리소스 구조, 14일 사용률, 월 예상 비용을 비동기로 수집한다. "
            + "이미 대기·실행 중인 분석이 있으면 그 작업 ID를 돌려준다. 진행 상황은 GET /v1/jobs/{jobId}로 확인.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/projects/{projectId}/analysis")
    public StartAnalysisResponse start(@PathVariable Long projectId) {
        return startAnalysisService.execute(projectId);
    }

    @Operation(summary = "분석 작업 상태 조회", description = "상태(QUEUED/RUNNING/SUCCEEDED/FAILED), 진행 단계, 실패 이유. 결과는 discovery로 조회.")
    @GetMapping("/jobs/{jobId}")
    public AnalysisJobResponse getJob(@PathVariable Long jobId) {
        return queryAnalysisJobService.execute(jobId);
    }

    @Operation(summary = "최근 분석 결과 조회", description = "마지막으로 성공한 분석 결과. 비용은 온디맨드 정가 기준 추정치(USD).")
    @GetMapping("/projects/{projectId}/discovery")
    public DiscoveryResponse getDiscovery(@PathVariable Long projectId) {
        return queryDiscoveryService.execute(projectId);
    }
}
