package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Common fields shared by every send call (the OpenAPI {@code SendBase}).
 *
 * <p>Only {@code to} is required. The remaining fields are optional and may be
 * left {@code null}. Use {@link #to(String)} for the simplest case and chain the
 * {@code with*} helpers to add more.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendOptions(
        @JsonProperty("to") String to,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("pool_id") String poolId,
        @JsonProperty("quoted_message_id") String quotedMessageId,
        @JsonProperty("client_reference") String clientReference,
        @JsonProperty("mentions") List<String> mentions,
        @JsonProperty("sticky") Boolean sticky,
        @JsonProperty("scheduled_at") String scheduledAt) {

    /** Start from a destination (E.164 phone or JID). */
    public static SendOptions to(String to) {
        return new SendOptions(to, null, null, null, null, null, null, null);
    }

    public SendOptions withInstanceId(String instanceId) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }

    public SendOptions withPoolId(String poolId) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }

    public SendOptions withQuotedMessageId(String quotedMessageId) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }

    public SendOptions withClientReference(String clientReference) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }

    public SendOptions withMentions(List<String> mentions) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }

    /**
     * Conversation affinity: with no instance_id/pool_id, reuses the number already
     * talking to {@code to}. Defaults to true server-side; set false to force rotation.
     */
    public SendOptions withSticky(Boolean sticky) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }

    /**
     * Schedule the send for a future RFC3339 timestamp. The number is picked at send
     * time. Max lead: Free 24h, Pro 30 days, 1 year with the extended-scheduling
     * add-on. Returns status {@code scheduled}. OTP cannot be scheduled.
     */
    public SendOptions withScheduledAt(String scheduledAt) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt);
    }
}
