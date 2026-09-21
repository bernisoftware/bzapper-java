package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of {@code createConnectSession}. Hand {@code sessionToken} (valid 30 min) to
 * your front-end to open the embedded component
 * ({@code BzapperConnect.open({ session })}); never the partner secret.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectSession(
        @JsonProperty("session_token") String sessionToken,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("connection") PartnerConnection connection) {
}
