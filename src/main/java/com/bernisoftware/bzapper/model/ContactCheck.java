package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A single result of {@code contactsCheck}: whether a phone is on WhatsApp. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactCheck(
        @JsonProperty("phone") String phone,
        @JsonProperty("exists") Boolean exists,
        @JsonProperty("jid") String jid) {
}
