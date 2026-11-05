package com.example.kkaribackend.domain.project.presentation;

import com.example.kkaribackend.domain.project.domain.ProjectStage;
import com.example.kkaribackend.domain.project.presentation.dto.request.CreateProjectRequest;
import com.example.kkaribackend.domain.project.presentation.dto.response.CreateProjectResponse;
import com.example.kkaribackend.domain.project.presentation.dto.response.ProjectDetailResponse;
import com.example.kkaribackend.domain.project.presentation.dto.response.ProjectListResponse;
import com.example.kkaribackend.domain.project.service.CreateProjectService;
import com.example.kkaribackend.domain.project.service.QueryProjectListService;
import com.example.kkaribackend.domain.project.service.QueryProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Project")
@RequiredArgsConstructor
@RequestMapping("/v1/projects")
@RestController
public class ProjectController {
    private final CreateProjectService createProjectService;
    private final QueryProjectListService queryProjectListService;
    private final QueryProjectService queryProjectService;

    @Operation(summary = "프로젝트 생성", description = "만든 엔지니어가 담당하고, 진행 단계는 RECEIVED(정보 입력)로 시작한다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return createProjectService.execute(request);
    }

    @Operation(summary = "내 프로젝트 목록", description = "진행 단계로 필터, 최근 수정 순.")
    @GetMapping
    public List<ProjectListResponse> list(@RequestParam(required = false) ProjectStage stage) {
        return queryProjectListService.execute(stage);
    }

    @Operation(summary = "프로젝트 상세", description = "고객이 등록한 서비스면 serviceName·answers가 있고, 직접 만든 프로젝트는 둘 다 null.")
    @GetMapping("/{projectId}")
    public ProjectDetailResponse get(@PathVariable Long projectId) {
        return queryProjectService.execute(projectId);
    }
}
