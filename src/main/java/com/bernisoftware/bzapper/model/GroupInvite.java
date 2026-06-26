package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of {@code groupInvite}: the group's invite code and shareable link. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupInvite(
        @JsonProperty("code") String code,
        @JsonProperty("url") String url) {
}
