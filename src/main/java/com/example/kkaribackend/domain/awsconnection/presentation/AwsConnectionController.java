package com.example.kkaribackend.domain.awsconnection.presentation;

import com.example.kkaribackend.domain.awsconnection.presentation.dto.request.RegisterAwsConnectionRequest;
import com.example.kkaribackend.domain.awsconnection.presentation.dto.response.AwsConnectionResponse;
import com.example.kkaribackend.domain.awsconnection.service.QueryAwsConnectionService;
import com.example.kkaribackend.domain.awsconnection.service.RegisterAwsConnectionService;
import com.example.kkaribackend.domain.awsconnection.service.TestAwsConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AWS Connection")
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/aws-connection")
@RestController
public class AwsConnectionController {
    private final QueryAwsConnectionService queryAwsConnectionService;
    private final RegisterAwsConnectionService registerAwsConnectionService;
    private final TestAwsConnectionService testAwsConnectionService;

    @Operation(summary = "AWS 연결 정보 조회", description = "고객사가 Role 신뢰 정책에 넣을 우리 계정 ID, External ID와 현재 연결 상태.")
    @GetMapping
    public AwsConnectionResponse get(@PathVariable Long projectId) {
        return queryAwsConnectionService.execute(projectId);
    }

    @Operation(summary = "Role ARN 등록", description = "등록하면 상태가 UNTESTED가 된다. 연결 정보는 프로젝트에 저장된다.")
    @PostMapping
    public AwsConnectionResponse register(@PathVariable Long projectId,
                                          @Valid @RequestBody RegisterAwsConnectionRequest request) {
        return registerAwsConnectionService.execute(projectId, request);
    }

    @Operation(summary = "AWS 연결 테스트", description = "AssumeRole 후 읽기 권한을 확인한다. CONNECTED / TRUST_POLICY_ERROR / PERMISSION_DENIED")
    @PostMapping("/test")
    public AwsConnectionResponse test(@PathVariable Long projectId) {
        return testAwsConnectionService.execute(projectId);
    }
}
