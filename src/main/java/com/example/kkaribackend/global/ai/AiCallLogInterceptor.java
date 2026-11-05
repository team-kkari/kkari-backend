package com.example.kkaribackend.global.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AI 서버 요청 한 번(시도 한 번)마다 AI_CALL 로거에 한 줄을 남긴다. 해커톤이라 본문 전체를 남긴다.
 * 헤더는 남기지 않는다. 멀티파트 요청은 FILES 속성, 파일 응답은 이름·종류·크기, NDJSON 스트림은 본문을 남기지 않는다.
 */
class AiCallLogInterceptor implements ClientHttpRequestInterceptor {

    static final Logger AI_CALL = LoggerFactory.getLogger("AI_CALL");
    static final String ATTEMPT = AiCallLogInterceptor.class.getName() + ".attempt";
    static final String MAX_ATTEMPTS = AiCallLogInterceptor.class.getName() + ".maxAttempts";
    static final String FILES = AiCallLogInterceptor.class.getName() + ".files";

    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        long startedAt = System.nanoTime();
        String requestLog = requestLog(request, body);
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            AI_CALL.info("ai_call {} {} error={} duration_ms={}", head(request), requestLog,
                e.getClass().getSimpleName(), millisSince(startedAt));
            throw e;
        }
        HttpStatusCode status = response.getStatusCode();
        MediaType contentType = response.getHeaders().getContentType();
        // 스트림은 모으지 않는다. 요약은 AiServerClient.chat 이 끝날 때 남긴다
        if (status.is2xxSuccessful() && contentType != null && NDJSON.isCompatibleWith(contentType)) {
            AI_CALL.info("ai_call {} {} status={} duration_ms={} response=stream", head(request), requestLog,
                status.value(), millisSince(startedAt));
            return response;
        }
        byte[] responseBody;
        try {
            responseBody = response.getBody().readAllBytes();
        } catch (IOException e) {
            response.close();
            AI_CALL.info("ai_call {} {} status={} error={} duration_ms={}", head(request), requestLog, status.value(),
                e.getClass().getSimpleName(), millisSince(startedAt));
            throw e;
        }
        AI_CALL.info("ai_call {} {} status={}{} duration_ms={} response_bytes={} {}", head(request), requestLog,
            status.value(), aiError(status, responseBody), millisSince(startedAt), responseBody.length,
            responseLog(response.getHeaders(), responseBody));
        return new BufferedResponse(response, responseBody);
    }

    private static String head(HttpRequest request) {
        Object attempt = request.getAttributes().getOrDefault(ATTEMPT, 1);
        Object maxAttempts = request.getAttributes().getOrDefault(MAX_ATTEMPTS, 1);
        return "%s %s attempt=%s/%s".formatted(request.getMethod(), request.getURI().getPath(), attempt, maxAttempts);
    }

    private static String requestLog(HttpRequest request, byte[] body) {
        MediaType contentType = request.getHeaders().getContentType();
        if (contentType != null && MediaType.MULTIPART_FORM_DATA.includes(contentType)) {
            return "request_bytes=%d request_files=%s".formatted(body.length, request.getAttributes().get(FILES));
        }
        String text = new String(body, StandardCharsets.UTF_8);
        String sessionId = sessionId(body);
        return "%srequest_bytes=%d request=%s".formatted(
            sessionId == null ? "" : "session_id=" + sessionId + " ", body.length, text);
    }

    private static String sessionId(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode sessionId = JsonMapper.shared().readTree(body).get("session_id");
            return sessionId == null || !sessionId.isString() ? null : sessionId.asString();
        } catch (JacksonException e) {
            return null;
        }
    }

    // 오류 본문의 code·retryable. AiServerClient 가 같은 본문으로 재시도 여부를 정한다
    private static String aiError(HttpStatusCode status, byte[] body) {
        if (!status.isError()) {
            return "";
        }
        try {
            JsonNode error = JsonMapper.shared().readTree(body);
            JsonNode code = error.get("code");
            if (code == null || !code.isString()) {
                return "";
            }
            JsonNode retryable = error.get("retryable");
            return " ai_code=%s retryable=%s".formatted(code.asString(), retryable != null && retryable.asBoolean());
        } catch (JacksonException e) {
            return "";
        }
    }

    // Content-Disposition 이 있는 응답은 파일(tf·zip)이라 내용 대신 이름·종류만 남긴다
    private static String responseLog(HttpHeaders headers, byte[] body) {
        if (headers.containsHeader(HttpHeaders.CONTENT_DISPOSITION)) {
            String filename;
            try {
                filename = headers.getContentDisposition().getFilename();
            } catch (IllegalArgumentException e) {
                filename = null;
            }
            return "response_file=%s response_type=%s".formatted(filename, headers.getContentType());
        }
        return "response=" + new String(body, StandardCharsets.UTF_8);
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
