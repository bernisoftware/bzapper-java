package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A contact captured automatically from received conversations (shared across the account). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactRecord(
        @JsonProperty("id") String id,
        @JsonProperty("chat_jid") String chatJid,
        @JsonProperty("phone") String phone,
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("message_count") int messageCount,
        @JsonProperty("last_message_at") String lastMessageAt) {
}
