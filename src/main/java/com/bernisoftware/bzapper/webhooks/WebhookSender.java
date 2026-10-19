package com.bernisoftware.bzapper.webhooks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Who sent/triggered the event (for message/group events). */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookSender {

    private final String jid;
    private final String lid;
    private final String name;
    private final String phone;
    private final String bsuid;

    public WebhookSender(String jid, String lid, String name) {
        this(jid, lid, name, null);
    }

    public WebhookSender(String jid, String lid, String name, String phone) {
        this(jid, lid, name, phone, null);
    }

    @JsonCreator
    public WebhookSender(@JsonProperty("jid") String jid,
                         @JsonProperty("lid") String lid,
                         @JsonProperty("name") String name,
                         @JsonProperty("phone") String phone,
                         @JsonProperty("bsuid") String bsuid) {
        this.jid = jid;
        this.lid = lid;
        this.name = name;
        this.phone = phone;
        this.bsuid = bsuid;
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

    /**
     * Sender's phone (+DDIdigits) when known; {@code null} when the person only
     * arrived by {@code @lid} and the LID→phone map doesn't know it yet.
     */
    public String phone() {
        return phone;
    }

    /** Meta business-scoped user ID (official rail); {@code null} when absent. */
    public String bsuid() {
        return bsuid;
    }
}
