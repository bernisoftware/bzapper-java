package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lifecycle status of a bZapper Connect connection.
 *
 * <p>Only {@link #ACTIVE} means the customer's API key works. {@link #SUSPENDED}
 * means the customer's Pro plan is unpaid: the key answers HTTP 402
 * {@code connect_suspended} and resumes by itself once paid. {@link #REVOKED} is
 * final. A value this SDK version does not know yet maps to {@link #UNKNOWN}.
 */
public enum ConnectionStatus {
    /** The customer has no linked bZapper account yet. */
    @JsonProperty("pending_account")
    PENDING_ACCOUNT("pending_account"),
    /** Account linked; the Pro plan is not paid yet. */
    @JsonProperty("pending_payment")
    PENDING_PAYMENT("pending_payment"),
    /** Pro paid; no WhatsApp number connected yet. */
    @JsonProperty("pending_number")
    PENDING_NUMBER("pending_number"),
    /** Connected: the API key works. */
    @JsonProperty("active")
    ACTIVE("active"),
    /** Pro unpaid: the key answers 402 {@code connect_suspended} until paid. */
    @JsonProperty("suspended")
    SUSPENDED("suspended"),
    /** Ended (by the customer, the partner or account deletion). */
    @JsonProperty("revoked")
    REVOKED("revoked"),
    /** A status newer than this SDK version. */
    @JsonEnumDefaultValue
    UNKNOWN("unknown");

    private final String value;

    ConnectionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
