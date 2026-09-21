package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Common fields shared by every send call (the OpenAPI {@code SendBase}).
 *
 * <p>Only {@code to} is required. The remaining fields are optional and may be
 * left {@code null}. Use {@link #to(String)} for the simplest case and chain the
 * {@code with*} helpers to add more.
 *
 * <p>{@code mentions} accepts JIDs or plain phones ({@code "5511..."},
 * {@code "+55 11 9..."}).
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
        @JsonProperty("scheduled_at") String scheduledAt,
        @JsonProperty("quoted_participant") String quotedParticipant,
        @JsonIgnore String idempotencyKey,
        @JsonProperty("force") Boolean force,
        @JsonProperty("groups") List<String> groups,
        @JsonProperty("tags") List<String> tags) {

    /**
     * Constructor of 0.6.x (without {@code force}/{@code groups}/{@code tags}), kept so
     * existing code keeps compiling.
     */
    public SendOptions(String to, String instanceId, String poolId, String quotedMessageId,
                       String clientReference, List<String> mentions, Boolean sticky, String scheduledAt,
                       String quotedParticipant, String idempotencyKey) {
        this(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt,
                quotedParticipant, idempotencyKey, null, null, null);
    }

    /**
     * Original constructor (without {@code quotedParticipant}/{@code idempotencyKey}),
     * kept so existing code keeps compiling.
     */
    public SendOptions(String to, String instanceId, String poolId, String quotedMessageId,
                       String clientReference, List<String> mentions, Boolean sticky, String scheduledAt) {
        this(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, null, null, null, null, null);
    }

    /** Start from a destination (E.164 phone or JID). */
    public static SendOptions to(String to) {
        return new SendOptions(to, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public SendOptions withInstanceId(String instanceId) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    public SendOptions withPoolId(String poolId) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    public SendOptions withQuotedMessageId(String quotedMessageId) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /**
     * Author (phone or JID) of the quoted/reacted message. Only needed in groups
     * when that message is not in the bZapper history (otherwise the author comes
     * from history).
     */
    public SendOptions withQuotedParticipant(String quotedParticipant) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    public SendOptions withClientReference(String clientReference) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /** People mentioned (groups): JIDs or plain phones. */
    public SendOptions withMentions(List<String> mentions) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /**
     * Conversation affinity: with no instance_id/pool_id, reuses the number already
     * talking to {@code to}. Defaults to true server-side; set false to force rotation.
     */
    public SendOptions withSticky(Boolean sticky) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /**
     * Schedule the send for a future RFC3339 timestamp. The number is picked at send
     * time. Max lead: Free 24h, Pro 30 days, 1 year with the extended-scheduling
     * add-on. Returns status {@code scheduled}. OTP cannot be scheduled.
     */
    public SendOptions withScheduledAt(String scheduledAt) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /**
     * Sent as the {@code Idempotency-Key} header (never in the body), up to 255
     * chars. Repeating a send with the same key within 24h (same account) returns
     * the SAME response without sending again — safe retries after timeouts.
     * Same key with a different body → 422 {@code idempotency_key_reused}; first
     * call still running → 409 {@code idempotency_in_progress}.
     */
    public SendOptions withIdempotencyKey(String idempotencyKey) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /**
     * When true, bypasses an INFERRED suppression (e.g. opt-out inferred from behaviour)
     * for this send. Does NOT override an explicit suppression entry — use with care and
     * only with a lawful basis.
     */
    public SendOptions withForce(Boolean force) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /**
     * Contact-group keys stamped onto the recipient contact when this send resolves
     * (see {@code listContactGroups}). Does NOT send to a WhatsApp group.
     */
    public SendOptions withGroups(List<String> groups) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }

    /** Tag keys stamped onto the recipient contact when this send resolves (see {@code listTags}). */
    public SendOptions withTags(List<String> tags) {
        return new SendOptions(to, instanceId, poolId, quotedMessageId, clientReference, mentions, sticky, scheduledAt, quotedParticipant, idempotencyKey, force, groups, tags);
    }
}
