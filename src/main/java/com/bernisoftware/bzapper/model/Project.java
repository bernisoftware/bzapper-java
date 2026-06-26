package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An isolated environment (numbers, inbox, keys, stats) within the account. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(
        @JsonProperty("id") String id,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("name") String name,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("color") String color,
        @JsonProperty("is_default") boolean isDefault,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt) {
}
