package com.bernisoftware.bzapper;

import java.time.Duration;

/**
 * Exception thrown by {@link BzapperClient} / {@link BzapperPartner} whenever the API
 * returns a non-2xx response, a 2xx response that is not JSON, or the request fails
 * at the network level.
 *
 * <p>The error body follows the bZapper error envelope:
 * {@code { "code": string, "message": string, "locale": string }}. Always branch
 * on the stable {@link #getCode() code} — never parse the human-readable
 * {@link #getMessage() message}, which is localized via {@code Accept-Language}.
 * When talking to support, send the {@link #getRequestId() requestId}.
 *
 * <p>Typed subclasses (all extend this class, so existing {@code catch (BzapperException e)}
 * blocks keep catching everything): {@link AuthenticationException} (401),
 * {@link PermissionDeniedException} (403), {@link NotFoundException} (404),
 * {@link ConflictException} (409), {@link ValidationException} (400 and 422),
 * {@link RateLimitException} (429), {@link ServerException} (5xx) and
 * {@link NetworkException} (connection failure/timeout, status 0). Any other status —
 * and a 2xx body that is not JSON ({@link #INVALID_RESPONSE}) — comes as this base class.
 */
public class BzapperException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP 402 — bZapper Connect: the customer's Pro plan is unpaid, so the key issued to
     * the partner is suspended. Not final: it works again by itself once paid
     * ({@code connect.resumed}). Don't discard the key.
     */
    public static final String CONNECT_SUSPENDED = "connect_suspended";

    /**
     * HTTP 401 — bZapper Connect: the connection was ended (by the customer, the partner
     * or account deletion). Final: the key never works again; start a new session.
     */
    public static final String CONNECT_REVOKED = "connect_revoked";

    /** Code of a 2xx response whose non-empty body is not JSON (proxy, captive portal…). */
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    /** Code of every {@link NetworkException} (connection failure, timeout, interruption). */
    public static final String NETWORK_ERROR = "NETWORK_ERROR";

    private final String code;
    private final int statusCode;
    private final String locale;
    private final String requestId;
    private final Duration retryAfter;
    private final String requiredScope;
    // The decoded error body (Map/List/String/…); never part of the stable contract.
    @SuppressWarnings("serial")
    private final transient Object body;

    public BzapperException(String code, String message, int statusCode, String locale) {
        this(code, message, statusCode, locale, null, null, null, null, null);
    }

    public BzapperException(String code, String message, int statusCode, String locale, Throwable cause) {
        this(code, message, statusCode, locale, null, null, null, null, cause);
    }

    /**
     * Full constructor (also handy to simulate API failures in your own tests).
     *
     * @param code          stable error code
     * @param message       human-readable message
     * @param statusCode    HTTP status ({@code 0} on a network error)
     * @param locale        locale of {@code message}, when provided by the server
     * @param requestId     the request id (response {@code X-Request-Id}, else the one the SDK sent)
     * @param retryAfter    wait requested by the API ({@code Retry-After}, only on 429); may be null
     * @param requiredScope scope the key lacks ({@code X-Required-Scope}, only on 403); may be null
     * @param body          the decoded error body; may be null
     * @param cause         original cause; may be null
     */
    public BzapperException(String code, String message, int statusCode, String locale, String requestId,
                            Duration retryAfter, String requiredScope, Object body, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = statusCode;
        this.locale = locale;
        this.requestId = requestId;
        this.retryAfter = retryAfter;
        this.requiredScope = requiredScope;
        this.body = body;
    }

    /** Stable, neutral error code (e.g. {@code instance_not_connected}). Use this in logic. */
    public String getCode() {
        return code;
    }

    /** HTTP status code of the failed response (0 if the failure was local, e.g. a transport error). */
    public int getStatusCode() {
        return statusCode;
    }

    /** Same as {@link #getStatusCode()} (the name used across the Berni Software SDKs). */
    public int getStatus() {
        return statusCode;
    }

    /** Locale of the translated {@code message}, when provided by the server. */
    public String getLocale() {
        return locale;
    }

    /**
     * Request id — send it to support. The response {@code X-Request-Id} header, else the
     * {@code X-Request-Id} the SDK sent (the API echoes the client's). Always set on
     * exceptions thrown by the SDK, including {@link NetworkException}.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Wait requested by the API in {@code Retry-After} — only on 429 (the SDK already
     * waited and retried before throwing). {@code null} otherwise.
     */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    /** Scope the API key lacks, from {@code X-Required-Scope} (403 of scope only); else {@code null}. */
    public String getRequiredScope() {
        return requiredScope;
    }

    /**
     * The decoded error body: a {@code Map<String, Object>} for JSON objects (structured
     * detail some errors carry), the raw text for a non-JSON body, or {@code null}.
     */
    public Object getBody() {
        return body;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{code='" + code + "', statusCode=" + statusCode
                + ", message='" + getMessage() + "'"
                + (requestId != null ? ", requestId='" + requestId + "'" : "") + "}";
    }
}
