package com.example.kkaribackend.domain.analysis.presentation;

import com.example.kkaribackend.domain.analysis.presentation.dto.response.AnalysisJobResponse;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.DiscoveryResponse;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.StartAnalysisResponse;
import com.example.kkaribackend.domain.analysis.service.QueryAnalysisJobService;
import com.example.kkaribackend.domain.analysis.service.QueryDiscoveryService;
import com.example.kkaribackend.domain.analysis.service.StartAnalysisService;
import com.example.kkaribackend.domain.analysis.service.UploadTerraformStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Infra Analysis")
@RequiredArgsConstructor
@RequestMapping("/v1")
@RestController
public class AnalysisController {
    private final StartAnalysisService startAnalysisService;
    private final QueryAnalysisJobService queryAnalysisJobService;
    private final QueryDiscoveryService queryDiscoveryService;
    private final UploadTerraformStateService uploadTerraformStateService;

    @Operation(summary = "인프라 분석 시작",
        description = "고객사 AWS의 리소스 구조, 14일 사용률, 월 예상 비용을 비동기로 수집한다. "
            + "이미 대기·실행 중인 분석이 있으면 그 작업 ID를 돌려준다(1시간이 지난 작업은 실패 처리하고 새로 시작). 진행 상황은 GET /v1/jobs/{jobId}로 확인.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/projects/{projectId}/analysis")
    public StartAnalysisResponse start(@PathVariable Long projectId) {
        return startAnalysisService.execute(projectId);
    }

    @Operation(summary = "Terraform state 파일 업로드",
        description = "AWS 연결 대신 terraform.tfstate(state 버전 4, 최대 5MB)로 리소스 구조와 월 예상 비용을 만들어 바로 돌려준다. "
            + "파일은 실행하거나 저장하지 않는다. 사용률은 알 수 없어 null. 형식이 틀리면 400이고 이전 결과는 유지된다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/projects/{projectId}/terraform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DiscoveryResponse uploadTerraform(@PathVariable Long projectId, @RequestPart MultipartFile file) {
        return uploadTerraformStateService.execute(projectId, file);
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
