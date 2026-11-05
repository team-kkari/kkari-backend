package com.example.kkaribackend.domain.analysis.service;

import com.example.kkaribackend.domain.analysis.domain.AnalysisJob;
import com.example.kkaribackend.domain.analysis.domain.repository.AnalysisJobRepository;
import com.example.kkaribackend.domain.analysis.presentation.dto.response.DiscoveryResponse;
import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.aws.dto.InfraSnapshot;
import com.example.kkaribackend.global.terraform.TerraformStateParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Service
public class UploadTerraformStateService {
    private final ProjectFacade projectFacade;
    private final TerraformStateParser terraformStateParser;
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    // 가격 조회가 외부 호출이라 트랜잭션으로 묶지 않는다
    // 형식이 틀리면 아무것도 저장하지 않으므로 이전에 성공한 결과가 그대로 유지된다. 원본 파일은 저장하지 않는다
    public DiscoveryResponse execute(Long projectId, MultipartFile file) {
        Project project = projectFacade.getAssignedProject(projectId);
        InfraSnapshot snapshot = terraformStateParser.parse(file);
        AnalysisJob job = analysisJobRepository.save(
            AnalysisJob.succeeded(project, objectMapper.writeValueAsString(snapshot)));
        return new DiscoveryResponse(job.getId(), job.getFinishedAt(), snapshot);
    }
}
