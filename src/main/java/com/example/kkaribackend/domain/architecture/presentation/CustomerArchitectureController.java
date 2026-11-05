package com.example.kkaribackend.domain.architecture.presentation;

import com.example.kkaribackend.domain.architecture.exception.ArchitectureResultNotFoundException;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureErrorResponseDto;
import com.example.kkaribackend.domain.architecture.presentation.dto.response.ArchitectureResponse;
import com.example.kkaribackend.domain.architecture.service.QueryCustomerArchitectureService;
import com.example.kkaribackend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Customer")
@RequiredArgsConstructor
@RequestMapping("/v1/customer/services/{serviceId}/architectures")
@RestController
public class CustomerArchitectureController {
    private final QueryCustomerArchitectureService queryCustomerArchitectureService;

    @Operation(summary = "내 서비스 추천 아키텍처 조회",
        description = "엔지니어 화면과 같은 응답이다(GET /v1/projects/{projectId}/architectures). "
            + "생성이 성공하면 바로 볼 수 있다. 본인이 등록한 서비스가 아니면 403.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ArchitectureResponse.class)))
    @ApiResponse(responseCode = "404", description = "AR002: 성공 결과 없음. P001: 서비스 없음(기본 오류 응답)",
        content = @Content(schema = @Schema(anyOf = {ArchitectureErrorResponseDto.class, ErrorResponse.class})))
    @GetMapping
    public ArchitectureResponse get(@PathVariable Long serviceId) {
        return queryCustomerArchitectureService.execute(serviceId);
    }

    @ExceptionHandler(ArchitectureResultNotFoundException.class)
    public ResponseEntity<ArchitectureErrorResponseDto> handleResultNotFound(ArchitectureResultNotFoundException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus()).body(ArchitectureErrorResponseDto.from(exception));
    }
}
