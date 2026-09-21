package com.bernisoftware.bzapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Encanamento HTTP/JSON/erros compartilhado por {@link BzapperClient} (API key
 * {@code bz_live_...}) e {@link BzapperPartner} (segredo {@code bz_partner_...}).
 * A única diferença entre os dois é o bearer; o resto — headers, timeouts,
 * novas tentativas, envelope de erro — tem que ser idêntico, por isso mora aqui
 * (padrão Berni Software r2, BRIEF §3–5).
 */
final class HttpTransport {

    static final int DEFAULT_MAX_RETRIES = 2;
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);
    private static final Pattern SECONDS = Pattern.compile("\\d+(\\.\\d+)?");

    /** Espera entre tentativas. Substituível nos testes (a suíte não pode dormir de verdade). */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final Sleeper REAL_SLEEP = duration -> {
        long millis = duration.toMillis();
        if (millis > 0) {
            Thread.sleep(millis);
        }
    };

    /** Um arquivo para {@code multipart/form-data}. */
    static final class FilePart {
        final String field;
        final String filename;
        final String contentType;
        final byte[] content;

        FilePart(String field, String filename, String contentType, byte[] content) {
            this.field = field;
            this.filename = Objects.requireNonNull(filename, "filename");
            this.contentType = contentType != null ? contentType : "application/octet-stream";
            this.content = Objects.requireNonNull(content, "content");
        }
    }

    private final String baseUrl;
    private final String bearer;
    private final String locale;
    private final String projectId;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final ObjectMapper mapper;
    private volatile Sleeper sleeper = REAL_SLEEP;

    HttpTransport(String baseUrl, String bearer, String locale, Duration timeout,
                  Duration connectTimeout, HttpClient httpClient) {
        this(baseUrl, bearer, locale, null, timeout, connectTimeout, httpClient, DEFAULT_MAX_RETRIES);
    }

    HttpTransport(String baseUrl, String bearer, String locale, String projectId, Duration timeout,
                  Duration connectTimeout, HttpClient httpClient, int maxRetries) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        Objects.requireNonNull(bearer, "apiKey");
        if (bearer.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey must not be empty");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.bearer = bearer;
        this.locale = locale;
        this.projectId = projectId;
        this.maxRetries = maxRetries;
        this.requestTimeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.http = httpClient != null
                ? httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Enums com @JsonEnumDefaultValue (ex.: ConnectionStatus) não quebram
                // quando a API ganha um valor novo.
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    }

    void setSleeper(Sleeper sleeper) {
        this.sleeper = sleeper == null ? REAL_SLEEP : sleeper;
    }

    int maxRetries() {
        return maxRetries;
    }

    // ── chamadas ─────────────────────────────────────────────────────────────────────────────────────────

    <T> T request(String method, String path, Object body, Class<T> type) {
        return request(method, path, body, type, (RequestOptions) null);
    }

    /** Like {@link #request(String, String, Object, Class)} with extra headers (e.g. Idempotency-Key). */
    <T> T request(String method, String path, Object body, Class<T> type, Map<String, String> headers) {
        String key = headers == null ? null : headers.get("Idempotency-Key");
        return request(method, path, body, type, key == null ? null : RequestOptions.idempotencyKey(key));
    }

    <T> T request(String method, String path, Object body, Class<T> type, RequestOptions options) {
        byte[] payload = body == null ? null : serialize(body);
        Response r = send(method, path, payload, body == null ? null : "application/json", options);
        return decode(r, type == null || type == Void.class ? null : mapper.constructType(type));
    }

    /** {@code multipart/form-data}: um arquivo + campos de texto opcionais. */
    <T> T multipart(String path, FilePart file, Map<String, String> fields, Class<T> type, RequestOptions options) {
        String boundary = "bzapper-" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (fields != null) {
                for (Map.Entry<String, String> e : fields.entrySet()) {
                    if (e.getValue() == null) {
                        continue;
                    }
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                            + quote(e.getKey()) + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + quote(file.field)
                    + "\"; filename=\"" + quote(file.filename) + "\"\r\nContent-Type: " + file.contentType
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(file.content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e); // ByteArrayOutputStream não lança
        }
        Response r = send("POST", path, out.toByteArray(), "multipart/form-data; boundary=" + boundary, options);
        return decode(r, type == null || type == Void.class ? null : mapper.constructType(type));
    }

    private static String quote(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "");
    }

    private static final class Response {
        final int status;
        final byte[] body;
        final String requestId;

        Response(int status, byte[] body, String requestId) {
            this.status = status;
            this.body = body;
            this.requestId = requestId;
        }
    }

    private Response send(String method, String path, byte[] payload, String contentType, RequestOptions options) {
        URI uri = URI.create(baseUrl + path);

        // Gerados UMA vez por chamada lógica e repetidos em toda nova tentativa: é o
        // que torna a repetição segura (a API devolve a resposta original com
        // Idempotent-Replayed: true).
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String idempotencyKey = null;
        if (isWrite(method)) {
            String own = options == null ? null : options.getIdempotencyKey();
            idempotencyKey = own != null && !own.isEmpty() ? own : UUID.randomUUID().toString();
        }
        Duration attemptTimeout = options != null && options.getTimeout() != null ? options.getTimeout() : requestTimeout;

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(attemptTimeout)
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                // Identifica SDK e versão para a API — é por ele que avisamos
                // você quando a versão que roda tem correção que exige
                // atualizar o código da integração.
                .header("X-Bzapper-Client", Version.CLIENT_ID)
                .header("User-Agent", Version.CLIENT_ID)
                .header("X-Request-Id", requestId);
        if (idempotencyKey != null) {
            rb.header("Idempotency-Key", idempotencyKey);
        }
        if (locale != null && !locale.isEmpty()) {
            rb.header("Accept-Language", locale);
        }
        if (projectId != null && !projectId.isEmpty()) {
            rb.header("X-Project-Id", projectId);
        }
        if (payload != null) {
            rb.header("Content-Type", contentType);
            rb.method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpRequest request = rb.build();

        for (int attempt = 0; ; attempt++) {
            BzapperException error;
            Duration retryAfter = null;
            try {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                byte[] data = response.body() == null ? new byte[0] : response.body();
                String headerRequestId = header(response, "X-Request-Id");
                String effectiveRequestId = headerRequestId != null ? headerRequestId : requestId;
                if (status >= 200 && status < 300) {
                    return new Response(status, data, effectiveRequestId);
                }
                retryAfter = parseRetryAfter(header(response, "Retry-After"));
                error = toException(status, data, effectiveRequestId, header(response, "X-Required-Scope"),
                        status == 429 ? retryAfter : null);
            } catch (HttpTimeoutException e) {
                error = new NetworkException(BzapperException.NETWORK_ERROR + ": timed out after "
                        + attemptTimeout.toMillis() + " ms on " + method + " " + path
                        + " (request_id " + requestId + ")", requestId, e);
            } catch (IOException e) {
                error = new NetworkException(BzapperException.NETWORK_ERROR + ": connection failed on "
                        + method + " " + path + ": " + e + " (request_id " + requestId + ")", requestId, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NetworkException(BzapperException.NETWORK_ERROR + ": interrupted on " + method + " "
                        + path + " (request_id " + requestId + ")", requestId, e);
            }

            if (attempt >= maxRetries || !isRetryable(error)) {
                throw error;
            }
            try {
                sleeper.sleep(backoff(attempt, retryAfter));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw error; // interrompida durante a espera: devolve o último erro real
            }
        }
    }

    private <T> T decode(Response r, JavaType type) {
        if (r.body.length == 0 || new String(r.body, StandardCharsets.UTF_8).trim().isEmpty()) {
            return null;
        }
        JsonNode tree;
        try {
            tree = mapper.readTree(r.body);
        } catch (IOException e) {
            throw invalidResponse(r, "the response body is not JSON", e);
        }
        if (type == null) {
            return null;
        }
        try {
            return mapper.convertValue(tree, type);
        } catch (IllegalArgumentException e) {
            throw invalidResponse(r, "unexpected response shape: " + e.getMessage(), e);
        }
    }

    private static BzapperException invalidResponse(Response r, String reason, Throwable cause) {
        return new BzapperException(BzapperException.INVALID_RESPONSE,
                BzapperException.INVALID_RESPONSE + ": " + reason + " (HTTP " + r.status + ")",
                r.status, null, r.requestId, null, null,
                new String(r.body, StandardCharsets.UTF_8), cause);
    }

    private byte[] serialize(Object body) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new BzapperException("serialization_error",
                    "Failed to serialize request body: " + e.getMessage(), 0, null, e);
        }
    }

    /** Espera antes da nova tentativa {@code attempt} (0 = a primeira nova tentativa). */
    static Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null) {
            if (retryAfter.isNegative()) {
                return Duration.ZERO;
            }
            return retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
        }
        double seconds = Math.min(8.0, 0.5 * Math.pow(2, attempt));
        double jitter = ThreadLocalRandom.current().nextDouble() * 0.25 * seconds;
        return Duration.ofNanos(Math.round((seconds + jitter) * 1_000_000_000L));
    }

    private static boolean isWrite(String method) {
        return method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE");
    }

    private static boolean isRetryable(BzapperException error) {
        int s = error.getStatusCode();
        return error instanceof NetworkException || s == 429 || s == 502 || s == 503 || s == 504;
    }

    private static String header(HttpResponse<?> response, String name) {
        for (String value : response.headers().allValues(name)) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /** {@code Retry-After} em segundos (inteiro ou decimal) ou data HTTP. {@code null} se ausente/ilegível. */
    static Duration parseRetryAfter(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (SECONDS.matcher(value).matches()) {
            double seconds = Double.parseDouble(value);
            return Duration.ofMillis(Math.round(Math.min(seconds, 86_400) * 1000));
        }
        try {
            ZonedDateTime date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration delta = Duration.between(ZonedDateTime.now(date.getZone()), date);
            return delta.isNegative() ? Duration.ZERO : delta;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ── erros ────────────────────────────────────────────────────────────────────────────────────────────

    /** Converte uma resposta fora de 2xx no {@link BzapperException} certo (BRIEF §4). */
    private BzapperException toException(int status, byte[] data, String requestId, String requiredScope,
                                         Duration retryAfter) {
        String code = null;
        String message = null;
        String errLocale = null;
        Object body = null;
        String text = data == null ? "" : new String(data, StandardCharsets.UTF_8);
        if (!text.trim().isEmpty()) {
            body = text;
            try {
                JsonNode node = mapper.readTree(text);
                body = mapper.convertValue(node, Object.class);
                if (node.isObject()) {
                    code = textOf(node, "code");
                    if (code == null) {
                        code = textOf(node, "error");
                    }
                    message = textOf(node, "message");
                    errLocale = textOf(node, "locale");
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Corpo não-JSON (proxy, balanceador…): cai no HTTP_<status> abaixo.
            }
        }
        if (code == null) {
            code = "HTTP_" + status;
        }
        if (message == null) {
            message = code;
        }

        if (status == 401) {
            return new AuthenticationException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        } else if (status == 403) {
            return new PermissionDeniedException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        } else if (status == 404) {
            return new NotFoundException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        } else if (status == 409) {
            return new ConflictException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        } else if (status == 400 || status == 422) {
            return new ValidationException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        } else if (status == 429) {
            return new RateLimitException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        } else if (status >= 500 && status <= 599) {
            return new ServerException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
        }
        return new BzapperException(code, message, status, errLocale, requestId, retryAfter, requiredScope, body, null);
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isEmpty() ? null : s;
    }

    /** Kept for source compatibility inside the package; prefer {@link Paths#seg} / {@link Paths#q}. */
    static String enc(String value) {
        return Paths.q(value);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
