package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response from every {@code send*} call (the OpenAPI {@code MessageQueued}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SentMessage(
        @JsonProperty("message_id") String messageId,
        @JsonProperty("status") String status,
        @JsonProperty("client_reference") String clientReference) {
}
