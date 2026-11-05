package com.example.kkaribackend.domain.user.presentation;

import com.example.kkaribackend.domain.user.presentation.dto.response.EngineerResponse;
import com.example.kkaribackend.domain.user.service.QueryEngineerListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customer")
@RequiredArgsConstructor
@RequestMapping("/v1/customer/engineers")
@RestController
public class EngineerController {
    private final QueryEngineerListService queryEngineerListService;

    @Operation(summary = "엔지니어 목록", description = "서비스 등록 때 담당 엔지니어를 고르는 용도. 이름 순.")
    @GetMapping
    public List<EngineerResponse> list() {
        return queryEngineerListService.execute();
    }
}
