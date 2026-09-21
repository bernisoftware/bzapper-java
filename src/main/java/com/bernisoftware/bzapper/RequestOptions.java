package com.bernisoftware.bzapper;

import java.time.Duration;
import java.util.Objects;

/**
 * Options for ONE call (immutable). Apply them with {@link BzapperClient#withOptions(RequestOptions)}
 * (or {@link BzapperPartner#withOptions(RequestOptions)}), which returns a view of the client
 * that uses these options on every call it makes — create one per logical call:
 *
 * <pre>{@code
 * client.withOptions(RequestOptions.idempotencyKey("order-1042-contact"))
 *       .createContact(Map.of("phone", "+5511999998888", "name", "Ana"));
 * }</pre>
 *
 * <p>Sends keep accepting the key through {@code SendOptions.withIdempotencyKey(...)}.
 */
public final class RequestOptions {

    private final String idempotencyKey;
    private final Duration timeout;

    private RequestOptions(String idempotencyKey, Duration timeout) {
        this.idempotencyKey = idempotencyKey;
        this.timeout = timeout;
    }

    /** New builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Shortcut: only the {@code Idempotency-Key}. */
    public static RequestOptions idempotencyKey(String key) {
        return builder().idempotencyKey(key).build();
    }

    /** Shortcut: only the per-attempt timeout. */
    public static RequestOptions timeout(Duration timeout) {
        return builder().timeout(timeout).build();
    }

    /**
     * Your own {@code Idempotency-Key} for writes (POST/PUT/PATCH/DELETE), sent verbatim.
     * Without it the SDK generates one per logical call and repeats it on retries.
     * Ignored on reads. {@code null} = generated.
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Per-attempt timeout for these calls (overrides the client's); {@code null} = the client's. */
    public Duration getTimeout() {
        return timeout;
    }

    /** Builder pre-filled with these options. */
    public Builder toBuilder() {
        return new Builder().idempotencyKey(idempotencyKey).timeout(timeout);
    }

    /** Builder for {@link RequestOptions}. */
    public static final class Builder {
        private String idempotencyKey;
        private Duration timeout;

        private Builder() {
        }

        /** Your own {@code Idempotency-Key} (up to 255 chars); {@code null} = the SDK generates one. */
        public Builder idempotencyKey(String key) {
            if (key != null && key.isEmpty()) {
                throw new IllegalArgumentException("idempotencyKey must not be empty (use null to let the SDK generate one)");
            }
            this.idempotencyKey = key;
            return this;
        }

        /** Per-attempt timeout (positive); {@code null} = the client's. */
        public Builder timeout(Duration timeout) {
            if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(idempotencyKey, timeout);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RequestOptions)) {
            return false;
        }
        RequestOptions other = (RequestOptions) o;
        return Objects.equals(idempotencyKey, other.idempotencyKey) && Objects.equals(timeout, other.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, timeout);
    }

    @Override
    public String toString() {
        return "RequestOptions{idempotencyKey=" + idempotencyKey + ", timeout=" + timeout + "}";
    }
}
