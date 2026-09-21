package com.bernisoftware.bzapper;

import java.time.Duration;

/**
 * HTTP 403 — the key cannot do this (role, plan or scope). The missing scope, when that is the reason, is in {@link #getRequiredScope()}.
 *
 * <p>Extends {@link BzapperException}: branch on {@link #getCode()}.
 */
public class PermissionDeniedException extends BzapperException {

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
    public PermissionDeniedException(String code, String message, int statusCode, String locale, String requestId,
            Duration retryAfter, String requiredScope, Object body, Throwable cause) {
        super(code, message, statusCode, locale, requestId, retryAfter, requiredScope, body, cause);
    }
}
