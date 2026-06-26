package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A member of a WhatsApp group. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupParticipant(
        @JsonProperty("jid") String jid,
        @JsonProperty("is_admin") Boolean isAdmin,
        @JsonProperty("is_super_admin") Boolean isSuperAdmin) {
}
