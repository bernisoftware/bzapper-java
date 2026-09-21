package com.bernisoftware.bzapper;

/**
 * Connection failure, timeout or interrupted call: {@code getStatusCode() == 0} and
 * {@code getCode() == "NETWORK_ERROR"}. {@link #getRequestId()} is the
 * {@code X-Request-Id} the SDK sent (same on every retry). The SDK already retried
 * (up to {@code maxRetries}) before throwing.
 */
public class NetworkException extends BzapperException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the network error.
     *
     * @param message   human-readable message
     * @param requestId the {@code X-Request-Id} that was sent
     * @param cause     original cause ({@code ConnectException}, {@code HttpTimeoutException}…)
     */
    public NetworkException(String message, String requestId, Throwable cause) {
        super(NETWORK_ERROR, message, 0, null, requestId, null, null, null, cause);
    }
}
