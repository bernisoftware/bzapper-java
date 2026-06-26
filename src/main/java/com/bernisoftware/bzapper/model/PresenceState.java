package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Chat presence state for {@code presenceChat}. */
public enum PresenceState {
    @JsonProperty("typing")
    TYPING("typing"),
    @JsonProperty("recording")
    RECORDING("recording"),
    @JsonProperty("paused")
    PAUSED("paused");

    private final String value;

    PresenceState(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
