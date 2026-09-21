package com.bernisoftware.bzapper;

import java.time.Duration;

/**
 * HTTP 400 or 422 — the request was rejected as invalid (e.g. {@code invalid_body}, {@code idempotency_key_reused}).
 *
 * <p>Extends {@link BzapperException}: branch on {@link #getCode()}.
 */
public class ValidationException extends BzapperException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the error (also handy to simulate API failures in your own tests).
     *
     * @param code          stable error code
     * @param message       human-readable message
     * @param statusCode    HTTP status
     * @param locale        locale of {@code message}; may be null
     * @param requestId     request id; may be null
     * @param retryAfter    {@code Retry-After} (429 only); may be null
     * @param requiredScope {@code X-Required-Scope} (403 only); may be null
     * @param body          decoded error body; may be null
     * @param cause         original cause; may be null
     */
    public ValidationException(String code, String message, int statusCode, String locale, String requestId,
            Duration retryAfter, String requiredScope, Object body, Throwable cause) {
        super(code, message, statusCode, locale, requestId, retryAfter, requiredScope, body, cause);
    }
}
