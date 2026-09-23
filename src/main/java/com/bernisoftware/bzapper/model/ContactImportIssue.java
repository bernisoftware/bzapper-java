package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row of {@code importContacts} that was skipped or failed — the whole call never fails
 * because of a single bad row.
 *
 * @param index  position of the row in the list that was sent (0-based)
 * @param phone  the row's phone, when it had a readable one
 * @param reason stable machine code: errors {@code phone_required}, {@code invalid_phone},
 *               {@code invalid_email}, {@code write_failed}, {@code taxonomy_failed}; skips
 *               {@code duplicate_phone}, {@code suppressed}, {@code opted_out}, {@code blocked},
 *               {@code unreachable}, {@code deleted}. Branch on this, never on {@code detail}.
 * @param detail free-form extra context (may be null)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactImportIssue(
        @JsonProperty("index") int index,
        @JsonProperty("phone") String phone,
        @JsonProperty("reason") String reason,
        @JsonProperty("detail") String detail) {
}
