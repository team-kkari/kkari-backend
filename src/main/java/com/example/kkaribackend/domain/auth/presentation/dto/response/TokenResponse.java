package com.example.kkaribackend.domain.auth.presentation.dto.response;

import com.example.kkaribackend.domain.user.domain.Role;

public record TokenResponse(
    String accessToken,
    Role role
) {
}
