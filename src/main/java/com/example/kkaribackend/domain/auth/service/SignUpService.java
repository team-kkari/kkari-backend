package com.example.kkaribackend.domain.auth.service;

import com.example.kkaribackend.domain.auth.presentation.dto.request.SignUpRequest;
import com.example.kkaribackend.domain.auth.presentation.dto.response.TokenResponse;
import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.domain.user.exception.UserAlreadyExistsException;
import com.example.kkaribackend.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.kkaribackend.domain.user.domain.EmailNormalizer;

@RequiredArgsConstructor
@Service
public class SignUpService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResponse execute(SignUpRequest request, Role role) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException();
        }

        User user;
        try {
            user = userRepository.saveAndFlush(
                User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(request.password()))
                    .name(request.name())
                    .companyName(request.companyName())
                    .role(role)
                    .build()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new UserAlreadyExistsException();
        }

        return new TokenResponse(jwtProvider.generateAccessToken(user.getId(), user.getRole()), user.getRole());
    }
}
