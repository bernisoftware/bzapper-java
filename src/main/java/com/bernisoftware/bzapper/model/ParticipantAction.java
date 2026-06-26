package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Action for {@code updateGroupParticipants}. */
public enum ParticipantAction {
    @JsonProperty("add")
    ADD("add"),
    @JsonProperty("remove")
    REMOVE("remove"),
    @JsonProperty("promote")
    PROMOTE("promote"),
    @JsonProperty("demote")
    DEMOTE("demote");

    private final String value;

    ParticipantAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
