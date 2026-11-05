package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.dto.HealthResponse;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient aiRestClient;

    // 응답 모양은 해석하지 않고 JSON 인지만 확인한다. 호출마다 Claude 를 여러 번 부르므로 실패해도 다시 시도하지 않는다
    public JsonNode architectures(ArchitectureRequest request) {
        return call("/architectures", () -> aiRestClient.post()
            .uri("/architectures")
            .body(request)
            .retrieve()
            .body(JsonNode.class));
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
            // AI 서버는 Claude 사용량 한도를 넘으면 429 를 준다
            throw new AiServerException(e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
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
