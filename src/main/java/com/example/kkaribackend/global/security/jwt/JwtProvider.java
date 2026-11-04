package com.example.kkaribackend.global.security.jwt;

import com.example.kkaribackend.domain.user.domain.Role;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(Long userId, Role role) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + jwtProperties.accessExp() * 1000))
            .signWith(jwtProperties.secretKey())
            .compact();
    }
}
