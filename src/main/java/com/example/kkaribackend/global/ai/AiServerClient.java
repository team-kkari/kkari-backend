package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.HealthResponse;
import com.example.kkaribackend.global.ai.dto.RecommendRequest;
import com.example.kkaribackend.global.ai.dto.RecommendResponse;
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

    public RecommendResponse recommend(RecommendRequest request) {
        return call("/recommend", () -> aiRestClient.post()
            .uri("/recommend")
            .body(request)
            .retrieve()
            .body(RecommendResponse.class));
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
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
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
