package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The numbers' identity (brand kit + "About"). Lives on the project.
 *
 * <p>Used both as the {@code getBrand} response and the {@code setBrand} request
 * body. All fields are optional; only the ones you set are sent. Build fluently
 * from {@link #empty()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrandProfile(
        @JsonProperty("about") String about,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("website") String website,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("address") String address,
        @JsonProperty("description") String description) {

    /** An empty profile to chain {@code with*} helpers onto. */
    public static BrandProfile empty() {
        return new BrandProfile(null, null, null, null, null, null, null, null);
    }

    public BrandProfile withAbout(String about) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withDisplayName(String displayName) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withLogoUrl(String logoUrl) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withWebsite(String website) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withEmail(String email) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withPhone(String phone) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withAddress(String address) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }

    public BrandProfile withDescription(String description) {
        return new BrandProfile(about, displayName, logoUrl, website, email, phone, address, description);
    }
}
