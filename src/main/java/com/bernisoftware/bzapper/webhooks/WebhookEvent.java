package com.bernisoftware.bzapper.webhooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed, typed webhook event (the delivered envelope).
 *
 * <p>Deserialized from the JSON envelope: {@code event_id} maps to {@link #id()}
 * and {@code event_type} to {@link #type()}. The untouched parsed JSON is also
 * available via {@link #raw()} for fields not surfaced as typed accessors.
 *
 * <p>Idempotency: each event carries a stable {@link #id()} — store processed ids
 * (Redis/DB) and skip duplicates, since the API may retry deliveries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookEvent {

    private final String id;
    private final String type;
    private final String timestamp;
    private final String instanceId;
    private final String clientReference;
    private final WebhookGroup group;
    private final WebhookSender sender;
    private final List<String> mentions;
    private final Map<String, Object> payload;
    private JsonNode raw;

    public WebhookEvent(
            @JsonProperty("event_id") String id,
            @JsonProperty("event_type") String type,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("client_reference") String clientReference,
            @JsonProperty("group") WebhookGroup group,
            @JsonProperty("sender") WebhookSender sender,
            @JsonProperty("mentions") List<String> mentions,
            @JsonProperty("payload") Map<String, Object> payload) {
        this.id = id != null ? id : "";
        this.type = type != null ? type : "";
        this.timestamp = timestamp;
        this.instanceId = instanceId;
        this.clientReference = clientReference;
        this.group = group;
        this.sender = sender;
        this.mentions = mentions != null ? mentions : new ArrayList<>();
        this.payload = payload != null ? payload : new LinkedHashMap<>();
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String timestamp() {
        return timestamp;
    }

    public String instanceId() {
        return instanceId;
    }

    public String clientReference() {
        return clientReference;
    }

    public WebhookGroup group() {
        return group;
    }

    public WebhookSender sender() {
        return sender;
    }

    public List<String> mentions() {
        return mentions;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    /** The untouched parsed JSON envelope (set by {@code Webhooks} after parsing). */
    public JsonNode raw() {
        return raw;
    }

    void setRaw(JsonNode raw) {
        this.raw = raw;
    }
}
