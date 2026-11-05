package com.example.kkaribackend.domain.awsconnection.presentation.dto.response;

import com.example.kkaribackend.domain.project.domain.AwsConnectionStatus;
import com.example.kkaribackend.domain.project.domain.Project;
import lombok.Builder;

@Builder
public record AwsConnectionResponse(
    String accountId,
    String externalId,
    String roleArn,
    AwsConnectionStatus status,
    // 고객사 Role 신뢰 정책에 그대로 붙여넣을 JSON
    String trustPolicy
) {
    private static final String TRUST_POLICY = """
        {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":"arn:aws:iam::%s:root"},\
        "Action":"sts:AssumeRole","Condition":{"StringEquals":{"sts:ExternalId":"%s"}}}]}""";

    public static AwsConnectionResponse of(Project project, String accountId) {
        return AwsConnectionResponse.builder()
            .accountId(accountId)
            .externalId(project.getExternalId())
            .roleArn(project.getRoleArn())
            .status(project.getAwsConnectionStatus())
            .trustPolicy(TRUST_POLICY.formatted(accountId, project.getExternalId()))
            .build();
    }
}
