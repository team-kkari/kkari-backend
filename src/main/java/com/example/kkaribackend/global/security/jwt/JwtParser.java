package com.example.kkaribackend.global.security.jwt;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.global.security.auth.CustomUserDetails;
import com.example.kkaribackend.global.security.jwt.exception.ExpiredTokenException;
import com.example.kkaribackend.global.security.jwt.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtParser {

    private final JwtProperties jwtProperties;

    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(jwtProperties.header());
        if (bearer != null && bearer.startsWith(jwtProperties.prefix())) {
            return bearer.substring(jwtProperties.prefix().length());
        }
        return null;
    }

    public Authentication getAuthentication(String token) {
        CustomUserDetails userDetails = toUserDetails(parse(token));
        return UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities());
    }

    private CustomUserDetails toUserDetails(Claims claims) {
        try {
            return new CustomUserDetails(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(Objects.requireNonNull(claims.get("role", String.class)))
            );
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException();
        }
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                .verifyWith(jwtProperties.secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw switch (e) {
                case ExpiredJwtException expired -> new ExpiredTokenException();
                default -> new InvalidTokenException();
            };
        }
    }
}
