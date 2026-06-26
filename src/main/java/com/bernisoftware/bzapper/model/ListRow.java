package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A row inside a {@link ListSection} for {@code sendList}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListRow(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description) {

    public static ListRow of(String title) {
        return new ListRow(null, title, null);
    }

    public static ListRow of(String id, String title, String description) {
        return new ListRow(id, title, description);
    }
}
