package com.example.kkaribackend.domain.user.facade;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import com.example.kkaribackend.domain.user.domain.repository.UserRepository;
import com.example.kkaribackend.domain.user.exception.EngineerNotFoundException;
import com.example.kkaribackend.domain.user.exception.UserNotFoundException;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import com.example.kkaribackend.global.security.jwt.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserRepository userRepository;

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.userId();
        }
        throw new UnauthorizedException();
    }

    public User getCurrentUser() {
        return userRepository.findById(getCurrentUserId())
            .orElseThrow(UserNotFoundException::new);
    }

    public User getEngineer(Long id) {
        return userRepository.findByIdAndRole(id, Role.MSP_ENGINEER)
            .orElseThrow(EngineerNotFoundException::new);
    }
}
