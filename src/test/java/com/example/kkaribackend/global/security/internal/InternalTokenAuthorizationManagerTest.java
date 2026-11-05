package com.example.kkaribackend.global.security.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenAuthorizationManagerTest {

    @Test
    void 토큰이_비었거나_짧으면_같은_값을_보내도_막는다() {
        for (String token : List.of("", "   ", "short-placeholder")) {
            InternalTokenAuthorizationManager manager = new InternalTokenAuthorizationManager(token);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(InternalTokenAuthorizationManager.HEADER, token);

            assertThat(manager.authorize(() -> null, new RequestAuthorizationContext(request)).isGranted()).isFalse();
        }
    }
}
