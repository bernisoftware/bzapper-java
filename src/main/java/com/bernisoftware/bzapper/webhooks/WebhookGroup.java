package com.bernisoftware.bzapper.webhooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** WhatsApp group context, when the event happened in a group. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookGroup {

    private final String jid;
    private final String name;

    public WebhookGroup(@JsonProperty("jid") String jid,
                        @JsonProperty("name") String name) {
        this.jid = jid;
        this.name = name;
    }

    public String jid() {
        return jid;
    }

    public String name() {
        return name;
    }
}
