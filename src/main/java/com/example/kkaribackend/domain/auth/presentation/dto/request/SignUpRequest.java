package com.example.kkaribackend.domain.auth.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @NotBlank(message = "이메일은 비어있을 수 없습니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    String email,

    @NotBlank(message = "비밀번호는 비어있을 수 없습니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    String password,

    @NotBlank(message = "이름은 비어있을 수 없습니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    String name,

    @NotBlank(message = "소속 회사는 비어있을 수 없습니다.")
    @Size(max = 100, message = "소속 회사는 100자 이하여야 합니다.")
    String companyName
) {
}
