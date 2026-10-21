package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A number's availability ({@code GET /instances/{id}/uptime}): share of time connected
 * over 24h, 7d and 30d, since when it is in its current status, and the latest changes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstanceUptime(
        @JsonProperty("instance_id") String instanceId,
        /** Current status. */
        @JsonProperty("status") String status,
        /** Since when the number is in its current status. */
        @JsonProperty("since") String since,
        /** First recorded status change; a window that goes back further shrinks to it. */
        @JsonProperty("tracked_since") String trackedSince,
        @JsonProperty("windows") Windows windows,
        /** Latest status changes, newest first (up to 20). */
        @JsonProperty("events") List<Event> events) {

    /**
     * Percent of time connected (0–100, one decimal; warming counts as connected).
     * {@code null} while the history does not cover the window yet.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Windows(
            @JsonProperty("24h") Double last24h,
            @JsonProperty("7d") Double last7d,
            @JsonProperty("30d") Double last30d) {
    }

    /** One status change. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            @JsonProperty("status") String status,
            @JsonProperty("reason") String reason,
            @JsonProperty("at") String at) {
    }
}
