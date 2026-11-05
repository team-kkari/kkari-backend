package com.example.kkaribackend.domain.terraform.service;

import com.example.kkaribackend.domain.project.domain.Project;
import com.example.kkaribackend.domain.project.facade.ProjectFacade;
import com.example.kkaribackend.global.terraform.TerraformStateParser;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
public class UploadTerraformService {
    private final ProjectFacade projectFacade;
    private final TerraformStateParser terraformStateParser;

    // 형식이 틀리면 예외로 끝나 이전에 올린 결과가 그대로 남는다
    @Transactional
    public TerraformInfra execute(Long projectId, MultipartFile file) {
        Project project = projectFacade.getAssignedProject(projectId);
        TerraformInfra terraform = terraformStateParser.parse(file);
        project.updateTerraform(terraform);
        return terraform;
    }
}
