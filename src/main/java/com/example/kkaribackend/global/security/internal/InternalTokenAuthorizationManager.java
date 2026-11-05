package com.example.kkaribackend.global.security.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * 서버 간 API 는 JWT 대신 X-Internal-Token 헤더가 설정된 토큰과 같아야 통과한다.
 * 토큰이 설정되지 않았으면 모든 요청을 막는다.
 */
@Component
public class InternalTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    public static final String HEADER = "X-Internal-Token";

    private final byte[] token;

    public InternalTokenAuthorizationManager(@Value("${internal-api.token}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
                                           RequestAuthorizationContext context) {
        String provided = context.getRequest().getHeader(HEADER);
        return new AuthorizationDecision(token.length > 0 && provided != null && matches(provided));
    }

    // 비교 시간이 맞은 글자 수에 따라 달라지지 않게 한다
    private boolean matches(String provided) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), token);
    }
}
