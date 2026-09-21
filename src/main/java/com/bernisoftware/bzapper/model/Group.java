package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** A WhatsApp group. {@code size} is the participant count. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Group(
        @JsonProperty("jid") String jid,
        @JsonProperty("name") String name,
        @JsonProperty("topic") String topic,
        @JsonProperty("owner") String owner,
        @JsonProperty("participants") List<GroupParticipant> participants,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("size") Integer size) {

    @JsonCreator
    public Group {
    }

    /** Original constructor (without {@code size}), kept for compatibility. */
    public Group(String jid, String name, String topic, String owner,
                 List<GroupParticipant> participants, String createdAt) {
        this(jid, name, topic, owner, participants, createdAt, null);
    }
}
