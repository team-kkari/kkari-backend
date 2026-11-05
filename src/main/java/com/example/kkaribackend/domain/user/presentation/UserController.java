package com.example.kkaribackend.domain.user.presentation;

import com.example.kkaribackend.domain.user.presentation.dto.response.CurrentUserResponseDto;
import com.example.kkaribackend.domain.user.service.QueryCurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User")
@RequiredArgsConstructor
@RequestMapping("/v1/users")
@RestController
public class UserController {
    private final QueryCurrentUserService queryCurrentUserService;

    @Operation(summary = "내 계정 조회", description = "로그인한 엔지니어·고객 공통. 토큰으로 현재 사용자를 확인한다.")
    @GetMapping("/me")
    public CurrentUserResponseDto me() {
        return queryCurrentUserService.execute();
    }
}
