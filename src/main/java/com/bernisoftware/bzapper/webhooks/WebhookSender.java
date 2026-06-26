package com.bernisoftware.bzapper.webhooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Who sent/triggered the event (for message/group events). */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookSender {

    private final String jid;
    private final String lid;
    private final String name;

    public WebhookSender(@JsonProperty("jid") String jid,
                         @JsonProperty("lid") String lid,
                         @JsonProperty("name") String name) {
        this.jid = jid;
        this.lid = lid;
        this.name = name;
    }

    public String jid() {
        return jid;
    }

    public String lid() {
        return lid;
    }

    public String name() {
        return name;
    }
}
