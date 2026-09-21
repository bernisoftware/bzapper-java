package com.bernisoftware.bzapper.webhooks;

import com.bernisoftware.bzapper.model.ConnectionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code connection} block of a partner (bZapper Connect) delivery: which of the
 * partner's customers the event is about.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookConnection {

    private final String id;
    private final String externalId;
    private final String accountId;
    private final String projectId;
    private final ConnectionStatus status;

    public WebhookConnection(@JsonProperty("id") String id,
                             @JsonProperty("external_id") String externalId,
                             @JsonProperty("account_id") String accountId,
                             @JsonProperty("project_id") String projectId,
                             @JsonProperty("status") ConnectionStatus status) {
        this.id = id;
        this.externalId = externalId;
        this.accountId = accountId;
        this.projectId = projectId;
        this.status = status;
    }

    public String id() {
        return id;
    }

    /** Your id for this customer (the {@code externalId} of the session). */
    public String externalId() {
        return externalId;
    }

    public String accountId() {
        return accountId;
    }

    public String projectId() {
        return projectId;
    }

    public ConnectionStatus status() {
        return status;
    }
}
