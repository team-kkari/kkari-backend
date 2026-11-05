package com.example.kkaribackend.domain.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record LoginRequest(
    @Schema(description = "앞뒤 공백 제거 후 대소문자 구분 없이 조회. 기존 중복 계정은 가입 당시 이메일 표기를 사용")
    @NotBlank(message = "이메일은 비어있을 수 없습니다.")
    String email,

    @NotBlank(message = "비밀번호는 비어있을 수 없습니다.")
    String password
) {
    public LoginRequest {
        // 기존에 대소문자만 다른 계정이 공존하면 원래 표기로 계정을 구분해야 한다.
        email = email == null ? null : email.trim();
    }
}
