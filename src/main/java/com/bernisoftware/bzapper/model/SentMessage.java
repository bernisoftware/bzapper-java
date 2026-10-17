package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from every {@code send*} call (the OpenAPI {@code MessageQueued}).
 *
 * @param delivery present ONLY when the platform took a path other than the one asked for
 *                 (today: the 24-hour window was closed, so the free text went out as the
 *                 project fallback template); {@code null} otherwise
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SentMessage(
        @JsonProperty("message_id") String messageId,
        @JsonProperty("status") String status,
        @JsonProperty("client_reference") String clientReference,
        @JsonProperty("delivery") @JsonInclude(JsonInclude.Include.NON_NULL) DeliveryInfo delivery) {

    /** The pre-{@code delivery} constructor, kept for source/binary compatibility. */
    public SentMessage(String messageId, String status, String clientReference) {
        this(messageId, status, clientReference, null);
    }
}
