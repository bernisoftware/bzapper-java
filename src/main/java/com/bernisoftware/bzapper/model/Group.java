package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** A WhatsApp group. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Group(
        @JsonProperty("jid") String jid,
        @JsonProperty("name") String name,
        @JsonProperty("topic") String topic,
        @JsonProperty("owner") String owner,
        @JsonProperty("participants") List<GroupParticipant> participants,
        @JsonProperty("created_at") String createdAt) {
}
