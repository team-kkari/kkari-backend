package com.example.kkaribackend.domain.user.presentation.dto.response;

import com.example.kkaribackend.domain.user.domain.User;

public record EngineerResponse(
    Long engineerId,
    String name,
    String companyName
) {
    public static EngineerResponse from(User engineer) {
        return new EngineerResponse(engineer.getId(), engineer.getName(), engineer.getCompanyName());
    }
}
