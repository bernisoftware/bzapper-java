package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Media payload for image/video/document/audio/sticker messages.
 *
 * <p>Provide either {@code url} <b>or</b> {@code base64}, never both — or, on the official
 * rail, {@code id}: a file already stored at Meta by {@code uploadOfficialMedia} (sent without
 * uploading again; valid 30 days, only from the number that uploaded it). Set
 * {@code ptt = true} on audio to send it as a voice note.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaInput(
        @JsonProperty("url") String url,
        @JsonProperty("base64") String base64,
        @JsonProperty("caption") String caption,
        @JsonProperty("filename") String filename,
        @JsonProperty("mimetype") String mimetype,
        @JsonProperty("ptt") Boolean ptt,
        @JsonProperty("id") String id) {

    /**
     * The constructor from before {@code id} existed — kept so code that builds
     * {@code MediaInput} directly keeps compiling.
     */
    public MediaInput(String url, String base64, String caption, String filename, String mimetype, Boolean ptt) {
        this(url, base64, caption, filename, mimetype, ptt, null);
    }

    /** Media referenced by a remote URL. */
    public static MediaInput url(String url) {
        return new MediaInput(url, null, null, null, null, null, null);
    }

    /** Media referenced by a remote URL, with a caption. */
    public static MediaInput url(String url, String caption) {
        return new MediaInput(url, null, caption, null, null, null, null);
    }

    /** Media supplied inline as a base64 string. */
    public static MediaInput base64(String base64) {
        return new MediaInput(null, base64, null, null, null, null, null);
    }

    /** Official rail: a file already stored at Meta (the id from {@code uploadOfficialMedia}). */
    public static MediaInput stored(String id) {
        return new MediaInput(null, null, null, null, null, null, id);
    }

    public MediaInput withCaption(String caption) {
        return new MediaInput(url, base64, caption, filename, mimetype, ptt, id);
    }

    public MediaInput withFilename(String filename) {
        return new MediaInput(url, base64, caption, filename, mimetype, ptt, id);
    }

    public MediaInput withMimetype(String mimetype) {
        return new MediaInput(url, base64, caption, filename, mimetype, ptt, id);
    }

    public MediaInput asVoiceNote() {
        return new MediaInput(url, base64, caption, filename, mimetype, true, id);
    }
}
