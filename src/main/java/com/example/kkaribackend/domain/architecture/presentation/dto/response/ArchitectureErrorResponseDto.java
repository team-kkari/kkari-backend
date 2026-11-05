package com.example.kkaribackend.domain.architecture.presentation.dto.response;

import com.example.kkaribackend.domain.architecture.exception.ArchitectureResultNotFoundException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "성공한 설계 결과가 없을 때의 AR002 응답. latestJob으로 진행·실패 상태를 복원한다")
public record ArchitectureErrorResponseDto(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    LatestArchitectureJobResponseDto latestJob
) {
    public static ArchitectureErrorResponseDto from(ArchitectureResultNotFoundException exception) {
        var error = exception.getErrorCode();
        return new ArchitectureErrorResponseDto(LocalDateTime.now(), error.getStatus().value(), error.getCode(),
            error.getMessage(), LatestArchitectureJobResponseDto.from(exception.getLatestJob()));
    }
}
