package com.bernisoftware.bzapper;

/**
 * Exception thrown by {@link BzapperClient} whenever the API returns a non-2xx
 * response.
 *
 * <p>The error body follows the bZapper error envelope:
 * {@code { "code": string, "message": string, "locale": string }}. Always branch
 * on the stable {@link #getCode() code} — never parse the human-readable
 * {@link #getMessage() message}, which is localized via {@code Accept-Language}.
 */
public class BzapperException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final int statusCode;
    private final String locale;

    public BzapperException(String code, String message, int statusCode, String locale) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.locale = locale;
    }

    public BzapperException(String code, String message, int statusCode, String locale, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = statusCode;
        this.locale = locale;
    }

    /** Stable, neutral error code (e.g. {@code instance_not_connected}). Use this in logic. */
    public String getCode() {
        return code;
    }

    /** HTTP status code of the failed response (0 if the failure was local, e.g. a transport error). */
    public int getStatusCode() {
        return statusCode;
    }

    /** Locale of the translated {@code message}, when provided by the server. */
    public String getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        return "BzapperException{code='" + code + "', statusCode=" + statusCode
                + ", message='" + getMessage() + "'}";
    }
}
