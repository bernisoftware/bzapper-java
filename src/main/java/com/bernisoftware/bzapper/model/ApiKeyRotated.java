package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Result of {@code rotateKey}. The raw {@code apiKey} of the NEW key is shown only once and is
 * never recoverable — store it now.
 *
 * <p>{@code key} and {@code previousKey} carry the key metadata as the API returns it (never the
 * secret): {@code id}, {@code name}, {@code role}, {@code scopes}, {@code project_id},
 * {@code created_at}, {@code last_used_at}, {@code revoked_at} and, since the rotation,
 * {@code expires_at} (when the old key stops working) and {@code rotated_to} (the id of the key
 * that replaced it).
 *
 * @param apiKey          the RAW new key — shown once
 * @param key             the new key's metadata
 * @param previousKey     the rotated key's metadata (may be null)
 * @param oldKeyExpiresAt RFC 3339 instant when the old key stops working; null when it was
 *                        revoked immediately ({@code revokeInSeconds = 0})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiKeyRotated(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("key") Map<String, Object> key,
        @JsonProperty("previous_key") Map<String, Object> previousKey,
        @JsonProperty("old_key_expires_at") String oldKeyExpiresAt) {
}
