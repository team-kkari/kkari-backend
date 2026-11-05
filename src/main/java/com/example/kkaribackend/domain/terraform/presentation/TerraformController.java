package com.example.kkaribackend.domain.terraform.presentation;

import com.example.kkaribackend.domain.terraform.service.UploadTerraformService;
import com.example.kkaribackend.global.terraform.dto.TerraformInfra;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Terraform")
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/terraform")
@RestController
public class TerraformController {
    private final UploadTerraformService uploadTerraformService;

    @Operation(summary = "Terraform state 파일 업로드 (선택)",
        description = "terraform.tfstate(state 버전 4, 최대 5MB)에서 리소스 구조를 읽어 프로젝트에 저장한다. 다시 올리면 교체한다. "
            + "AWS 수집 결과와 같은 모양·ID로 discovery의 terraform에 함께 담긴다. 사용률·비용은 계산하지 않는다. "
            + "파일은 실행하거나 저장하지 않는다. 형식이 틀리면 400이고 이전 결과는 유지된다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TerraformInfra upload(@PathVariable Long projectId, @RequestPart MultipartFile file) {
        return uploadTerraformService.execute(projectId, file);
    }
}
