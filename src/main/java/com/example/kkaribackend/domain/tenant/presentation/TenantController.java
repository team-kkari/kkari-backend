package com.example.kkaribackend.domain.tenant.presentation;

import com.example.kkaribackend.domain.engagement.domain.EngagementStage;
import com.example.kkaribackend.domain.tenant.presentation.dto.request.CreateTenantRequest;
import com.example.kkaribackend.domain.tenant.presentation.dto.response.CreateTenantResponse;
import com.example.kkaribackend.domain.tenant.presentation.dto.response.TenantListResponse;
import com.example.kkaribackend.domain.tenant.service.CreateTenantService;
import com.example.kkaribackend.domain.tenant.service.QueryTenantListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tenant")
@RequiredArgsConstructor
@RequestMapping("/v1/tenants")
@RestController
public class TenantController {
    private final CreateTenantService createTenantService;
    private final QueryTenantListService queryTenantListService;

    @Operation(summary = "고객사 등록", description = "등록한 엔지니어가 담당자가 되고, 첫 의뢰가 '의뢰 접수' 단계로 같이 생성된다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateTenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        return createTenantService.execute(request);
    }

    @Operation(summary = "고객사 목록", description = "내가 담당하는 고객사와 최근 의뢰의 진행 단계. 최근 수정 순.")
    @GetMapping
    public List<TenantListResponse> list(@RequestParam(required = false) EngagementStage stage) {
        return queryTenantListService.execute(stage);
    }
}
