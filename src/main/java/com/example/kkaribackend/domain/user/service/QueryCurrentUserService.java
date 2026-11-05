package com.example.kkaribackend.domain.user.service;

import com.example.kkaribackend.domain.user.facade.UserFacade;
import com.example.kkaribackend.domain.user.presentation.dto.response.CurrentUserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueryCurrentUserService {
    private final UserFacade userFacade;

    @Transactional(readOnly = true)
    public CurrentUserResponseDto execute() {
        return CurrentUserResponseDto.from(userFacade.getCurrentUser());
    }
}
