package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How a send was actually delivered, when the platform took a path other than the one
 * asked for ({@link SentMessage#delivery()}). The platform decides, but it does not hide.
 *
 * @param via      {@code template_fallback} — the window was closed and the fallback template went out
 * @param template name of the template actually sent
 * @param note     one sentence explaining what happened, safe to show to a person
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryInfo(
        @JsonProperty("via") String via,
        @JsonProperty("template") String template,
        @JsonProperty("note") String note) {
}
