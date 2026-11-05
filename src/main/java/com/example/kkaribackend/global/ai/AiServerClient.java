package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.HealthResponse;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

    public IntakeResponseDto intake(IntakeRequestDto request) {
        return call("/intake", () -> aiRestClient.post()
            .uri("/intake")
            .body(request)
            .retrieve()
            .body(IntakeResponseDto.class));
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
            throw classifyResponse(path, e);
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

    private AiServerException classifyResponse(String path, RestClientResponseException exception) {
        Boolean retryable = null;
        boolean readable = false;
        String code = null;
        try {
            JsonNode body = JsonMapper.shared().readTree(exception.getResponseBodyAsString());
            if (body != null && body.isObject()) {
                readable = true;
                JsonNode retry = body.get("retryable");
                if (retry != null && retry.isBoolean()) {
                    retryable = retry.asBoolean();
                }
                JsonNode aiCode = body.get("code");
                // 오류 코드 이외의 임의 문자열이나 본문은 로그에 남기지 않는다
                if (aiCode != null && aiCode.isString() && aiCode.asString().matches("[A-Z_]{1,64}")) {
                    code = aiCode.asString();
                }
            }
        } catch (RuntimeException ignored) {
            // 구형 서버나 프록시의 JSON 이 아닌 오류는 상태 코드로 판별한다
        }
        log.error("AI 서버 오류 응답: {} {} {}", path, exception.getStatusCode().value(), code);
        boolean unavailable = Boolean.TRUE.equals(retryable)
            || (!readable && (exception.getStatusCode().value() == 429
                || exception.getStatusCode().value() == 503));
        return new AiServerException(unavailable ? AiErrorCode.AI_SERVER_UNAVAILABLE : AiErrorCode.AI_SERVER_ERROR);
    }

    private boolean isReadTimeout(ResourceAccessException e) {
        Throwable cause = e.getCause();
        return (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException))
            || cause instanceof SocketTimeoutException;
    }
}
