package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A bZapper Connect connection: one of the partner's customers ({@code externalId})
 * linked to a bZapper account/project.
 *
 * <p>{@code apiKey} is only set by {@code exchangeCode} and {@code rotateConnectionKey}
 * (the OpenAPI {@code PartnerConnectionWithKey}): the raw {@code bz_live_...} key,
 * shown once — store it. It is {@code null} everywhere else.
 * {@code partnerName}/{@code partnerLogoUrl} are only filled by {@code listConnectedApps}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerConnection(
        @JsonProperty("id") String id,
        /** Your id for this customer. */
        @JsonProperty("external_id") String externalId,
        @JsonProperty("status") ConnectionStatus status,
        /** The customer's bZapper account (tenant). */
        @JsonProperty("account_id") String accountId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("customer") ConnectCustomer customer,
        @JsonProperty("numbers") List<ConnectionNumber> numbers,
        @JsonProperty("partner_name") String partnerName,
        @JsonProperty("partner_logo_url") String partnerLogoUrl,
        @JsonProperty("activated_at") String activatedAt,
        @JsonProperty("suspended_at") String suspendedAt,
        @JsonProperty("revoked_at") String revokedAt,
        @JsonProperty("created_at") String createdAt,
        /** Raw customer API key; only on exchange/rotate-key. */
        @JsonProperty("api_key") String apiKey) {

    /** Whether the customer's API key currently works. */
    public boolean isActive() {
        return status == ConnectionStatus.ACTIVE;
    }
}
