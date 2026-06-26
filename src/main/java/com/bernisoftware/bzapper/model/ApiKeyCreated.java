package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Result of {@code createKey}. The raw {@code apiKey} is shown only once and is
 * never recoverable — store it now.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiKeyCreated(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("key") Map<String, Object> key) {
}
