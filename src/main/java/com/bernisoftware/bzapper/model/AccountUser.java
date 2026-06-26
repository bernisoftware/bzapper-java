package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An account user. role {@code admin} (everything) or {@code agent} (member — no billing). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountUser(
        @JsonProperty("id") String id,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("role") String role,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("email_verified_at") String emailVerifiedAt) {
}
