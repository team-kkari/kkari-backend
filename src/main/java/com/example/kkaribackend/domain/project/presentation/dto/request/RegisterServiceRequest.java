package com.example.kkaribackend.domain.project.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RegisterServiceRequest(
    @NotBlank(message = "서비스명은 비어있을 수 없습니다.")
    @Size(max = 100, message = "서비스명은 100자 이하여야 합니다.")
    String serviceName,

    @NotNull(message = "답변은 비어있을 수 없습니다.")
    @Size(max = 50, message = "답변은 50개 이하여야 합니다.")
    Map<@NotBlank(message = "질문은 비어있을 수 없습니다.")
        @Size(max = 200, message = "질문은 200자 이하여야 합니다.") String,
        @NotNull(message = "답은 null일 수 없습니다.")
        @Size(max = 2000, message = "답은 2000자 이하여야 합니다.") String> answers,

    @NotNull(message = "담당 엔지니어는 비어있을 수 없습니다.")
    Long engineerId
) {
}
