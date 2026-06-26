package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Role of an API key / user (super_admin is granted via admin token, not here). */
public enum Role {
    @JsonProperty("admin")
    ADMIN("admin"),
    @JsonProperty("agent")
    AGENT("agent");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
