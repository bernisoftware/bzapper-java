package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Result of {@code applyBrand}: how many connected numbers got the "About" applied. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrandApplyResult(
        @JsonProperty("applied") int applied,
        @JsonProperty("skipped") List<String> skipped,
        @JsonProperty("total") int total) {
}
