package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The partner a {@code bz_partner_...} secret belongs to (bZapper Connect). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Partner(
        @JsonProperty("id") String id,
        @JsonProperty("slug") String slug,
        @JsonProperty("name") String name,
        @JsonProperty("logo_url") String logoUrl,
        /** Origins allowed to open the embedded component. */
        @JsonProperty("allowed_origins") List<String> allowedOrigins,
        /** Where connect.* and forwarded project events are delivered. */
        @JsonProperty("webhook_url") String webhookUrl,
        /** Scopes of the API keys issued to this partner. */
        @JsonProperty("key_scopes") List<String> keyScopes) {
}
