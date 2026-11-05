package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.dto.HealthResponse;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient aiRestClient;

    // 응답 JSON 을 해석하지 않고 그대로 돌려준다. 호출마다 Claude 를 여러 번 부르므로 실패해도 다시 시도하지 않는다
    public String architectures(ArchitectureRequest request) {
        return call("/architectures", () -> aiRestClient.post()
            .uri("/architectures")
            .body(request)
            .retrieve()
            .body(String.class));
    }

    public HealthResponse health() {
        return call("/health", () -> aiRestClient.get()
            .uri("/health")
            .retrieve()
            .body(HealthResponse.class));
    }

    private <T> T call(String path, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.error("AI 서버 오류 응답: {} {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            // 429: AI 서버의 Claude 사용량 한도 초과
            throw new AiServerException(e.getStatusCode().value() == 429
                ? AiErrorCode.AI_SERVER_BUSY : AiErrorCode.AI_SERVER_ERROR);
        } catch (ResourceAccessException e) {
            if (isReadTimeout(e)) {
                log.error("AI 서버 응답 시간 초과: {}", path, e);
                throw new AiServerException(AiErrorCode.AI_SERVER_TIMEOUT);
            }
            log.error("AI 서버 연결 실패: {}", path, e);
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        } catch (RestClientException e) {
            log.error("AI 서버 요청 실패: {}", path, e);
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        }
    }

    private boolean isReadTimeout(ResourceAccessException e) {
        Throwable cause = e.getCause();
        return (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException))
            || cause instanceof SocketTimeoutException;
    }
}
