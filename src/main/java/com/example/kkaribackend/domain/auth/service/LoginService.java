package com.example.kkaribackend.domain.auth.service;

import com.example.kkaribackend.domain.auth.exception.InvalidCredentialsException;
import com.example.kkaribackend.domain.auth.presentation.dto.request.LoginRequest;
import com.example.kkaribackend.domain.auth.presentation.dto.response.TokenResponse;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.kkaribackend.domain.user.domain.EmailNormalizer;

@RequiredArgsConstructor
@Service
public class LoginService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public TokenResponse execute(LoginRequest request) {
        var candidates = userRepository.findAllByEmailIgnoreCase(EmailNormalizer.normalize(request.email()));
        User user = candidates.stream().filter(candidate -> candidate.getEmail().equals(request.email()))
            .findFirst().orElseGet(() -> {
                if (candidates.size() != 1) {
                    // 충돌 계정을 임의 선택하거나 비밀번호를 다른 계정에 대입하지 않는다.
                    throw new InvalidCredentialsException();
                }
                return candidates.getFirst();
            });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new TokenResponse(jwtProvider.generateAccessToken(user.getId(), user.getRole()), user.getRole());
    }
}
