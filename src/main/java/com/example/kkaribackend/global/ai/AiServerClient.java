package com.example.kkaribackend.global.ai;

import com.example.kkaribackend.global.ai.dto.AiErrorResponse;
import com.example.kkaribackend.global.ai.dto.ArchitectureRequest;
import com.example.kkaribackend.global.ai.dto.ChatEvent;
import com.example.kkaribackend.global.ai.dto.ChatRequest;
import com.example.kkaribackend.global.ai.dto.ConsultRequest;
import com.example.kkaribackend.global.ai.dto.ConsultResponse;
import com.example.kkaribackend.global.ai.dto.IntakeRequestDto;
import com.example.kkaribackend.global.ai.dto.IntakeResponseDto;
import com.example.kkaribackend.global.ai.dto.TerraformFile;
import com.example.kkaribackend.global.ai.dto.TerraformFormat;
import com.example.kkaribackend.global.ai.dto.TerraformRequest;
import com.example.kkaribackend.global.ai.exception.AiErrorCode;
import com.example.kkaribackend.global.ai.exception.AiServerException;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * AI 서버(kkari-AI) 호출을 모은다. 실패는 모두 AiServerException 으로 바꾸고,
 * AI 가 retryable=true 로 답한 실패만 지수 백오프로 다시 보낸다.
 */
@Slf4j
public class AiServerClient {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONSULT_FILES_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration CONVERSATION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ARCHITECTURES_TIMEOUT = Duration.ofSeconds(300);
    private static final Duration TERRAFORM_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient healthClient;
    private final RestClient consultFilesClient;
    private final RestClient conversationClient;
    private final RestClient architecturesClient;
    private final RestClient terraformClient;
    private final Duration retryDelay;

    // requestFactory: read timeout 을 받아 그 timeout 을 쓰는 요청 팩토리를 만든다
    public AiServerClient(RestClient.Builder builder, Function<Duration, ClientHttpRequestFactory> requestFactory,
                          Duration retryDelay) {
        this.healthClient = builder.clone().requestFactory(requestFactory.apply(HEALTH_TIMEOUT)).build();
        this.consultFilesClient = builder.clone().requestFactory(requestFactory.apply(CONSULT_FILES_TIMEOUT)).build();
        this.conversationClient = builder.clone().requestFactory(requestFactory.apply(CONVERSATION_TIMEOUT)).build();
        this.architecturesClient = builder.clone().requestFactory(requestFactory.apply(ARCHITECTURES_TIMEOUT)).build();
        this.terraformClient = builder.clone().requestFactory(requestFactory.apply(TERRAFORM_TIMEOUT)).build();
        this.retryDelay = retryDelay;
    }

    public boolean isHealthy() {
        try {
            Health health = call("/health", () -> healthClient.get().uri("/health").retrieve().body(Health.class));
            return health != null && health.ok();
        } catch (AiServerException e) {
            return false;
        }
    }

    // 응답의 file_analysis 를 돌려준다. 이후 consult 에 그대로 넣는다
    public JsonNode consultFiles(List<MultipartFile> files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        files.forEach(file -> body.add("files", file.getResource()));
        JsonNode response = call("/consult/files", () -> consultFilesClient.post()
            .uri("/consult/files")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(JsonNode.class));
        JsonNode fileAnalysis = response == null ? null : response.get("file_analysis");
        if (fileAnalysis == null || !fileAnalysis.isObject()) {
            throw invalidResponse("/consult/files");
        }
        return fileAnalysis;
    }

    public ConsultResponse consult(ConsultRequest request) {
        ConsultResponse response = call("/consult", () -> conversationClient.post()
            .uri("/consult")
            .body(request)
            .retrieve()
            .body(ConsultResponse.class));
        if (response == null || isBlank(response.reply())) {
            throw invalidResponse("/consult");
        }
        return response;
    }

    // 원본 응답도 함께 돌려준다. 상담 기록에 그대로 보관한다
    public IntakeResponseDto intake(IntakeRequestDto request) {
        IntakeResponseDto response = call("/intake", () -> {
            JsonNode raw = conversationClient.post().uri("/intake").body(request).retrieve().body(JsonNode.class);
            IntakeResponseDto parsed = raw == null ? null : JsonMapper.shared().treeToValue(raw, IntakeResponseDto.class);
            return parsed == null ? null : parsed.toBuilder().rawResponse(raw).build();
        });
        if (response == null || isBlank(response.reply())) {
            throw invalidResponse("/intake");
        }
        return response;
    }

    // 응답 모양은 해석하지 않고 JSON 인지만 확인한다
    public JsonNode architectures(ArchitectureRequest request) {
        JsonNode response = call("/architectures", () -> architecturesClient.post()
            .uri("/architectures")
            .body(request)
            .retrieve()
            .body(JsonNode.class));
        if (isEmpty(response)) {
            throw invalidResponse("/architectures");
        }
        return response;
    }

    // {project, files: {파일 이름: 내용}, warnings}
    public JsonNode terraform(TerraformRequest request) {
        JsonNode response = call("/terraform", () -> terraformClient.post()
            .uri("/terraform")
            .body(new TerraformBody(request, "json"))
            .retrieve()
            .body(JsonNode.class));
        if (isEmpty(response)) {
            throw invalidResponse("/terraform");
        }
        return response;
    }

