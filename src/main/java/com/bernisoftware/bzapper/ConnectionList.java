package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.PartnerConnection;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** Envelope {@code {"data": [...]}} das listagens de conexões (parceiro e /me/connections). */
@JsonIgnoreProperties(ignoreUnknown = true)
record ConnectionList(@JsonProperty("data") List<PartnerConnection> data) {
    ConnectionList {
        data = data != null ? data : new ArrayList<>();
    }
}
