package com.bernisoftware.bzapper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Your customer, as already authenticated in YOUR product (bZapper Connect).
 *
 * <p>{@code email} is required, plus {@code name} or {@code company}. The data is
 * trusted: the bZapper account is created inside the embedded component without
 * password, captcha or email confirmation (unless the email already has an
 * account, in which case a code is sent to it). Start with {@link #of(String, String)}
 * and chain the {@code with*} helpers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectCustomer(
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        /** E.164; pre-fills the WhatsApp number. */
        @JsonProperty("phone") String phone,
        /** Becomes the bZapper account and project name. */
        @JsonProperty("company") String company,
        /** ISO-3166 alpha-2; sets the currency (BR → BRL, Americas → USD, others → EUR). */
        @JsonProperty("country") String country,
        @JsonProperty("locale") String locale) {

    /** Start from the customer's name and email. */
    public static ConnectCustomer of(String name, String email) {
        return new ConnectCustomer(name, email, null, null, null, null);
    }

    public ConnectCustomer withName(String name) {
        return new ConnectCustomer(name, email, phone, company, country, locale);
    }

    public ConnectCustomer withEmail(String email) {
        return new ConnectCustomer(name, email, phone, company, country, locale);
    }

    public ConnectCustomer withPhone(String phone) {
        return new ConnectCustomer(name, email, phone, company, country, locale);
    }

    public ConnectCustomer withCompany(String company) {
        return new ConnectCustomer(name, email, phone, company, country, locale);
    }

    public ConnectCustomer withCountry(String country) {
        return new ConnectCustomer(name, email, phone, company, country, locale);
    }

    public ConnectCustomer withLocale(String locale) {
        return new ConnectCustomer(name, email, phone, company, country, locale);
    }
}
