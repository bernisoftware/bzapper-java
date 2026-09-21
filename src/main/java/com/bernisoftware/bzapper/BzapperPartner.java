package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.ConnectCustomer;
import com.bernisoftware.bzapper.model.ConnectSession;
import com.bernisoftware.bzapper.model.Partner;
import com.bernisoftware.bzapper.model.PartnerConnection;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Partner client for <b>bZapper Connect</b>: lets your software's customers subscribe
 * to bZapper Pro and connect WhatsApp inside your product, and hands you back an API
 * key authorized by the customer.
 *
 * <p>Authenticates with the partner secret ({@code bz_partner_...}). Use it from your
 * <b>backend only</b> — the secret must never reach a browser. The flow:
 *
 * <pre>{@code
 * BzapperPartner partner = new BzapperPartner("bz_partner_...");
 *
 * // 1. backend: open a session for YOUR customer id
 * ConnectSession s = partner.createConnectSession("customer-42",
 *         ConnectCustomer.of("Ana Souza", "ana@boxy.com").withCompany("Boxy"), "pt-BR");
 * // 2. front-end: BzapperConnect.open({ session: s.sessionToken() }) → emits a one-time code
 * // 3. backend: exchange the code for the customer's key (shown once — store it)
 * PartnerConnection conn = partner.exchangeCode(code);
 * // 4. operate the customer's WhatsApp with the regular client
 * BzapperClient customer = new BzapperClient(conn.apiKey());
 * }</pre>
 *
 * <p>Errors are the same {@link BzapperException} as {@link BzapperClient}. Instances
 * are thread-safe and reusable.
 */
public final class BzapperPartner {

    private final HttpTransport transport;

    /**
     * Creates a partner client pointing at the production API.
     *
     * @param partnerSecret partner secret, e.g. {@code bz_partner_...}
     */
    public BzapperPartner(String partnerSecret) {
        this(builder(partnerSecret));
    }

    /**
     * Creates a partner client with an explicit base URL (dev/self-host). Prefer
     * {@link #BzapperPartner(String)}.
     */
    public BzapperPartner(String baseUrl, String partnerSecret) {
        this(builder(baseUrl, partnerSecret));
    }

    private BzapperPartner(Builder b) {
        this.transport = new HttpTransport(
                Objects.requireNonNull(b.baseUrl, "baseUrl"),
                Objects.requireNonNull(b.partnerSecret, "partnerSecret"),
                b.locale, b.timeout, b.connectTimeout, b.httpClient);
    }

    /** Builder pointing at the production API (recommended). */
    public static Builder builder(String partnerSecret) {
        return new Builder(BzapperClient.DEFAULT_BASE_URL, partnerSecret);
    }

    /** Builder with an explicit base URL (dev/self-host). Prefer {@link #builder(String)}. */
    public static Builder builder(String baseUrl, String partnerSecret) {
        return new Builder(baseUrl, partnerSecret);
    }

    /** Fluent builder for {@link BzapperPartner}. */
    public static final class Builder {
        private final String baseUrl;
        private final String partnerSecret;
        private String locale;
        private Duration timeout;
        private Duration connectTimeout;
        private HttpClient httpClient;

        private Builder(String baseUrl, String partnerSecret) {
            this.baseUrl = baseUrl;
            this.partnerSecret = partnerSecret;
        }

        /** BCP-47 locale sent as {@code Accept-Language} (e.g. {@code pt-BR}). */
        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        /** Per-request timeout (default 30s). */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Connection timeout for the underlying HttpClient (default 10s). */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /** Supply a pre-configured {@link HttpClient} (proxies, executors, etc.). */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public BzapperPartner build() {
            return new BzapperPartner(this);
        }
    }

    // ------------------------------------------------------------------
    // Partner
    // ------------------------------------------------------------------

    /** {@code GET /partner/me} — who this partner secret belongs to. */
    public Partner me() {
        return transport.request("GET", "/partner/me", null, Partner.class);
    }

