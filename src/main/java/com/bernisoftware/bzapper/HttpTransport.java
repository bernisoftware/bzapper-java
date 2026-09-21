package com.bernisoftware.bzapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Encanamento HTTP/JSON/erros compartilhado por {@link BzapperClient} (API key
 * {@code bz_live_...}) e {@link BzapperPartner} (segredo {@code bz_partner_...}).
 * A única diferença entre os dois é o bearer; o resto — headers, timeouts,
 * envelope de erro — tem que ser idêntico, por isso mora aqui.
 */
final class HttpTransport {

    private final String baseUrl;
    private final String bearer;
    private final String locale;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    HttpTransport(String baseUrl, String bearer, String locale, Duration timeout,
                  Duration connectTimeout, HttpClient httpClient) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.bearer = bearer;
        this.locale = locale;
        this.requestTimeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.http = httpClient != null
                ? httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(10))
                        .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Enums com @JsonEnumDefaultValue (ex.: ConnectionStatus) não quebram
                // quando a API ganha um valor novo.
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    }

    <T> T request(String method, String path, Object body, Class<T> type) {
        return request(method, path, body, type, null);
    }

    /** Like {@link #request(String, String, Object, Class)} with extra headers (e.g. Idempotency-Key). */
    <T> T request(String method, String path, Object body, Class<T> type, Map<String, String> headers) {
        return request(method, path, body, type == Void.class ? null : mapper.constructType(type), headers);
    }

    private <T> T request(String method, String path, Object body, JavaType type) {
        return request(method, path, body, type, null);
    }

    private <T> T request(String method, String path, Object body, JavaType type, Map<String, String> headers) {
        byte[] payload = serialize(body);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                // Identifica SDK e versão para a API — é por ele que avisamos
                // você quando a versão que roda tem correção que exige
                // atualizar o código da integração.
                .header("X-Bzapper-Client", Version.CLIENT_ID);

        if (locale != null && !locale.isEmpty()) {
            rb.header("Accept-Language", locale);
        }
        if (headers != null) {
            headers.forEach(rb::header);
        }

        if (body != null) {
            rb.header("Content-Type", "application/json");
            rb.method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<byte[]> response;
        try {
            response = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new BzapperException("network_error", "HTTP request failed: " + e.getMessage(),
                    0, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BzapperException("interrupted", "HTTP request interrupted", 0, null, e);
        }

        int status = response.statusCode();
        byte[] data = response.body();

        if (status < 200 || status >= 300) {
            throw toException(status, data);
        }

        return deserialize(data, type);
    }

    private byte[] serialize(Object body) {
        if (body == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new BzapperException("serialization_error",
                    "Failed to serialize request body: " + e.getMessage(), 0, null, e);
        }
    }

    private <T> T deserialize(byte[] data, JavaType type) {
        if (type == null || data == null || data.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(data, type);
        } catch (IOException e) {
            throw new BzapperException("deserialization_error",
                    "Failed to parse response body: " + e.getMessage(), 0, null, e);
        }
    }

    private BzapperException toException(int status, byte[] data) {
        String code = "http_error";
        String message = "HTTP " + status;
        String errLocale = null;
        if (data != null && data.length > 0) {
            try {
                JsonNode node = mapper.readTree(data);
                if (node.hasNonNull("code")) code = node.get("code").asText();
                if (node.hasNonNull("message")) message = node.get("message").asText();
                if (node.hasNonNull("locale")) errLocale = node.get("locale").asText();
            } catch (IOException ignored) {
                // Non-JSON error body; keep generic code/message.
            }
        }
        return new BzapperException(code, message, status, errLocale);
    }

    static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
