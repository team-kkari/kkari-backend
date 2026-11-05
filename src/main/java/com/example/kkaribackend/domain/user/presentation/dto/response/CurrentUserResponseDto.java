package com.example.kkaribackend.domain.user.presentation.dto.response;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "현재 로그인한 엔지니어 또는 고객의 계정 정보")
public record CurrentUserResponseDto(Long id, String email, String name, String companyName, Role role) {
    public static CurrentUserResponseDto from(User user) {
        return new CurrentUserResponseDto(user.getId(), user.getEmail(), user.getName(), user.getCompanyName(), user.getRole());
    }
}
