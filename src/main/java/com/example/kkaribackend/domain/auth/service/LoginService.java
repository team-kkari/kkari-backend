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

@RequiredArgsConstructor
@Service
public class LoginService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public TokenResponse execute(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new TokenResponse(jwtProvider.generateAccessToken(user.getId(), user.getRole()), user.getRole());
    }
}
