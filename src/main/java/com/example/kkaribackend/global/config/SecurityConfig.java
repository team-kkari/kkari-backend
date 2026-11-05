package com.example.kkaribackend.global.config;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.global.error.exception.KkariException;
import com.example.kkaribackend.global.security.internal.InternalTokenAuthorizationManager;
import com.example.kkaribackend.global.security.jwt.JwtFilter;
import com.example.kkaribackend.global.security.jwt.JwtParser;
import com.example.kkaribackend.global.security.jwt.exception.ForbiddenException;
import com.example.kkaribackend.global.security.jwt.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtParser jwtParser;
    private final InternalTokenAuthorizationManager internalTokenAuthorizationManager;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/v1/users/me").authenticated()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/v1/auth/**").permitAll()
                .requestMatchers("/v1/internal/**").access(internalTokenAuthorizationManager)
                .requestMatchers("/v1/customer/**").hasRole(Role.CUSTOMER.name())
                .anyRequest().hasRole(Role.MSP_ENGINEER.name())
            )

            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, e) -> {
                    if (request.getAttribute(JwtFilter.EXCEPTION_ATTRIBUTE) instanceof KkariException tokenException) {
                        throw tokenException;
                    }
                    throw new UnauthorizedException();
                })
                .accessDeniedHandler((request, response, e) -> {
                    throw new ForbiddenException();
                })
            )

            .addFilterBefore(new JwtFilter(jwtParser), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        var bcrypt = new BCryptPasswordEncoder();
        var encoder = new DelegatingPasswordEncoder("pbkdf2@SpringSecurity_v5_8", Map.of(
            "pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            "bcrypt", bcrypt));
        // 기존의 식별자 없는 60자 BCrypt 해시는 수정 없이 검증한다.
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
}
