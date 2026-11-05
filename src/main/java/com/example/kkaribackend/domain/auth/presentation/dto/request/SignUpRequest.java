package com.example.kkaribackend.domain.auth.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.example.kkaribackend.domain.user.domain.EmailNormalizer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record SignUpRequest(
    @Schema(description = "앞뒤 공백 제거·소문자 변환 후 저장 및 중복 검사")
    @NotBlank(message = "이메일은 비어있을 수 없습니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    String email,

    @NotBlank(message = "비밀번호는 비어있을 수 없습니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "비밀번호는 영문과 숫자를 각각 1자 이상 포함해야 합니다.")
    @Schema(description = "영문과 숫자를 각각 포함한 8~64자", accessMode = Schema.AccessMode.WRITE_ONLY)
    String password,

    @NotBlank(message = "이름은 비어있을 수 없습니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    String name,

    @NotBlank(message = "소속 회사는 비어있을 수 없습니다.")
    @Size(max = 100, message = "소속 회사는 100자 이하여야 합니다.")
    String companyName
) {
    public SignUpRequest {
        email = EmailNormalizer.normalize(email);
    }
}
