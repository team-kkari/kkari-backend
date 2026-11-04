package com.example.kkaribackend.domain.requirement.presentation;

import com.example.kkaribackend.domain.project.domain.Requirements;
import com.example.kkaribackend.domain.requirement.presentation.dto.request.SaveRequirementsRequest;
import com.example.kkaribackend.domain.requirement.service.QueryRequirementsService;
import com.example.kkaribackend.domain.requirement.service.SaveRequirementsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Requirement")
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/requirements")
@RestController
public class RequirementController {
    private final QueryRequirementsService queryRequirementsService;
    private final SaveRequirementsService saveRequirementsService;

    @Operation(summary = "요구사항 조회", description = "저장된 요구사항을 돌려준다. 아직 입력하지 않았으면 404(R001).")
    @GetMapping
    public Requirements get(@PathVariable Long projectId) {
        return queryRequirementsService.execute(projectId);
    }

    @Operation(summary = "요구사항 저장", description = "요구사항 전체를 교체한다. 우선순위는 앞에 둘수록 중요하다.")
    @PutMapping
    public Requirements save(@PathVariable Long projectId,
                             @Valid @RequestBody SaveRequirementsRequest request) {
        return saveRequirementsService.execute(projectId, request);
    }
}
