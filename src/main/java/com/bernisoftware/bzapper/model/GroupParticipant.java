package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A member of a WhatsApp group.
 *
 * <p>{@code phone} (+DDIdigits) and {@code lid} are filled when known: in
 * LID-addressed groups {@code jid} is the {@code @lid} and only {@code phone}
 * identifies the person.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupParticipant(
        @JsonProperty("jid") String jid,
        @JsonProperty("is_admin") Boolean isAdmin,
        @JsonProperty("is_super_admin") Boolean isSuperAdmin,
        @JsonProperty("phone") String phone,
        @JsonProperty("lid") String lid) {

    @JsonCreator
    public GroupParticipant {
    }

    /** Original constructor (without {@code phone}/{@code lid}), kept for compatibility. */
    public GroupParticipant(String jid, Boolean isAdmin, Boolean isSuperAdmin) {
        this(jid, isAdmin, isSuperAdmin, null, null);
    }
}