    // 파일 이름은 AI 서버의 Content-Disposition 을 그대로 쓴다
    public TerraformFile terraformFile(TerraformRequest request, TerraformFormat format) {
        ResponseEntity<byte[]> response = call("/terraform", () -> terraformClient.post()
            .uri("/terraform")
            .body(new TerraformBody(request, format.name().toLowerCase(Locale.ROOT)))
            .retrieve()
            .toEntity(byte[].class));
        String filename = response.getHeaders().getContentDisposition().getFilename();
        MediaType contentType = response.getHeaders().getContentType();
        if (filename == null || contentType == null || response.getBody() == null) {
            throw invalidResponse("/terraform");
        }
        return new TerraformFile(filename, contentType, response.getBody());
    }

    /**
     * NDJSON 을 한 줄씩 읽어 onEvent 로 넘긴다. Done 이나 Failure 를 넘기면 끝난다.
     * 첫 줄 전에 온 HTTP 오류만 재시도 대상이고, 스트림이 시작된 뒤의 실패는 다시 보내지 않는다.
     */
    public void chat(ChatRequest request, Consumer<ChatEvent> onEvent) {
        boolean finished = call("/chat", () -> conversationClient.post()
            .uri("/chat")
            .body(request)
            .exchange((httpRequest, response) -> {
                if (response.getStatusCode().isError()) {
                    throw response.createException();
                }
                return readEvents(response.getBody(), onEvent);
            }));
        if (!finished) {
            log.error("AI 서버 스트림이 끝 이벤트 없이 끊겼습니다: /chat");
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE);
        }
    }

    private boolean readEvents(InputStream body, Consumer<ChatEvent> onEvent) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.isBlank()) {
                continue;
            }
            ChatEvent event = JsonMapper.shared().readValue(line, ChatEvent.class);
            onEvent.accept(event);
            if (event instanceof ChatEvent.Done || event instanceof ChatEvent.Failure) {
                return true;
            }
        }
        return false;
    }

    private <T> T call(String path, Supplier<T> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return request.get();
            } catch (RestClientResponseException e) {
                AiErrorResponse error = parseError(e.getResponseBodyAsString());
                boolean retry = error != null && error.retryable() && attempt < MAX_ATTEMPTS;
                if (!retry || !waitBeforeRetry(path, attempt)) {
                    throw toException(path, e.getStatusCode().value(), error);
                }
            } catch (ResourceAccessException e) {
                throw toException(path, e);
            } catch (JacksonException | RestClientException e) {
                // 파싱 예외에 응답 원문이 들어 있을 수 있어 예외 메시지는 남기지 않는다
                log.error("AI 서버 응답 처리 실패: {} {}", path, e.getClass().getSimpleName());
                throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
            }
        }
    }

    private boolean waitBeforeRetry(String path, int attempt) {
        Duration delay = retryDelay.multipliedBy(1L << (attempt - 1));
        log.warn("AI 서버 재시도: {} {}/{} {}ms 후", path, attempt + 1, MAX_ATTEMPTS, delay.toMillis());
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private AiErrorResponse parseError(String body) {
        try {
            return JsonMapper.shared().readValue(body, AiErrorResponse.class);
        } catch (JacksonException e) {
            // 프록시 등이 보낸 JSON 이 아닌 오류는 상태 코드로 판단한다
            return null;
        }
    }

    private AiServerException toException(String path, int status, AiErrorResponse error) {
        if (error == null) {
            log.error("AI 서버 오류 응답: {} {}", path, status);
            return new AiServerException(status == 429 || status == 503
                ? AiErrorCode.AI_SERVER_UNAVAILABLE : AiErrorCode.AI_SERVER_ERROR);
        }
        String code = error.code() != null && error.code().matches("[A-Z_]{1,64}") ? error.code() : "";
        // 오류 코드와 필드 이름만 남긴다. message 에는 고객이 올린 파일 이름 등이 들어갈 수 있다
        log.error("AI 서버 오류 응답: {} {} {} {}", path, status, code, error.errors() == null ? List.of()
            : error.errors().stream().map(AiErrorResponse.FieldError::field).toList());
        if (error.retryable()) {
            return new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE);
        }
        return switch (code) {
            case "REFUSED" -> new AiServerException(AiErrorCode.AI_REQUEST_REFUSED);
            // 고객이 올린 파일을 하나도 분석할 수 없을 때처럼 이유를 고객에게 보여 줘야 해서 AI 문구를 그대로 쓴다
            case "INVALID_REQUEST" -> isBlank(error.message())
                ? new AiServerException(AiErrorCode.AI_INVALID_REQUEST)
                : new AiServerException(AiErrorCode.AI_INVALID_REQUEST, error.message());
            default -> new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        };
    }

    private AiServerException toException(String path, ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
            log.error("AI 서버 응답 시간 초과: {}", path);
            return new AiServerException(AiErrorCode.AI_SERVER_TIMEOUT);
        }
        log.error("AI 서버 연결 실패: {} {}", path, cause == null ? null : cause.getClass().getSimpleName());
        return new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE);
    }

    private AiServerException invalidResponse(String path) {
        log.error("AI 서버 응답 형식 오류: {}", path);
        return new AiServerException(AiErrorCode.AI_SERVER_ERROR);
    }

    private static boolean isEmpty(JsonNode body) {
        return body == null || body.isNull() || body.isMissingNode();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Health(boolean ok) {
    }

    // name 을 비우면 AI 서버 기본값(kkari)을 쓴다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TerraformBody(String name, JsonNode diagram, JsonNode constraints, String format) {
        TerraformBody(TerraformRequest request, String format) {
            this(request.name(), request.diagram(), request.constraints(), format);
        }
    }
}
