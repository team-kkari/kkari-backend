package com.example.kkaribackend.domain.project.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.domain.ProjectStage;
import lombok.Builder;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import com.example.kkaribackend.global.util.ResponseTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

@Builder
public record ProjectDetailResponse(
    Long projectId,
    String serviceName,
    String companyName,
    String contactName,
    Map<String, String> answers,
    ProjectStage stage,
    AwsConnectionStatus awsConnectionStatus,
    @Schema(description = "Terraform 파싱 결과가 저장되어 있는지 여부") boolean terraformUploaded,
    @Schema(description = "저장된 Terraform 결과와 파일 메타데이터. 업로드 전에는 null") TerraformInfra terraform,
    @Schema(description = "생성 시각. ISO 8601 offset 포함") OffsetDateTime createdAt,
    @Schema(description = "최근 수정 시각. ISO 8601 offset 포함") OffsetDateTime updatedAt
) {
    public static ProjectDetailResponse from(Project project) {
        return ProjectDetailResponse.builder()
            .projectId(project.getId())
            .serviceName(project.getServiceName())
            .companyName(project.getCompanyName())
            .contactName(project.getContactName())
            .answers(project.getAnswers())
            .stage(project.getStage())
            .awsConnectionStatus(project.getAwsConnectionStatus())
            .terraformUploaded(project.getTerraform() != null)
            .terraform(project.getTerraform())
            .createdAt(ResponseTime.withOffset(project.getCreatedAt()))
            .updatedAt(ResponseTime.withOffset(project.getUpdatedAt()))
            .build();
    }
}
