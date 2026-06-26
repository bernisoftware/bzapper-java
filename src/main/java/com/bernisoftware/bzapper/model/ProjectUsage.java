package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Per-project usage breakdown inside {@link AccountUsage}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectUsage(
        @JsonProperty("project_id") String projectId,
        @JsonProperty("name") String name,
        @JsonProperty("numbers") int numbers,
        @JsonProperty("total") int total,
        @JsonProperty("sent") int sent,
        @JsonProperty("received") int received) {
}
