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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * AI 서버(kkari-AI) 호출을 모은다. 실패는 모두 AiServerException 으로 바꾸고,
 * AI 가 retryable=true 로 답한 실패만 지수 백오프로 다시 보낸다.
 */
@Slf4j
public class AiServerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_ATTEMPTS = 3;

    private final Timeouts timeouts;
    private final RestClient healthClient;
    private final RestClient consultFilesClient;
    private final RestClient conversationClient;
    private final RestClient architecturesClient;
    private final RestClient terraformClient;
    private final Duration retryDelay;

    public AiServerClient(String baseUrl) {
        this(baseUrl, Timeouts.SPEC, RETRY_DELAY);
    }

    AiServerClient(String baseUrl, Timeouts timeouts, Duration retryDelay) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.timeouts = timeouts;
        this.healthClient = client(baseUrl, httpClient, timeouts.health());
        this.consultFilesClient = client(baseUrl, httpClient, timeouts.consultFiles());
        this.conversationClient = client(baseUrl, httpClient, timeouts.conversation());
        this.architecturesClient = client(baseUrl, httpClient, timeouts.architectures());
        this.terraformClient = client(baseUrl, httpClient, timeouts.terraform());
        this.retryDelay = retryDelay;
    }

    // 이 팩토리의 read timeout 은 응답 본문을 다 읽을 때까지의 전체 시간 제한이다
    private static RestClient client(String baseUrl, HttpClient httpClient, Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
            .requestInterceptor(new AiCallLogInterceptor()).build();
    }

    // 시도 번호는 요청 속성으로 넘겨 AI_CALL 로그에 남긴다
    private static RestClient.RequestBodySpec post(RestClient client, String path, int attempt) {
        return client.post().uri(path)
            .attribute(AiCallLogInterceptor.ATTEMPT, attempt)
            .attribute(AiCallLogInterceptor.MAX_ATTEMPTS, MAX_ATTEMPTS);
    }

    // 재시도하지 않는다. 상태 확인은 한 번에 빨리 끝나야 한다
    public boolean isHealthy() {
        try {
            Health health = healthClient.get().uri("/health").retrieve().body(Health.class);
            return health != null && health.ok();
        } catch (RestClientException e) {
            return false;
        }
    }

    // 응답의 file_analysis 를 돌려준다. 이후 consult 에 그대로 넣는다
    public JsonNode consultFiles(List<MultipartFile> files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        files.forEach(file -> body.add("files", file.getResource()));
        // 파일 내용 대신 이름과 크기만 로그에 남긴다
        List<String> fileLog = files.stream()
            .map(file -> file.getOriginalFilename() + "(" + file.getSize() + "B)").toList();
        JsonNode response = call("/consult/files", attempt -> post(consultFilesClient, "/consult/files", attempt)
            .attribute(AiCallLogInterceptor.FILES, fileLog)
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

    // 원본 응답도 함께 돌려준다. 상담 기록에 그대로 보관한다
    public ConsultResponse consult(ConsultRequest request) {
        ConsultResponse response = postKeepingRaw("/consult", request, ConsultResponse.class,
            (parsed, raw) -> parsed.toBuilder().rawResponse(raw).build());
        if (response == null || isBlank(response.reply())) {
            throw invalidResponse("/consult");
        }
        return response;
    }

    // 원본 응답도 함께 돌려준다. 상담 기록에 그대로 보관한다
    public IntakeResponseDto intake(IntakeRequestDto request) {
        IntakeResponseDto response = postKeepingRaw("/intake", request, IntakeResponseDto.class,
            (parsed, raw) -> parsed.toBuilder().rawResponse(raw).build());
        if (response == null || isBlank(response.reply())) {
            throw invalidResponse("/intake");
        }
        return response;
    }

    private <T> T postKeepingRaw(String path, Object request, Class<T> type, BiFunction<T, JsonNode, T> attachRaw) {
        return call(path, attempt -> {
            JsonNode raw = post(conversationClient, path, attempt).body(request).retrieve().body(JsonNode.class);
            T parsed = raw == null ? null : JsonMapper.shared().treeToValue(raw, type);
            return parsed == null ? null : attachRaw.apply(parsed, raw);
        });
    }

    // 응답 모양은 해석하지 않고 JSON 인지만 확인한다
    public JsonNode architectures(ArchitectureRequest request) {
        JsonNode response = call("/architectures", attempt -> post(architecturesClient, "/architectures", attempt)
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
        JsonNode response = call("/terraform", attempt -> post(terraformClient, "/terraform", attempt)
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
        ResponseEntity<byte[]> response = call("/terraform", attempt -> post(terraformClient, "/terraform", attempt)
            .body(new TerraformBody(request, format.name().toLowerCase(Locale.ROOT)))
            .retrieve()
            .toEntity(byte[].class));
        String filename = filename(response.getHeaders());
        MediaType contentType = response.getHeaders().getContentType();
        if (filename == null || contentType == null || response.getBody() == null) {
            throw invalidResponse("/terraform");
        }
        return new TerraformFile(filename, contentType, response.getBody());
    }

    private static String filename(HttpHeaders headers) {
        try {
            return headers.getContentDisposition().getFilename();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * NDJSON 을 한 줄씩 읽어 onEvent 로 넘긴다. Done 이나 Failure 를 넘기면 끝난다.
     * 첫 줄 전에 온 HTTP 오류만 재시도 대상이고, 스트림이 시작된 뒤의 실패는 다시 보내지 않는다.
     */
    public void chat(ChatRequest request, Consumer<ChatEvent> onEvent) {
        ChatSummary summary = new ChatSummary();
        try {
            call("/chat", attempt -> {
                long sentAt = System.nanoTime();
                return post(conversationClient, "/chat", attempt)
                    .attribute(AiCallLogInterceptor.STREAM, true)
                    .body(request)
                    .exchange((httpRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw response.createException();
                        }
                        if (!readEvents(response.getBody(), summary.andThen(onEvent))) {
                            throw streamBroken(sentAt);
                        }
                        return null;
                    });
            });
        } finally {
            AiCallLogInterceptor.AI_CALL.info("ai_chat_summary POST /chat sources={} refused={} error={} answer={}",
                summary.sources, summary.refused, summary.error, summary.answer);
        }
    }

    // 스트림은 줄마다 남기지 않고 끝날 때 한 줄로 남긴다. 호출 하나 안에서만 쓴다
    private static final class ChatSummary implements Consumer<ChatEvent> {
        private final StringBuilder answer = new StringBuilder();
        private int sources;
        private boolean refused;
        private String error;

        @Override
        public void accept(ChatEvent event) {
            switch (event) {
                case ChatEvent.Sources s -> sources = s.sources() == null ? 0 : s.sources().size();
                case ChatEvent.Delta d -> answer.append(d.text());
                case ChatEvent.Done d -> refused = d.refused();
                case ChatEvent.Failure f -> error = f.code() + "(retryable=" + f.retryable() + ")";
            }
        }
    }

    // 끊기면 false. 시간 제한이 지나면 요청 팩토리가 본문을 닫아 끊김과 같은 모습이 된다
    private boolean readEvents(InputStream body, Consumer<ChatEvent> onEvent) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                ChatEvent event = parseEvent(line);
                if (event == null) {
                    continue;
                }
                onEvent.accept(event);
                if (event instanceof ChatEvent.Done || event instanceof ChatEvent.Failure) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    // 빈 줄과 나중에 AI 서버에 생길 이벤트 종류는 null 로 건너뛴다
    private static ChatEvent parseEvent(String line) {
        if (line.isBlank()) {
            return null;
        }
        try {
            return JsonMapper.shared().readValue(line, ChatEvent.class);
        } catch (InvalidTypeIdException e) {
            return null;
        }
    }

    private AiServerException streamBroken(long sentAt) {
        boolean timedOut = Duration.ofNanos(System.nanoTime() - sentAt).compareTo(timeouts.conversation()) >= 0;
        log.error("AI 서버 스트림이 끝 이벤트 없이 끊겼습니다: /chat {}", timedOut ? "시간 초과" : "연결 끊김");
        return new AiServerException(timedOut ? AiErrorCode.AI_SERVER_TIMEOUT : AiErrorCode.AI_SERVER_UNAVAILABLE);
    }

    // 시도마다 AI_CALL 로그는 AiCallLogInterceptor 가 남기고, 여기서는 호출이 끝날 때 결과 한 줄을 남긴다
    private <T> T call(String path, IntFunction<T> request) {
        long startedAt = System.nanoTime();
        int attempt = 1;
        String result = "OK";
        try {
            for (; ; attempt++) {
                try {
                    return request.apply(attempt);
                } catch (RestClientResponseException e) {
                    AiErrorResponse error = parseError(e.getResponseBodyAsString());
                    boolean retry = error != null && error.retryable() && attempt < MAX_ATTEMPTS;
                    if (!retry) {
                        throw errorResponseException(path, e.getStatusCode().value(), error);
                    }
                    if (!waitBeforeRetry(path, attempt)) {
                        throw errorResponseException(path, e.getStatusCode().value(), error);
                    }
                } catch (ResourceAccessException e) {
                    throw accessException(path, e);
                } catch (JacksonException | RestClientException e) {
                    // 파싱 예외에 응답 원문이 들어 있을 수 있어 예외 메시지는 남기지 않는다
                    log.error("AI 서버 응답 처리 실패: {} {}", path, e.getClass().getSimpleName());
                    throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                }
            }
        } catch (RuntimeException e) {
            result = e instanceof AiServerException ai ? ai.getErrorCode().getCode() : e.getClass().getSimpleName();
            throw e;
        } finally {
            AiCallLogInterceptor.AI_CALL.info("ai_call_end POST {} attempts={}/{} duration_ms={} result={}", path,
                attempt, MAX_ATTEMPTS, (System.nanoTime() - startedAt) / 1_000_000, result);
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
        // 프록시 등이 보낸, code 가 없는 오류는 AI 서버 오류가 아니라 상태 코드로 판단한다
        try {
            AiErrorResponse error = JsonMapper.shared().readValue(body, AiErrorResponse.class);
            return error == null || error.code() == null ? null : error;
        } catch (JacksonException e) {
            return null;
        }
    }

    private AiServerException errorResponseException(String path, int status, AiErrorResponse error) {
        if (error == null) {
            log.error("AI 서버 오류 응답: {} {}", path, status);
            return new AiServerException(switch (status) {
                case 429, 503 -> AiErrorCode.AI_SERVER_UNAVAILABLE;
                case 504 -> AiErrorCode.AI_SERVER_TIMEOUT;
                default -> AiErrorCode.AI_SERVER_ERROR;
            });
        }
        String code = error.code();
        String loggedCode = code.matches("[A-Z_]{1,64}") ? code : "";
        List<String> fields = error.errors() == null ? List.of()
            : error.errors().stream().map(AiErrorResponse.FieldError::field).toList();
        // 오류 코드와 필드 이름만 남긴다. message 에는 고객이 올린 파일 이름 등이 들어갈 수 있다
        log.error("AI 서버 오류 응답: {} {} {} {}", path, status, loggedCode, fields);
        if (error.retryable()) {
            return new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE);
        }
        return switch (code) {
            case "REFUSED" -> new AiServerException(AiErrorCode.AI_REQUEST_REFUSED);
            case "INVALID_REQUEST" -> isCustomerFileError(path, error)
                ? new AiServerException(AiErrorCode.AI_INVALID_REQUEST, error.message())
                : new AiServerException(AiErrorCode.AI_SERVER_ERROR);
            default -> new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        };
    }

    // 다른 엔드포인트의 INVALID_REQUEST 는 우리가 잘못 보낸 요청이라 AI 문구를 보여 주지 않는다
    private static boolean isCustomerFileError(String path, AiErrorResponse error) {
        return path.equals("/consult/files") && !isBlank(error.message());
    }

    private AiServerException accessException(String path, ResourceAccessException exception) {
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

    record Timeouts(Duration health, Duration consultFiles, Duration conversation, Duration architectures,
                    Duration terraform) {
        static final Timeouts SPEC = new Timeouts(Duration.ofSeconds(5), Duration.ofSeconds(120),
            Duration.ofSeconds(60), Duration.ofSeconds(300), Duration.ofSeconds(10));
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
