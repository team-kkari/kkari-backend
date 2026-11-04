package com.example.kkaribackend.domain.project.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlank(message = "고객사명은 비어있을 수 없습니다.")
    @Size(max = 100, message = "고객사명은 100자 이하여야 합니다.")
    String companyName,

    @NotBlank(message = "담당자는 비어있을 수 없습니다.")
    @Size(max = 50, message = "담당자는 50자 이하여야 합니다.")
    String contactName,

    @Size(max = 1000, message = "의뢰 내용은 1000자 이하여야 합니다.")
    String memo
) {
}