    // ------------------------------------------------------------------
    // Sessions & code exchange
    // ------------------------------------------------------------------

    /**
     * {@code POST /partner/connect-sessions} — open a Connect session for one of your
     * customers. Creates (or reuses) the connection for {@code externalId} and returns a
     * short-lived {@code sessionToken} (30 min) for the embedded component.
     *
     * @param externalId the customer id in YOUR system (max 200 chars); same id = same connection
     * @param customer   your customer ({@code email} + {@code name} or {@code company} required)
     * @param locale     optional component locale, e.g. {@code pt-BR}; null to omit
     */
    public ConnectSession createConnectSession(String externalId, ConnectCustomer customer, String locale) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_id", externalId);
        payload.put("customer", customer);
        if (locale != null) payload.put("locale", locale);
        return transport.request("POST", "/partner/connect-sessions", payload, ConnectSession.class);
    }

    /** {@code POST /partner/connect-sessions} — same as above without a locale. */
    public ConnectSession createConnectSession(String externalId, ConnectCustomer customer) {
        return createConnectSession(externalId, customer, null);
    }

    /**
     * {@code POST /partner/connect/exchange} — exchange the one-time {@code code} emitted
     * by the component ({@code bzapper:complete}, valid 10 min) for the customer's API key.
     *
     * <p>The returned {@link PartnerConnection#apiKey()} is the raw {@code bz_live_...}
     * key, shown <b>once</b> — store it (use {@link #rotateConnectionKey} if lost).
     * Errors: 400 {@code invalid_code}, 409 {@code connection_revoked}.
     */
    public PartnerConnection exchangeCode(String code) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        return transport.request("POST", "/partner/connect/exchange", payload, PartnerConnection.class);
    }

    // ------------------------------------------------------------------
    // Connections
    // ------------------------------------------------------------------

    /**
     * {@code GET /partner/connections} — list your connections. Both filters are optional.
     *
     * @param externalId filter by your customer id; null to omit
     * @param status     filter by status, e.g. {@code "active"} or
     *                   {@code ConnectionStatus.ACTIVE.value()}; null to omit
     */
    public List<PartnerConnection> listConnections(String externalId, String status) {
        StringBuilder path = new StringBuilder("/partner/connections");
        List<String> q = new ArrayList<>();
        if (externalId != null) q.add("external_id=" + HttpTransport.enc(externalId));
        if (status != null) q.add("status=" + HttpTransport.enc(status));
        if (!q.isEmpty()) path.append('?').append(String.join("&", q));
        return transport.request("GET", path.toString(), null, ConnectionList.class).data();
    }

    /** {@code GET /partner/connections} — every connection (no filters). */
    public List<PartnerConnection> listConnections() {
        return listConnections(null, null);
    }

    /** {@code GET /partner/connections/{id}} — one connection (status, account, numbers). */
    public PartnerConnection getConnection(String id) {
        return transport.request("GET", "/partner/connections/" + HttpTransport.enc(id), null,
                PartnerConnection.class);
    }

    /**
     * {@code POST /partner/connections/{id}/rotate-key} — issue a new API key for a
     * completed connection; the previous key stops working. The new raw key is in
     * {@link PartnerConnection#apiKey()}, shown once. 409 {@code connection_not_active}
     * when the connection is not completed yet or was revoked.
     */
    public PartnerConnection rotateConnectionKey(String id) {
        return transport.request("POST", "/partner/connections/" + HttpTransport.enc(id) + "/rotate-key",
                Map.of(), PartnerConnection.class);
    }

    /**
     * {@code DELETE /partner/connections/{id}} — end a connection: revokes the customer
     * key (it then answers 401 {@code connect_revoked}) but does NOT cancel the
     * customer's plan. A {@code connect.revoked} webhook is sent.
     */
    public void revokeConnection(String id) {
        transport.request("DELETE", "/partner/connections/" + HttpTransport.enc(id), null, Void.class);
    }
}
