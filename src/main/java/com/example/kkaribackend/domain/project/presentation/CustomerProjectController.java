package com.example.kkaribackend.domain.project.presentation;

import com.example.kkaribackend.domain.project.presentation.dto.request.RegisterServiceRequest;
import com.example.kkaribackend.domain.project.presentation.dto.response.RegisterServiceResponse;
import com.example.kkaribackend.domain.project.presentation.dto.response.ServiceListResponse;
import com.example.kkaribackend.domain.project.presentation.dto.response.ServiceResponse;
import com.example.kkaribackend.domain.project.service.QueryCustomerProjectListService;
import com.example.kkaribackend.domain.project.service.QueryCustomerProjectService;
import com.example.kkaribackend.domain.project.service.RegisterCustomerProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Customer")
@RequiredArgsConstructor
@RequestMapping("/v1/customer/services")
@RestController
public class CustomerProjectController {
    private final RegisterCustomerProjectService registerCustomerProjectService;
    private final QueryCustomerProjectListService queryCustomerProjectListService;
    private final QueryCustomerProjectService queryCustomerProjectService;

    @Operation(summary = "서비스 등록", description = "고른 엔지니어가 담당한다. answers는 질문 → 답(최대 50개). "
        + "고객사명·담당자는 가입 정보로 채운다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public RegisterServiceResponse register(@Valid @RequestBody RegisterServiceRequest request) {
        return registerCustomerProjectService.execute(request);
    }

    @Operation(summary = "내 서비스 목록", description = "최근 등록 순.")
    @GetMapping
    public List<ServiceListResponse> list() {
        return queryCustomerProjectListService.execute();
    }

    @Operation(summary = "내 서비스 상세")
    @GetMapping("/{serviceId}")
    public ServiceResponse get(@PathVariable Long serviceId) {
        return queryCustomerProjectService.execute(serviceId);
    }
}
