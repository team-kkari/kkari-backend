package com.example.kkaribackend.domain.awsconnection.service;

import com.example.kkaribackend.domain.awsconnection.presentation.dto.response.AwsConnectionResponse;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.global.aws.AwsConnectionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QueryAwsConnectionService {
    private final ProjectFacade projectFacade;
    private final AwsConnectionProvider awsConnectionProvider;

    @Transactional(readOnly = true)
    public AwsConnectionResponse execute(Long projectId) {
        Project project = projectFacade.getAssignedProject(projectId);
        return AwsConnectionResponse.of(project, awsConnectionProvider.getAccountId());
    }
}
