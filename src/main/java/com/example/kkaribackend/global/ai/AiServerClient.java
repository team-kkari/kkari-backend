package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.dto.HealthResponse;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
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
import tools.jackson.core.JacksonException;

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
        JsonNode architectures = call("/architectures", () -> aiRestClient.post()
            .uri("/architectures")
            .body(request)
            .retrieve()
            .body(JsonNode.class));
        if (isEmpty(architectures)) {
            log.error("AI 서버 응답이 비어 있습니다: /architectures");
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        }
        return architectures;
    }

    public IntakeResponseDto intake(IntakeRequestDto request) {
        IntakeResponseDto response = call("/intake", () -> {
            String body = aiRestClient.post().uri("/intake").body(request).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            try {
                JsonNode raw = JsonMapper.shared().readTree(body);
                IntakeResponseDto parsed = JsonMapper.shared().treeToValue(raw, IntakeResponseDto.class);
                return parsed == null ? null : parsed.toBuilder().rawResponse(raw).build();
            } catch (JacksonException e) {
                log.error("AI 서버 응답 파싱 실패: /intake");
                throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
            }
        });
        if (response == null || response.reply() == null || response.reply().isBlank()) {
            log.error("AI 서버 응답 형식 오류: /intake");
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        }
        return response;
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
            // 파싱 예외에 응답 원문이 포함될 수 있으므로 예외 메시지는 기록하지 않는다.
            log.error("AI 서버 요청 실패: {} {}", path, e.getClass().getSimpleName());
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        }
    }

    private boolean isEmpty(JsonNode body) {
        return body == null || body.isNull() || body.isMissingNode();
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
