package com.example.kkaribackend.domain.architecture.presentation;

import com.example.kkaribackend.domain.architecture.presentation.dto.request.StartArchitectureRequest;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureResponse;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.StartArchitectureResponse;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureErrorResponseDto;
import com.example.kkaribackend.domain.architecture.exception.ArchitectureResultNotFoundException;
import com.example.kkaribackend.domain.architecture.service.QueryArchitectureService;
import com.example.kkaribackend.domain.architecture.service.StartArchitectureService;
import com.example.kkaribackend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            + "이미 대기·실행 중인 생성 작업이 있으면 그 작업 ID를 돌려준다. 진행 상황은 GET /v1/jobs/{jobId}로 확인. "
            + "새 작업 생성 시 양수 예산의 통화가 없거나 미지원이면 400(AR003)이므로 USD/KRW로 수정해야 한다.")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = StartArchitectureResponse.class)))
    @ApiResponse(responseCode = "400", description = "C001: 입력 검증 실패. AR003: 기존 요구사항의 예산 통화 누락 또는 미지원",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping
    public StartArchitectureResponse start(@PathVariable Long projectId,
                                           @Valid @RequestBody(required = false) StartArchitectureRequest request) {
        return startArchitectureService.execute(projectId, request);
    }

    @Operation(summary = "최근 추천 아키텍처 조회",
        description = "마지막으로 성공한 생성 결과와 latestJob(가장 최근 설계 작업)을 반환한다. "
            + "result는 AI 서버 응답 JSON 그대로다. 성공 결과가 없으면 404(AR002)에도 latestJob을 포함한다. "
            + "작업을 시작한 적이 없으면 latestJob은 null이다. generatedAt은 offset을 포함한다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ArchitectureResponse.class)))
    @ApiResponse(responseCode = "404", description = "AR002: 성공 결과 없음. P001: 프로젝트 없음(기본 오류 응답)",
        content = @Content(schema = @Schema(anyOf = {ArchitectureErrorResponseDto.class, ErrorResponse.class})))
    @GetMapping
    public ArchitectureResponse get(@PathVariable Long projectId) {
        return queryArchitectureService.execute(projectId);
    }

    @ExceptionHandler(ArchitectureResultNotFoundException.class)
    public ResponseEntity<ArchitectureErrorResponseDto> handleResultNotFound(ArchitectureResultNotFoundException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus()).body(ArchitectureErrorResponseDto.from(exception));
    }
}
