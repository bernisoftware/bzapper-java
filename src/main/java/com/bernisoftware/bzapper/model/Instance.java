package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A WhatsApp number/instance of the tenant. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Instance(
        @JsonProperty("id") String id,
        @JsonProperty("phone") String phone,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("jid") String jid,
        @JsonProperty("status") String status,
        @JsonProperty("status_reason") String statusReason,
        /** When a TEMPORARY ban expires (the number auto-reconnects); null if permanent/no ban. */
        @JsonProperty("banned_until") String bannedUntil,
        @JsonProperty("proxy_url") String proxyUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        /**
         * Sends rejected by WhatsApp in a row; resets to 0 on the first accepted
         * send. A connected number above zero is alive but not delivering.
         */
        @JsonProperty("consecutive_send_failures") Integer consecutiveSendFailures,
        /** Code WhatsApp returned on the last rejected send. */
        @JsonProperty("last_send_error_code") Integer lastSendErrorCode,
        @JsonProperty("last_send_failure_at") String lastSendFailureAt,
        @JsonProperty("last_send_error") String lastSendError) {

    /**
     * WhatsApp's anti-spam reach-out time-lock. A per-account limit, not an
     * infrastructure failure: the session stays healthy, replies to people who
     * messaged first still go through, and it usually clears within hours.
     */
    public static final int REACH_OUT_LOCK_CODE = 463;

    /** Whether WhatsApp is refusing this number's sends due to the time-lock. */
    public boolean isReachOutLocked() {
        return lastSendErrorCode != null
                && lastSendErrorCode == REACH_OUT_LOCK_CODE
                && consecutiveSendFailures != null
                && consecutiveSendFailures > 0;
    }
}
