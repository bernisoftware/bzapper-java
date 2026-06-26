package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** A section of rows for {@code sendList}. {@code title} is optional; {@code rows} is required. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListSection(
        @JsonProperty("title") String title,
        @JsonProperty("rows") List<ListRow> rows) {

    public static ListSection of(String title, List<ListRow> rows) {
        return new ListSection(title, rows);
    }

    public static ListSection of(List<ListRow> rows) {
        return new ListSection(null, rows);
    }
}
