package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of {@code connectInstance}: a QR payload or an 8-char pairing code. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectResult(
        @JsonProperty("status") String status,
        @JsonProperty("qr_code") String qrCode,
        @JsonProperty("pair_code") String pairCode) {
}
