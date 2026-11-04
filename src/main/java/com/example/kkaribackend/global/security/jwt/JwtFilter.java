package com.example.kkaribackend.global.security.jwt;

import com.example.kkaribackend.global.error.exception.KkariException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    public static final String EXCEPTION_ATTRIBUTE = "jwtException";

    private final JwtParser jwtParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = jwtParser.resolveToken(request);

        if (token != null) {
            try {
                SecurityContextHolder.getContext().setAuthentication(jwtParser.getAuthentication(token));
            } catch (KkariException e) {
                request.setAttribute(EXCEPTION_ATTRIBUTE, e);
            }
        }

        filterChain.doFilter(request, response);
    }
}
