package com.example.kkaribackend.domain.project.presentation.dto.request;

import com.example.kkaribackend.domain.project.domain.InfraStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlank(message = "고객사명은 비어있을 수 없습니다.")
    @Size(max = 100, message = "고객사명은 100자 이하여야 합니다.")
    String companyName,

    @NotBlank(message = "담당자는 비어있을 수 없습니다.")
    @Size(max = 50, message = "담당자는 50자 이하여야 합니다.")
    String contactName,

    @Schema(description = "인프라 상태. EXISTING=이미 구축함, NONE=아직 없음(신규 구축). 생략하면 EXISTING")
    InfraStatus infraStatus
) {
}
