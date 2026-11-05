package com.example.kkaribackend.domain.awsconnection.service;

import com.example.kkaribackend.domain.awsconnection.exception.RoleArnNotRegisteredException;
import com.example.kkaribackend.domain.awsconnection.presentation.dto.response.AwsConnectionResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.aws.AwsConnectionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TestAwsConnectionService {
    private final ProjectFacade projectFacade;
    private final AwsConnectionProvider awsConnectionProvider;

    @Transactional
    public AwsConnectionResponse execute(Long projectId) {
        Project project = projectFacade.getAssignedProject(projectId);
        if (project.getRoleArn() == null) {
            throw new RoleArnNotRegisteredException();
        }

        project.updateAwsConnectionStatus(awsConnectionProvider.testConnection(
            project.getRoleArn(), project.getExternalId(), "kkari-project-" + project.getId()));
        return AwsConnectionResponse.of(project, awsConnectionProvider.getAccountId());
    }
}
