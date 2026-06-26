package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Profile fields for {@code setProfile} ({@code PATCH /instances/{id}/profile}).
 *
 * <p>All fields are optional; only the ones you set are sent. {@code picture} is a
 * base64-encoded image. Build fluently from {@link #empty()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileUpdate(
        @JsonProperty("display_name") String displayName,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("picture") String picture) {

    /** An empty update to chain {@code with*} helpers onto. */
    public static ProfileUpdate empty() {
        return new ProfileUpdate(null, null, null);
    }

    public ProfileUpdate withDisplayName(String displayName) {
        return new ProfileUpdate(displayName, statusMessage, picture);
    }

    public ProfileUpdate withStatusMessage(String statusMessage) {
        return new ProfileUpdate(displayName, statusMessage, picture);
    }

    /** Base64-encoded profile picture. */
    public ProfileUpdate withPicture(String picture) {
        return new ProfileUpdate(displayName, statusMessage, picture);
    }
}
