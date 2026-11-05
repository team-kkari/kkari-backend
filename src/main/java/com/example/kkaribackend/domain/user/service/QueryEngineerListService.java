package com.example.kkaribackend.domain.user.service;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.domain.user.presentation.dto.response.EngineerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class QueryEngineerListService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<EngineerResponse> execute() {
        return userRepository.findAllByRoleOrderByNameAscIdAsc(Role.MSP_ENGINEER).stream()
            .map(EngineerResponse::from)
            .toList();
    }
}
