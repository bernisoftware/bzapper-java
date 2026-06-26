package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A single button for {@code sendButtons}. {@code id} is optional; {@code title} is required. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Button(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title) {

    public static Button of(String title) {
        return new Button(null, title);
    }

    public static Button of(String id, String title) {
        return new Button(id, title);
    }
}
