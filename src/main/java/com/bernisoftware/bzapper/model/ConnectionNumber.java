package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A WhatsApp number of the customer's project, as seen through a connection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectionNumber(
        @JsonProperty("id") String id,
        @JsonProperty("phone") String phone,
        @JsonProperty("status") String status) {
}
