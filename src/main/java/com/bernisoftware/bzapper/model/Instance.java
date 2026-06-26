package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A WhatsApp number/instance of the tenant. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Instance(
        @JsonProperty("id") String id,
        @JsonProperty("phone") String phone,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("jid") String jid,
        @JsonProperty("status") String status,
        @JsonProperty("status_reason") String statusReason,
        @JsonProperty("proxy_url") String proxyUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt) {
}
