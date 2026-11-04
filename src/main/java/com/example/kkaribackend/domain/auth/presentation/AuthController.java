package com.example.kkaribackend.domain.auth.presentation;

import com.example.kkaribackend.domain.auth.presentation.dto.request.LoginRequest;
import com.example.kkaribackend.domain.auth.presentation.dto.request.SignUpRequest;
import com.example.kkaribackend.domain.auth.presentation.dto.response.TokenResponse;
import com.example.kkaribackend.domain.auth.service.LoginService;
import com.example.kkaribackend.domain.auth.service.SignUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@SecurityRequirements
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
@RestController
public class AuthController {
    private final SignUpService signUpService;
    private final LoginService loginService;

    @Operation(summary = "회원가입", description = "가입 후 바로 access token을 발급한다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public TokenResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return signUpService.execute(request);
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.execute(request);
    }
}
