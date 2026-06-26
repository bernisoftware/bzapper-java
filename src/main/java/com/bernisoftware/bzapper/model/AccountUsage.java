package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Aggregated account usage plus a per-project breakdown (admin). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountUsage(
        @JsonProperty("account") Map<String, Object> account,
        @JsonProperty("projects") List<ProjectUsage> projects) {
}
