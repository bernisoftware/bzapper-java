package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.AccountUsage;
import com.bernisoftware.bzapper.model.AccountUser;
import com.bernisoftware.bzapper.model.ApiKeyCreated;
import com.bernisoftware.bzapper.model.BrandApplyResult;
import com.bernisoftware.bzapper.model.BrandProfile;
import com.bernisoftware.bzapper.model.Button;
import com.bernisoftware.bzapper.model.ConnectResult;
import com.bernisoftware.bzapper.model.ContactCheck;
import com.bernisoftware.bzapper.model.Group;
import com.bernisoftware.bzapper.model.GroupInvite;
import com.bernisoftware.bzapper.model.Instance;
import com.bernisoftware.bzapper.model.ListSection;
import com.bernisoftware.bzapper.model.MediaInput;
import com.bernisoftware.bzapper.model.ParticipantAction;
import com.bernisoftware.bzapper.model.PartnerConnection;
import com.bernisoftware.bzapper.model.PresenceState;
import com.bernisoftware.bzapper.model.Project;
import com.bernisoftware.bzapper.model.ProfileUpdate;
import com.bernisoftware.bzapper.model.Role;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.model.SentMessage;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Official Java client for the bZapper WhatsApp gateway API.
 *
 * <p>Built on the JDK {@link java.net.http.HttpClient} and Jackson; no other
 * runtime dependencies. Construct directly or via {@link #builder(String, String)}:
 *
 * <pre>{@code
 * BzapperClient client = new BzapperClient("bz_live_..."); // points at production
 * SentMessage msg = client.sendText(SendOptions.to("+5511999999999"), "Hello!");
 * }</pre>
 *
 * <p>Every non-2xx response raises a {@link BzapperException}; branch on its
 * stable {@link BzapperException#getCode() code}, never on the localized message.
 *
 * <p>Instances are thread-safe and reusable.
 */
public final class BzapperClient {

    /** Production API base URL. Used by default; override only in dev/self-host. */
    public static final String DEFAULT_BASE_URL = "https://api.bzapper.com.br";

    private final HttpTransport transport;
    // Per-call options applied by withOptions(...); null = defaults.
    private final RequestOptions defaults;

    /**
     * Creates a client pointing at the production API — pass just your API key.
     * This is the recommended constructor.
     *
     * @param apiKey tenant API key, e.g. {@code bz_live_...}
     */
    public BzapperClient(String apiKey) {
        this(builder(apiKey));
    }

    /**
     * Creates a client with an explicit base URL. Prefer {@link #BzapperClient(String)}
     * (which defaults to production); use this only for dev/self-host.
     *
     * @param baseUrl base API URL, e.g. {@code https://api.bzapper.com.br} or
     *                {@code http://localhost:8080}
     * @param apiKey  tenant API key, e.g. {@code bz_live_...}
     */
    public BzapperClient(String baseUrl, String apiKey) {
        this(builder(baseUrl, apiKey));
    }

    private BzapperClient(Builder b) {
        this.transport = new HttpTransport(
                Objects.requireNonNull(b.baseUrl, "baseUrl"),
                Objects.requireNonNull(b.apiKey, "apiKey"),
                b.locale, b.projectId, b.timeout, b.connectTimeout, b.httpClient, b.maxRetries);
        this.defaults = null;
    }

    private BzapperClient(HttpTransport transport, RequestOptions defaults) {
        this.transport = transport;
        this.defaults = defaults;
    }

    /**
     * A view of this client that applies {@code options} (your own {@code Idempotency-Key},
     * a per-attempt timeout) to every call it makes. Cheap: shares the connection pool.
     * Create one per logical call when passing an idempotency key:
     *
     * <pre>{@code
     * client.withOptions(RequestOptions.idempotencyKey("order-1042"))
     *       .createContact(Map.of("phone", "+5511999998888", "name", "Ana"));
     * }</pre>
     */
    public BzapperClient withOptions(RequestOptions options) {
        return new BzapperClient(transport, options);
    }

    /** Retry wait, replaceable in tests (the suite must not really sleep). */
    void setSleeper(HttpTransport.Sleeper sleeper) {
        transport.setSleeper(sleeper);
    }

    /** Builder pointing at the production API — pass just your API key (recommended). */
    public static Builder builder(String apiKey) {
        return new Builder(DEFAULT_BASE_URL, apiKey);
    }

    /** Builder with an explicit base URL (dev/self-host). Prefer {@link #builder(String)}. */
    public static Builder builder(String baseUrl, String apiKey) {
        return new Builder(baseUrl, apiKey);
    }

    /** Fluent builder for {@link BzapperClient}. */
    public static final class Builder {
        private final String baseUrl;
        private final String apiKey;
        private String locale;
        private Duration timeout;
        private Duration connectTimeout;
        private HttpClient httpClient;
        private int maxRetries = HttpTransport.DEFAULT_MAX_RETRIES;
        private String projectId;

        private Builder(String baseUrl, String apiKey) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
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

        /**
         * Retries on network error/timeout, 429, 502, 503 and 504 (beyond the first
         * attempt). Default 2; {@code 0} disables. Retries reuse the same
         * {@code X-Request-Id} and {@code Idempotency-Key}, so they are safe.
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /** Project scope sent as {@code X-Project-Id} (a key already carries its own project). */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public BzapperClient build() {
            return new BzapperClient(this);
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    /** {@code POST /messages/text} — send a text message. */
    public SentMessage sendText(SendOptions options, String body) {
        Map<String, Object> payload = base(options);
        payload.put("body", body);
        return send("/messages/text", payload, options);
    }

    /**
     * {@code POST /messages/otp} — send a verification code. Sends the context
     * text and the code on its own copyable bubble (counts as one send). The API
     * generates the context text in the account language when none is given.
     */
    public SentMessage sendOTP(SendOptions options, String code) {
        Map<String, Object> payload = base(options);
        payload.put("code", code);
        return send("/messages/otp", payload, options);
    }

    /** {@code POST /messages/otp} — send a verification code with custom context text. */
    public SentMessage sendOTP(SendOptions options, String code, String body) {
        Map<String, Object> payload = base(options);
        payload.put("code", code);
        payload.put("body", body);
        return send("/messages/otp", payload, options);
    }

    /** {@code POST /messages/image} — send an image (url or base64). */
    public SentMessage sendImage(SendOptions options, MediaInput media) {
        return sendMedia("/messages/image", options, media);
    }

    /** {@code POST /messages/video} — send a video. */
    public SentMessage sendVideo(SendOptions options, MediaInput media) {
        return sendMedia("/messages/video", options, media);
    }

    /** {@code POST /messages/document} — send a document. */
    public SentMessage sendDocument(SendOptions options, MediaInput media) {
        return sendMedia("/messages/document", options, media);
    }

    /** {@code POST /messages/audio} — send audio. Set {@link MediaInput#asVoiceNote()} for a voice note. */
    public SentMessage sendAudio(SendOptions options, MediaInput media) {
        return sendMedia("/messages/audio", options, media);
    }

    /** {@code POST /messages/sticker} — send a sticker. */
    public SentMessage sendSticker(SendOptions options, MediaInput media) {
        return sendMedia("/messages/sticker", options, media);
    }

    private SentMessage sendMedia(String path, SendOptions options, MediaInput media) {
        Map<String, Object> payload = base(options);
        payload.put("media", media);
        return send(path, payload, options);
    }

    /** {@code POST /messages/location} — send a location pin. */
    public SentMessage sendLocation(SendOptions options, double latitude, double longitude,
                                    String name, String address) {
        Map<String, Object> payload = base(options);
        payload.put("latitude", latitude);
        payload.put("longitude", longitude);
        if (name != null) payload.put("name", name);
        if (address != null) payload.put("address", address);
        return send("/messages/location", payload, options);
    }

    /** {@code POST /messages/contact} — send a contact card (name and/or vCard). */
    public SentMessage sendContact(SendOptions options, String contactName, String contactVcard) {
        Map<String, Object> payload = base(options);
        if (contactName != null) payload.put("contact_name", contactName);
        if (contactVcard != null) payload.put("contact_vcard", contactVcard);
        return send("/messages/contact", payload, options);
    }

    /** {@code POST /messages/poll} — send a poll. {@code selectableCount} may be null (defaults to 1). */
    public SentMessage sendPoll(SendOptions options, String name, List<String> pollOptions,
                                Integer selectableCount) {
        Map<String, Object> payload = base(options);
        payload.put("name", name);
        payload.put("options", pollOptions);
        if (selectableCount != null) payload.put("selectable_count", selectableCount);
        return send("/messages/poll", payload, options);
    }

    /** {@code POST /messages/reaction} — react to a message with an emoji. */
    public SentMessage sendReaction(SendOptions options, String quotedMessageId, String emoji) {
        Map<String, Object> payload = base(options);
        payload.put("quoted_message_id", quotedMessageId);
        payload.put("emoji", emoji);
        return send("/messages/reaction", payload, options);
    }

    /**
     * {@code POST /messages/buttons} — send buttons.
     *
     * <p><b>Caveat:</b> buttons are unreliable on WhatsApp (worse in groups), so the
     * API always also sends an equivalent numbered text menu as a fallback.
     */
    public SentMessage sendButtons(SendOptions options, String body, String footer, List<Button> buttons) {
        Map<String, Object> payload = base(options);
        payload.put("body", body);
        if (footer != null) payload.put("footer", footer);
        payload.put("buttons", buttons);
        return send("/messages/buttons", payload, options);
    }

    /**
     * {@code POST /messages/list} — send a list.
     *
     * <p><b>Caveat:</b> like buttons, lists may fall back to a numbered text menu
     * on the WhatsApp side.
     */
    public SentMessage sendList(SendOptions options, String body, String footer,
                                String buttonText, List<ListSection> sections) {
        Map<String, Object> payload = base(options);
        payload.put("body", body);
        if (footer != null) payload.put("footer", footer);
        if (buttonText != null) payload.put("button_text", buttonText);
        payload.put("sections", sections);
        return send("/messages/list", payload, options);
    }

    // ------------------------------------------------------------------
    // Scheduled sends (scheduled_at on any send)
    // ------------------------------------------------------------------

    /** {@code GET /messages/scheduled} — list pending/recent scheduled sends. */
    public Map<String, Object> listScheduled() {
        return getMap("/messages/scheduled");
    }

    /** {@code DELETE /messages/scheduled/{id}} — cancel a pending scheduled send. */
    public Map<String, Object> cancelScheduled(String scheduledId) {
        return requestMap("DELETE", "/messages/scheduled/" + seg(scheduledId, "scheduledId"), null);
    }

    // ------------------------------------------------------------------
    // Campaigns (Pro + campaigns add-on)
    // ------------------------------------------------------------------

    /**
     * {@code POST /campaigns} — create a campaign with template variations.
     * Requires the Pro plan and the Campaigns add-on. Each variation body accepts
     * {@code {variables}} and spintax {@code {a|b|c}}. {@code params} carries
     * {@code variations} (required) plus optional {@code name}, {@code pool_id},
     * {@code pacing_profile} and {@code start_at}.
     */
    public Map<String, Object> createCampaign(Map<String, Object> params) {
        return postMap("/campaigns", params);
    }

    /** {@code GET /campaigns} — list the project's campaigns. */
    public Map<String, Object> listCampaigns() {
        return getMap("/campaigns");
    }

    /** {@code GET /campaigns/{id}} — campaign with stats. */
    public Map<String, Object> getCampaign(String id) {
        return getMap("/campaigns/" + seg(id, "id"));
    }

    /**
     * {@code PATCH /campaigns/{id}} — edit a not-yet-started campaign. Only allowed
     * while the campaign is draft/scheduled (409 once started). {@code body} carries
     * any of {@code name}, {@code pool_id}, {@code pacing_profile}, {@code start_at}
     * and {@code variations} (when sent, it replaces the existing variations).
     */
    public Map<String, Object> updateCampaign(String id, Map<String, Object> body) {
        return requestMap("PATCH", "/campaigns/" + seg(id, "id"), body);
    }

    /**
     * {@code GET /campaigns/estimate} — live send estimate for a recipient count and
     * pacing (no campaign needed); powers the builder's real-time panel. All params
     * are optional; pass {@code null} to omit.
     *
     * @param recipients number of recipients; null to omit
     * @param pacing     {@code "conservative"} or {@code "normal"}; null to omit
     * @param poolId     restrict to a pool's numbers; null for all project numbers
     * @return an estimate map {@code {recipients, numbers_available, estimated_seconds, estimated_human}}
     */
    public Map<String, Object> estimateCampaign(Integer recipients, String pacing, String poolId) {
        StringBuilder path = new StringBuilder("/campaigns/estimate");
        List<String> q = new ArrayList<>();
        if (recipients != null) q.add("recipients=" + recipients);
        if (pacing != null) q.add("pacing=" + enc(pacing));
        if (poolId != null) q.add("pool_id=" + enc(poolId));
        if (!q.isEmpty()) path.append('?').append(String.join("&", q));
        return getMap(path.toString());
    }

    /**
     * {@code POST /campaigns/{id}/recipients} — add (or replace) recipients. Combine
     * as needed; {@code body} carries any of:
     * <ul>
     *   <li>{@code recipients} — a list of {@code {phone, payload}} objects;</li>
     *   <li>{@code contacts} — a map of phone to payload;</li>
     *   <li>{@code contact_ids} — a list of explicit contact ids (only active ones are added);</li>
     *   <li>{@code contact_filter} — a map selecting every ACTIVE contact matching a filter:
     *       {@code search}, {@code tags} (list), {@code tags_all} (bool, require all),
     *       {@code groups} (list), {@code city}, {@code state}, {@code country},
     *       {@code has_email} (bool);</li>
     *   <li>{@code replace} — a bool that replaces the whole recipient list instead of
     *       appending (draft/scheduled only).</li>
     * </ul>
     * For {@code contact_ids}/{@code contact_filter} phones are resolved server-side,
     * restricted to active contacts, and re-checked against suppression.
     */
    public Map<String, Object> addCampaignRecipients(String id, Map<String, Object> body) {
        return postMap("/campaigns/" + seg(id, "id") + "/recipients", body);
    }

    /**
     * {@code GET /campaigns/{id}/recipients} — list recipients with per-contact
     * delivery. Each item carries {@code id}, {@code phone}, {@code contact_name}
     * (resolved from the contact base), {@code status}
     * ({@code pending|claimed|sent|failed|suppressed}), {@code delivery}
     * (real WhatsApp receipt state: {@code ''|sent|delivered|read}),
     * {@code message_id} and {@code last_error}.
     */
    public Map<String, Object> listCampaignRecipients(String id) {
        return getMap("/campaigns/" + seg(id, "id") + "/recipients");
    }

    /** {@code POST /campaigns/{id}/start} — start (or schedule) the campaign. */
    public Map<String, Object> startCampaign(String id) {
        return postMap("/campaigns/" + seg(id, "id") + "/start", null);
    }

    /** {@code POST /campaigns/{id}/pause} — pause the campaign. */
    public Map<String, Object> pauseCampaign(String id) {
        return postMap("/campaigns/" + seg(id, "id") + "/pause", null);
    }

    /** {@code POST /campaigns/{id}/resume} — resume the campaign. */
    public Map<String, Object> resumeCampaign(String id) {
        return postMap("/campaigns/" + seg(id, "id") + "/resume", null);
    }

    /** {@code POST /campaigns/{id}/cancel} — cancel the campaign. */
    public Map<String, Object> cancelCampaign(String id) {
        return postMap("/campaigns/" + seg(id, "id") + "/cancel", null);
    }

    /** {@code POST /campaigns/{id}/dry-run} — simulate without sending. */
    public Map<String, Object> dryRunCampaign(String id) {
        return postMap("/campaigns/" + seg(id, "id") + "/dry-run", null);
    }

    // ------------------------------------------------------------------
    // Instances
    // ------------------------------------------------------------------

    /** {@code GET /instances} — list the tenant's numbers (active project). Returns the parsed body. */
    public Map<String, Object> listInstances() {
        return listInstances(null);
    }

    /**
     * {@code GET /instances} — list the tenant's numbers, scoped by project.
     *
     * @param projectId a project id, or {@code "all"} for every number in the
     *                  account; {@code null} to use the active project (X-Project-Id).
     */
    public Map<String, Object> listInstances(String projectId) {
        String path = "/instances";
        if (projectId != null) path += "?project_id=" + enc(projectId);
        return getMap(path);
    }

    /** {@code POST /instances} — create an instance (number). */
    public Instance createInstance(String phone, String nickname, String proxyUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phone", phone);
        if (nickname != null) payload.put("nickname", nickname);
        if (proxyUrl != null) payload.put("proxy_url", proxyUrl);
        return post("/instances", payload, Instance.class);
    }

    /** {@code GET /instances/{id}} — fetch an instance. */
    public Instance getInstance(String id) {
        return request("GET", "/instances/" + seg(id, "id"), null, Instance.class);
    }

    /**
     * {@code POST /instances/{id}/connect?method=qr|code} — start connecting.
     *
     * @param method {@code "qr"} (default) or {@code "code"}; null uses the server default
     */
    public ConnectResult connectInstance(String id, String method) {
        String path = "/instances/" + seg(id, "id") + "/connect";
        if (method != null && !method.isEmpty()) {
            path += "?method=" + enc(method);
        }
        return request("POST", path, null, ConnectResult.class);
    }

    /** {@code POST /instances/{id}/disconnect} — disconnect (reconnectable). */
    public void disconnectInstance(String id) {
        request("POST", "/instances/" + seg(id, "id") + "/disconnect", null, Void.class);
    }

    /**
     * {@code POST /instances/{id}/clear-session} — wipe the paired device
     * credential, forcing a clean re-pairing.
     *
     * <p>Use it when {@link #connectInstance} will not produce a QR code, or when
     * pairing is stuck in an inconsistent state: a plain logout only drops the
     * reference and leaves the old device behind.
     *
     * <p>Destructive and irreversible — the number goes offline and must be paired
     * again by scanning a QR code. Idempotent and safe to retry.
     */
    public void clearInstanceSession(String id) {
        request("POST", "/instances/" + seg(id, "id") + "/clear-session", null, Void.class);
    }

    // ------------------------------------------------------------------
    // API keys (self-serve)
    // ------------------------------------------------------------------

    /** {@code GET /keys} — list the tenant's API keys (without the raw secret). */
    public Map<String, Object> listKeys() {
        return getMap("/keys");
    }

    /** {@code POST /keys} — create an API key. The raw {@code apiKey} is shown only once. */
    public ApiKeyCreated createKey(String name, Role role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (name != null) payload.put("name", name);
        if (role != null) payload.put("role", role.value());
        return post("/keys", payload, ApiKeyCreated.class);
    }

    /** {@code DELETE /keys/{id}} — revoke an API key. */
    public void revokeKey(String id) {
        request("DELETE", "/keys/" + seg(id, "id"), null, Void.class);
    }

    // ------------------------------------------------------------------
    // Usage
    // ------------------------------------------------------------------

    /**
     * {@code GET /usage} — usage summary for the tenant.
     *
     * @param from optional RFC3339 lower bound (inclusive); null to omit
     * @param to   optional RFC3339 upper bound; null to omit
     */
    public Map<String, Object> getUsage(String from, String to) {
        StringBuilder path = new StringBuilder("/usage");
        List<String> q = new ArrayList<>();
        if (from != null) q.add("from=" + enc(from));
        if (to != null) q.add("to=" + enc(to));
        if (!q.isEmpty()) {
            path.append('?').append(String.join("&", q));
        }
        return getMap(path.toString());
    }

    // ------------------------------------------------------------------
    // Presence (works in groups, too)
    // ------------------------------------------------------------------

    /**
     * {@code POST /presence/chat} — broadcast a chat presence (typing/recording/paused).
     *
     * <p>{@code instanceId} goes in the body here. {@code to} may be a contact or a
     * <b>group JID</b> — presence works in groups.
     */
    public void presenceChat(String instanceId, String to, PresenceState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instanceId);
        payload.put("to", to);
        payload.put("state", state != null ? state.value() : null);
        post("/presence/chat", payload, Void.class);
    }

    // ------------------------------------------------------------------
    // Conversations & chats
    // ------------------------------------------------------------------

    /** {@code GET /conversations?instance_id=} — list the instance's conversations. */
    public Map<String, Object> listConversations(String instanceId) {
        return getMap("/conversations" + query("instance_id", instanceId));
    }

    /**
     * {@code GET /conversations/{jid}/messages} — page a conversation's history.
     *
     * @param jid        conversation JID (path)
     * @param instanceId instance id (query)
     * @param before     optional RFC3339 upper bound (exclusive); null to omit
     * @param limit      optional page size (server caps at 200); null for the default
     */
    public Map<String, Object> conversationHistory(String jid, String instanceId, String before, Integer limit) {
        StringBuilder path = new StringBuilder("/conversations/").append(seg(jid, "jid")).append("/messages");
        List<String> q = new ArrayList<>();
        if (instanceId != null) q.add("instance_id=" + enc(instanceId));
        if (before != null) q.add("before=" + enc(before));
        if (limit != null) q.add("limit=" + limit);
        if (!q.isEmpty()) path.append('?').append(String.join("&", q));
        return getMap(path.toString());
    }

    /** {@code POST /chats/{jid}/archive} — archive ({@code on=true}) or unarchive a chat. */
    public void archiveChat(String jid, String instanceId, boolean on) {
        post("/chats/" + seg(jid, "jid") + "/archive", chatToggle(instanceId, on), Void.class);
    }

    /** {@code POST /chats/{jid}/pin} — pin ({@code on=true}) or unpin a chat. */
    public void pinChat(String jid, String instanceId, boolean on) {
        post("/chats/" + seg(jid, "jid") + "/pin", chatToggle(instanceId, on), Void.class);
    }

    /** {@code POST /chats/{jid}/read} — mark a chat read ({@code on=true}) or unread. */
    public void markChat(String jid, String instanceId, boolean on) {
        post("/chats/" + seg(jid, "jid") + "/read", chatToggle(instanceId, on), Void.class);
    }

    private Map<String, Object> chatToggle(String instanceId, boolean on) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instanceId);
        payload.put("on", on);
        return payload;
    }

    // ------------------------------------------------------------------
    // Groups
    // ------------------------------------------------------------------

    /** {@code GET /groups?instance_id=} — list the instance's groups. */
    public Map<String, Object> listGroups(String instanceId) {
        return getMap("/groups" + query("instance_id", instanceId));
    }

    /** {@code POST /groups?instance_id=} — create a group with the given participants. */
    public Group createGroup(String instanceId, String name, List<String> participants) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("participants", participants);
        return post("/groups" + query("instance_id", instanceId), payload, Group.class);
    }

    /** {@code GET /groups/{jid}?instance_id=} — fetch a single group. */
    public Group getGroup(String jid, String instanceId) {
        return request("GET", "/groups/" + seg(jid, "jid") + query("instance_id", instanceId), null, Group.class);
    }

    /**
     * {@code POST /groups/join/preview?instance_id=} — show the group behind an
     * invite {@code code} (name, topic, size) WITHOUT joining, to confirm before
     * putting the number in someone else's group.
     */
    public Group previewGroupInvite(String instanceId, String code) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        return post("/groups/join/preview" + query("instance_id", instanceId), payload, Group.class);
    }

    /** {@code POST /groups/join?instance_id=} — join a group via its invite {@code code}. */
    public Group joinGroup(String instanceId, String code) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        return post("/groups/join" + query("instance_id", instanceId), payload, Group.class);
    }

    /**
     * {@code POST /groups/{jid}/participants?instance_id=} — add/remove/promote/demote members.
     *
     * @param participants member JIDs the action applies to
     */
    public Group updateGroupParticipants(String jid, String instanceId, ParticipantAction action,
                                         List<String> participants) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action != null ? action.value() : null);
        payload.put("participants", participants);
        return post("/groups/" + seg(jid, "jid") + "/participants" + query("instance_id", instanceId),
                payload, Group.class);
    }

    /** {@code POST /groups/{jid}/leave?instance_id=} — leave a group. */
    public void leaveGroup(String jid, String instanceId) {
        request("POST", "/groups/" + seg(jid, "jid") + "/leave" + query("instance_id", instanceId),
                null, Void.class);
    }

    /** {@code GET /groups/{jid}/invite?instance_id=} — fetch the group's invite code/link. */
    public GroupInvite groupInvite(String jid, String instanceId) {
        return request("GET", "/groups/" + seg(jid, "jid") + "/invite" + query("instance_id", instanceId),
                null, GroupInvite.class);
    }

    // ------------------------------------------------------------------
    // Contacts
    // ------------------------------------------------------------------

    /**
     * {@code POST /contacts/check} — check which phone numbers are on WhatsApp.
     *
     * <p>{@code instanceId} goes in the body here. Returns the parsed response body.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> contactsCheck(String instanceId, List<String> phones) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instanceId);
        payload.put("phones", phones);
        return request("POST", "/contacts/check", payload, Map.class);
    }

    // ------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------

    /** {@code PATCH /instances/{id}/profile} — update display name, status and/or picture. */
    public Instance setProfile(String id, ProfileUpdate profile) {
        return request("PATCH", "/instances/" + seg(id, "id") + "/profile", profile, Instance.class);
    }

    // ------------------------------------------------------------------
    // Contacts (base captured from conversations — shared across the account)
    // ------------------------------------------------------------------

    /**
     * {@code GET /contacts} — list the account's contact base (optional project filter).
     *
     * @param search     optional name/phone filter; null to omit
     * @param projectId  optional project filter: a project id or {@code "current"}; null to omit
     * @param limit      optional cap on results; null for the default
     */
    public Map<String, Object> listContacts(String search, String projectId, Integer limit) {
        return listContacts(search, projectId, null, limit);
    }

    /**
     * {@code GET /contacts} — list the account's contact base with project and number filters.
     *
     * @param search      optional name/phone filter; null to omit
     * @param projectId   optional project filter: a project id or {@code "current"}; null to omit
     * @param instanceId  optional filter by a number (instance) the contact interacted
     *                    with — the contact↔number link is maintained automatically by the
     *                    API; null to omit
     * @param limit       optional cap on results; null for the default
     */
    public Map<String, Object> listContacts(String search, String projectId, String instanceId, Integer limit) {
        StringBuilder path = new StringBuilder("/contacts");
        List<String> q = new ArrayList<>();
        if (search != null) q.add("search=" + enc(search));
        if (projectId != null) q.add("project_id=" + enc(projectId));
        if (instanceId != null) q.add("instance_id=" + enc(instanceId));
        if (limit != null) q.add("limit=" + limit);
        if (!q.isEmpty()) path.append('?').append(String.join("&", q));
        return getMap(path.toString());
    }

    // ------------------------------------------------------------------
    // Projects (numbers, inbox, keys and stats are isolated per project)
    // ------------------------------------------------------------------

    /** {@code GET /projects} — list the account's projects. */
    public Map<String, Object> listProjects() {
        return getMap("/projects");
    }

    /** {@code POST /projects} — create a project (admin). */
    public Project createProject(String name) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        return post("/projects", payload, Project.class);
    }

    // ------------------------------------------------------------------
    // Brand (numbers' identity — lives on the project)
    // ------------------------------------------------------------------

    /** {@code GET /brand} — read the project's numbers identity. */
    public BrandProfile getBrand() {
        return request("GET", "/brand", null, BrandProfile.class);
    }

    /** {@code PUT /brand} — update the project's numbers identity. */
    public BrandProfile setBrand(BrandProfile brand) {
        return request("PUT", "/brand", brand, BrandProfile.class);
    }

    /** {@code POST /brand/apply} — apply the "About" to all connected numbers of the project. */
    public BrandApplyResult applyBrand() {
        return request("POST", "/brand/apply", null, BrandApplyResult.class);
    }

    // ------------------------------------------------------------------
    // Account: users & usage (admin)
    // ------------------------------------------------------------------

    /** {@code GET /users} — list the account's users. */
    public Map<String, Object> listUsers() {
        return getMap("/users");
    }

    /**
     * {@code POST /users} — invite a user (admin).
     *
     * @param email user email (required)
     * @param name  optional display name; null to omit
     * @param role  {@code "admin"} or {@code "agent"}; null uses the server default
     */
    public AccountUser inviteUser(String email, String name, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        if (name != null) payload.put("name", name);
        if (role != null) payload.put("role", role);
        return post("/users", payload, AccountUser.class);
    }

    /** {@code PATCH /users/{id}} — change a user's role ({@code "admin"} or {@code "agent"}, admin). */
    public void updateUserRole(String id, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", role);
        request("PATCH", "/users/" + seg(id, "id"), payload, Void.class);
    }

    /** {@code DELETE /users/{id}} — remove a user from the account (admin). */
    public void removeUser(String id) {
        request("DELETE", "/users/" + seg(id, "id"), null, Void.class);
    }

    /**
     * {@code GET /account/usage} — aggregated account usage plus a per-project breakdown (admin).
     *
     * @param from optional RFC3339 lower bound (inclusive); null to omit
     * @param to   optional RFC3339 upper bound; null to omit
     */
    public AccountUsage getAccountUsage(String from, String to) {
        StringBuilder path = new StringBuilder("/account/usage");
        List<String> q = new ArrayList<>();
        if (from != null) q.add("from=" + enc(from));
        if (to != null) q.add("to=" + enc(to));
        if (!q.isEmpty()) path.append('?').append(String.join("&", q));
        return request("GET", path.toString(), null, AccountUsage.class);
    }

    // ------------------------------------------------------------------
    // Webhooks (management; to RECEIVE + process events use
    // com.bernisoftware.bzapper.webhooks.Webhooks)
    // ------------------------------------------------------------------

    /** {@code GET /webhooks} — list the project's webhooks. */
    /**
     * {@code GET /advisories} — list "action required on your integration" notices.
     *
     * <p>An advisory means a change on our side requires you to update YOUR code
     * (an SDK to upgrade, a payload or endpoint that changed). It is never a
     * changelog: you only receive advisories that affect your account, matched
     * against the SDK version you run and the features you actually use. The
     * {@code action} field says what to do.
     */
    public Map<String, Object> listAdvisories() {
        return getMap("/advisories");
    }

    /** {@code POST /advisories/{id}/read} — dismiss an advisory once handled. */
    public Map<String, Object> markAdvisoryRead(String advisoryId) {
        return postMap("/advisories/" + seg(advisoryId, "advisoryId") + "/read", null);
    }

    public Map<String, Object> listWebhooks() {
        return getMap("/webhooks");
    }

    /**
     * {@code POST /webhooks} — create a webhook.
     *
     * <p>The response carries the webhook plus its {@code secret} <b>once</b> (when
     * one was generated). Keep it: it's the signing secret for
     * {@link com.bernisoftware.bzapper.webhooks.Webhooks}.
     *
     * @param url          HTTPS endpoint that will receive the deliveries (required)
     * @param secret       omit ({@code null}) to let the API generate a strong one
     * @param eventTypes   subscribed events; null/empty = all. Each event can belong
     *                     to a single webhook (409 on conflict)
     * @param numberFilter optional {@code instance_id} to restrict to one number; null to omit
     */
    public Map<String, Object> createWebhook(String url, String secret,
                                             List<String> eventTypes, String numberFilter) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", url);
        if (secret != null) payload.put("secret", secret);
        if (eventTypes != null) payload.put("event_types", eventTypes);
        if (numberFilter != null) payload.put("number_filter", numberFilter);
        return postMap("/webhooks", payload);
    }

    /** {@code POST /webhooks} — create a webhook subscribed to all events with a generated secret. */
    public Map<String, Object> createWebhook(String url) {
        return createWebhook(url, null, null, null);
    }

    /**
     * {@code PATCH /webhooks/{id}} — update or pause a webhook. All arguments are
     * optional; pass {@code null} to leave a field unchanged.
     *
     * @param secret pass {@code "regenerate"} to rotate the signing secret
     * @param active {@code false} pauses the webhook, {@code true} re-enables it
     */
    public Map<String, Object> updateWebhook(String id, String url, String secret,
                                             List<String> eventTypes, String numberFilter, Boolean active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (url != null) payload.put("url", url);
        if (secret != null) payload.put("secret", secret);
        if (eventTypes != null) payload.put("event_types", eventTypes);
        if (numberFilter != null) payload.put("number_filter", numberFilter);
        if (active != null) payload.put("active", active);
        return requestMap("PATCH", "/webhooks/" + seg(id, "id"), payload);
    }

    /** {@code DELETE /webhooks/{id}} — delete a webhook. */
    public void deleteWebhook(String id) {
        request("DELETE", "/webhooks/" + seg(id, "id"), null, Void.class);
    }

    /**
     * {@code POST /webhooks/{id}/test} — send a test delivery and return the
     * endpoint's HTTP status.
     *
     * @param eventType optional event type to simulate; null for the server default
     */
    public Map<String, Object> testWebhook(String id, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (eventType != null) payload.put("event_type", eventType);
        return postMap("/webhooks/" + seg(id, "id") + "/test", payload);
    }

    /**
     * {@code GET /webhooks/{id}/deliveries} — recent delivery attempts for a webhook.
     *
     * @param limit optional cap on results; null for the server default
     */
    public Map<String, Object> webhookDeliveries(String id, Integer limit) {
        String path = "/webhooks/" + seg(id, "id") + "/deliveries";
        if (limit != null) path += "?limit=" + limit;
        return getMap(path);
    }

    // ------------------------------------------------------------------
    // Connected apps (bZapper Connect — partner software using this account)
    // ------------------------------------------------------------------

    /**
     * {@code GET /me/connections} — partner apps (bZapper Connect) that operate this
     * account's WhatsApp, with {@code partnerName}/{@code partnerLogoUrl} filled in.
     */
    public List<PartnerConnection> listConnectedApps() {
        return request("GET", "/me/connections", null, ConnectionList.class).data();
    }

    /**
     * {@code DELETE /me/connections/{id}} — disconnect a partner app (admin). The
     * partner's API key stops working immediately (it then gets 401
     * {@code connect_revoked}); the account's plan is untouched.
     */
    public void revokeConnectedApp(String id) {
        request("DELETE", "/me/connections/" + seg(id, "id"), null, Void.class);
    }

    // ==================================================================
    // Added in the "padrão Berni r2" upgrade: one method per API operation.
    // ==================================================================

    // ------------------------------------------------------------------
    // Messages (extras)
    // ------------------------------------------------------------------

    /**
     * {@code POST /messages/otp} — send a verification code with custom context text and
     * the code's validity (shown to the recipient). {@code body}/{@code expiryMinutes} may be null.
     */
    public SentMessage sendOTP(SendOptions options, String code, String body, Integer expiryMinutes) {
        Map<String, Object> payload = base(options);
        payload.put("code", code);
        if (body != null) payload.put("body", body);
        if (expiryMinutes != null) payload.put("expiry_minutes", expiryMinutes);
        return send("/messages/otp", payload, options);
    }

    /** {@code GET /messages/scheduled?limit=} — list scheduled sends, capped at {@code limit} (null = default). */
    public Map<String, Object> listScheduled(Integer limit) {
        return getMap("/messages/scheduled" + Paths.query().add("limit", limit));
    }

    /** {@code PATCH /messages/{id}} — edit the text of a sent message ({@code id} = wa_message_id). */
    public Map<String, Object> editMessage(String id, String text) {
        return requestMap("PATCH", "/messages/" + seg(id, "id"), body("text", text));
    }

    /**
     * {@code DELETE /messages/{id}?for_everyone=} — revoke a message.
     *
     * @param forEveryone true to delete for everyone; null uses the server default
     */
    public void revokeMessage(String id, Boolean forEveryone) {
        exec("DELETE", "/messages/" + seg(id, "id") + Paths.query().add("for_everyone", forEveryone), null);
    }

    /**
     * {@code POST /messages/forward} — forward a message (experimental).
     *
     * @param instanceId  number that forwards
     * @param to          destination phone or JID
     * @param fromChat    chat JID where the original message lives
     * @param waMessageId the original wa_message_id
     */
    public Map<String, Object> forwardMessage(String instanceId, String to, String fromChat, String waMessageId) {
        return postMap("/messages/forward",
                body("instance_id", instanceId, "to", to, "from_chat", fromChat, "wa_message_id", waMessageId));
    }

    /**
     * {@code POST /messages/{id}/read} — mark messages as read.
     *
     * @param id           wa_message_id (used when {@code waMessageIds} is null)
     * @param instanceId   number
     * @param chat         chat JID
     * @param sender       author JID (groups); null to omit
     * @param waMessageIds several ids at once; null to omit
     */
    public void markRead(String id, String instanceId, String chat, String sender, List<String> waMessageIds) {
        exec("POST", "/messages/" + seg(id, "id") + "/read",
                body("instance_id", instanceId, "chat", chat, "sender", sender, "wa_message_ids", waMessageIds));
    }

    // ------------------------------------------------------------------
    // Chats, labels, blocking, calls
    // ------------------------------------------------------------------

    /** {@code POST /chats/{jid}/mute} — mute ({@code on=true}) or unmute a chat. */
    public void muteChat(String jid, String instanceId, boolean on) {
        exec("POST", "/chats/" + seg(jid, "jid") + "/mute", chatToggle(instanceId, on));
    }

    /** {@code POST /chats/{jid}/labels} — apply ({@code apply=true}) or remove a WhatsApp Business label. */
    public void applyChatLabel(String jid, String instanceId, String labelId, boolean apply) {
        exec("POST", "/chats/" + seg(jid, "jid") + "/labels",
                body("instance_id", instanceId, "label_id", labelId, "apply", apply));
    }

    /** {@code GET /labels?instance_id=} — the number's WhatsApp Business labels. */
    public Map<String, Object> listLabels(String instanceId) {
        return getMap("/labels" + query("instance_id", instanceId));
    }

    /** {@code POST /labels} — create a label ({@code color} may be null). */
    public Map<String, Object> createLabel(String instanceId, String name, String color) {
        return postMap("/labels", body("instance_id", instanceId, "name", name, "color", color));
    }

    /** {@code DELETE /labels/{id}?instance_id=} — delete a label. */
    public void deleteLabel(String id, String instanceId) {
        exec("DELETE", "/labels/" + seg(id, "id") + query("instance_id", instanceId), null);
    }

    /** {@code POST /contacts/{jid}/block} — block a contact on the number. */
    public void blockContact(String jid, String instanceId) {
        exec("POST", "/contacts/" + seg(jid, "jid") + "/block", body("instance_id", instanceId));
    }

    /** {@code POST /contacts/{jid}/unblock} — unblock a contact on the number. */
    public void unblockContact(String jid, String instanceId) {
        exec("POST", "/contacts/" + seg(jid, "jid") + "/unblock", body("instance_id", instanceId));
    }

    /** {@code GET /blocklist?instance_id=} — the number's blocked contacts. */
    public Map<String, Object> getBlocklist(String instanceId) {
        return getMap("/blocklist" + query("instance_id", instanceId));
    }

    /** {@code POST /calls/reject} — reject an incoming call (ids come from the {@code call.*} webhook). */
    public void rejectCall(String instanceId, String callId, String callFrom) {
        exec("POST", "/calls/reject", body("instance_id", instanceId, "call_id", callId, "call_from", callFrom));
    }

    /** {@code POST /calls/offer} — start a call (experimental). {@code video} may be null (voice). */
    public Map<String, Object> offerCall(String instanceId, String to, Boolean video) {
        return postMap("/calls/offer", body("instance_id", instanceId, "to", to, "video", video));
    }

    /** {@code PATCH /instances/{id}/privacy} — set a privacy setting (e.g. {@code last} → {@code contacts}). */
    public void setPrivacy(String id, String setting, String value) {
        exec("PATCH", "/instances/" + seg(id, "id") + "/privacy", body("setting", setting, "value", value));
    }

    // ------------------------------------------------------------------
    // Instances (extras) and the official rail (WhatsApp Cloud API)
    // ------------------------------------------------------------------

    /**
     * {@code GET /instances} — list numbers, optionally the ARCHIVED ones.
     *
     * @param projectId a project id, {@code "all"}, or null for the active project
     * @param archived  true lists the archived numbers ({@code archived=1}) instead of the active ones
     */
    public Map<String, Object> listInstances(String projectId, boolean archived) {
        return getMap("/instances" + Paths.query().add("project_id", projectId).add("archived", archived ? "1" : null));
    }

    /** {@code DELETE /instances/{id}} — delete a number (logs it out first). */
    public void deleteInstance(String id) {
        exec("DELETE", "/instances/" + seg(id, "id"), null);
    }

    /** {@code POST /instances/{id}/logout} — log the number out of WhatsApp (unpairs the device). */
    public void logoutInstance(String id) {
        exec("POST", "/instances/" + seg(id, "id") + "/logout", null);
    }

    /** {@code POST /instances/{id}/archive} — archive a number (keeps history, frees the slot). */
    public void archiveInstance(String id) {
        exec("POST", "/instances/" + seg(id, "id") + "/archive", null);
    }

    /** {@code POST /instances/{id}/unarchive} — bring an archived number back. */
    public void unarchiveInstance(String id) {
        exec("POST", "/instances/" + seg(id, "id") + "/unarchive", null);
    }

    /** {@code PATCH /instances/{id}/proxy} — set (or clear, with {@code ""}) the number's proxy URL. */
    public void setInstanceProxy(String id, String proxyUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("proxy_url", proxyUrl);
        exec("PATCH", "/instances/" + seg(id, "id") + "/proxy", payload);
    }

    /**
     * {@code PATCH /instances/{id}/inbound-filters} — what the number ignores on the way in.
     * {@code filters} carries any of {@code ignore_groups}, {@code ignore_broadcast},
     * {@code ignore_status} (booleans), {@code group_allowlist}, {@code group_denylist} (JID lists).
     */
    public Map<String, Object> setInboundFilters(String id, Map<String, Object> filters) {
        return requestMap("PATCH", "/instances/" + seg(id, "id") + "/inbound-filters", filters);
    }

    /** {@code GET /official/account} — the project's WhatsApp Business (Cloud API) account. */
    public Map<String, Object> getOfficialAccount() {
        return getMap("/official/account");
    }

    /**
     * {@code POST /official/account} — connect a WhatsApp Business account with manual
     * credentials. {@code account} carries {@code waba_id}, {@code phone_number_id},
     * {@code access_token} and optionally {@code display_number}, {@code verified_name},
     * {@code status}.
     */
    public Map<String, Object> connectOfficialAccount(Map<String, Object> account) {
        return postMap("/official/account", account);
    }

    /** {@code DELETE /official/account} — disconnect the project's WhatsApp Business account. */
    public void disconnectOfficialAccount() {
        exec("DELETE", "/official/account", null);
    }

    // ------------------------------------------------------------------
    // Groups (extras)
    // ------------------------------------------------------------------

    /**
     * {@code PATCH /groups/{jid}?instance_id=} — change name/topic/settings. Every field is
     * optional (null = unchanged).
     *
     * @param announce only admins send messages
     * @param locked   only admins edit the group info
     */
    public void updateGroup(String jid, String instanceId, String name, String topic, Boolean announce, Boolean locked) {
        exec("PATCH", "/groups/" + seg(jid, "jid") + query("instance_id", instanceId),
                body("name", name, "topic", topic, "announce", announce, "locked", locked));
    }

    /**
     * {@code GET /groups/{jid}/invite?instance_id=&reset=} — the group's invite link;
     * {@code reset=true} revokes the current link and issues a new one.
     */
    public GroupInvite groupInviteLink(String jid, String instanceId, Boolean reset) {
        return request("GET", "/groups/" + seg(jid, "jid") + "/invite"
                + Paths.query().add("instance_id", instanceId).add("reset", reset), null, GroupInvite.class);
    }

    /** {@code GET /groups/{jid}/join-requests?instance_id=} — pending requests to join. */
    public Map<String, Object> listJoinRequests(String jid, String instanceId) {
        return getMap("/groups/" + seg(jid, "jid") + "/join-requests" + query("instance_id", instanceId));
    }

    /** {@code POST /groups/{jid}/join-requests?instance_id=} — approve ({@code true}) or reject requests. */
    public void updateJoinRequests(String jid, String instanceId, List<String> participants, boolean approve) {
        exec("POST", "/groups/" + seg(jid, "jid") + "/join-requests" + query("instance_id", instanceId),
                body("participants", participants, "approve", approve));
    }

    // ------------------------------------------------------------------
    // Contacts (CRM base: tags, groups, opt-in/out, suppressions)
    // ------------------------------------------------------------------

    /**
     * {@code GET /contacts} — the contact base with every filter of the API. Keys are the
     * query names: {@code search}, {@code tags}, {@code tags_match} ({@code any|all}),
     * {@code groups}, {@code project_id}, {@code instance_id}, {@code status}, {@code city},
     * {@code state}, {@code country}, {@code zip}, {@code document}, {@code has_email},
     * {@code last_activity_after}, {@code last_activity_before}, {@code created_after},
     * {@code created_before}, {@code sort}, {@code limit}, {@code offset}. Lists go as CSV,
     * booleans as {@code true/false}, {@link java.time.Instant}/{@link java.time.OffsetDateTime}
     * as ISO 8601 UTC; null values are omitted.
     */
    public Map<String, Object> listContacts(Map<String, ?> filters) {
        return getMap("/contacts" + Paths.query().addAll(filters));
    }

    /**
     * {@code POST /contacts} — create a contact. {@code contact} carries {@code phone}
     * (required, {@code +DDIdigits}) and any of {@code name}, {@code email}, {@code document},
     * {@code document_type}, {@code address} (map).
     */
    public Map<String, Object> createContact(Map<String, Object> contact) {
        return postMap("/contacts", contact);
    }

    /** {@code GET /contacts/{id}} — one contact. */
    public Map<String, Object> getContact(String id) {
        return getMap("/contacts/" + seg(id, "id"));
    }

    /**
     * {@code PATCH /contacts/{id}} — update a contact. Only the keys present are sent; a key
     * mapped to {@code null} is sent as JSON {@code null} (clears the field), an absent key
     * is left unchanged.
     */
    public Map<String, Object> updateContact(String id, Map<String, Object> changes) {
        return requestMap("PATCH", "/contacts/" + seg(id, "id"), changes);
    }

    /** {@code DELETE /contacts/{id}} — delete a contact. */
    public void deleteContact(String id) {
        exec("DELETE", "/contacts/" + seg(id, "id"), null);
    }

    /** {@code GET /contacts/{id}/history?limit=} — the contact's timeline (messages + events). */
    public Map<String, Object> getContactHistory(String id, Integer limit) {
        return getMap("/contacts/" + seg(id, "id") + "/history" + Paths.query().add("limit", limit));
    }

    /** {@code POST /contacts/{id}/notes} — add an internal note to the timeline. */
    public void addContactNote(String id, String body) {
        exec("POST", "/contacts/" + seg(id, "id") + "/notes", body("body", body));
    }

    /** {@code POST /contacts/{id}/tags} — add/remove tag keys (either list may be null). */
    public Map<String, Object> mutateContactTags(String id, List<String> add, List<String> remove) {
        return postMap("/contacts/" + seg(id, "id") + "/tags", body("add", add, "remove", remove));
    }

    /** {@code POST /contacts/{id}/groups} — add/remove contact-group keys (either list may be null). */
    public Map<String, Object> mutateContactGroups(String id, List<String> add, List<String> remove) {
        return postMap("/contacts/" + seg(id, "id") + "/groups", body("add", add, "remove", remove));
    }

    /** {@code POST /contacts/{id}/optout} — record the contact's opt-out (stops sends to them). */
    public Map<String, Object> optOutContact(String id) {
        return postMap("/contacts/" + seg(id, "id") + "/optout", null);
    }

    /** {@code POST /contacts/{id}/suppress} — add the contact to the suppression list. */
    public Map<String, Object> suppressContact(String id) {
        return postMap("/contacts/" + seg(id, "id") + "/suppress", null);
    }

    /** {@code POST /contacts/{id}/optin} — record the contact's opt-in (lifts an opt-out). */
    public Map<String, Object> optInContact(String id) {
        return postMap("/contacts/" + seg(id, "id") + "/optin", null);
    }

    /** {@code GET /tags} — the account's tags (with contact counts). */
    public Map<String, Object> listTags() {
        return getMap("/tags");
    }

    /** {@code POST /tags} — create a tag. {@code color} (hex) may be null. */
    public Map<String, Object> createTag(String key, String name, String color) {
        return postMap("/tags", body("key", key, "name", name, "color", color));
    }

    /** {@code DELETE /tags/{id}} — delete a tag. */
    public void deleteTag(String id) {
        exec("DELETE", "/tags/" + seg(id, "id"), null);
    }

    /** {@code GET /contact-groups} — the account's contact groups (with counts). */
    public Map<String, Object> listContactGroups() {
        return getMap("/contact-groups");
    }

    /** {@code POST /contact-groups} — create a contact group. {@code color} (hex) may be null. */
    public Map<String, Object> createContactGroup(String key, String name, String color) {
        return postMap("/contact-groups", body("key", key, "name", name, "color", color));
    }

    /** {@code DELETE /contact-groups/{id}} — delete a contact group. */
    public void deleteContactGroup(String id) {
        exec("DELETE", "/contact-groups/" + seg(id, "id"), null);
    }

    /** {@code GET /suppressions?limit=} — phones that never receive sends. */
    public Map<String, Object> listSuppressions(Integer limit) {
        return getMap("/suppressions" + Paths.query().add("limit", limit));
    }

    /** {@code POST /suppressions} — suppress a phone ({@code reason} may be null). */
    public void createSuppression(String phone, String reason) {
        exec("POST", "/suppressions", body("phone", phone, "reason", reason));
    }

    /** {@code DELETE /suppressions?phone=} — lift a suppression. */
    public void deleteSuppression(String phone) {
        exec("DELETE", "/suppressions" + query("phone", phone), null);
    }

    // ------------------------------------------------------------------
    // Campaigns (extras)
    // ------------------------------------------------------------------

    /** {@code GET /campaigns?limit=} — list campaigns, capped at {@code limit} (null = default). */
    public Map<String, Object> listCampaigns(Integer limit) {
        return getMap("/campaigns" + Paths.query().add("limit", limit));
    }

    /** {@code GET /campaigns/eligibility?pool_id=} — which numbers may dispatch a campaign now. */
    public Map<String, Object> getCampaignEligibility(String poolId) {
        return getMap("/campaigns/eligibility" + query("pool_id", poolId));
    }

    /** {@code POST /campaigns/media} (multipart) — upload a file for campaign variations; returns {@code {url}}. */
    public Map<String, Object> uploadCampaignMedia(byte[] content, String filename, String contentType) {
        return upload("/campaigns/media", content, filename, contentType);
    }

    /** {@code POST /campaigns/media} (multipart) — upload a file from disk. */
    public Map<String, Object> uploadCampaignMedia(java.nio.file.Path file) {
        return upload("/campaigns/media", file);
    }

    /** {@code GET /campaigns/{id}/recipients?limit=} — recipients, capped at {@code limit}. */
    public Map<String, Object> listCampaignRecipients(String id, Integer limit) {
        return getMap("/campaigns/" + seg(id, "id") + "/recipients" + Paths.query().add("limit", limit));
    }

    // ------------------------------------------------------------------
    // Pools (number rotation groups)
    // ------------------------------------------------------------------

    /** {@code GET /pools} — the project's number pools. */
    public Map<String, Object> listPools() {
        return getMap("/pools");
    }

    /**
     * {@code POST /pools} — create a pool.
     *
     * @param strategy e.g. {@code round_robin}; null for the server default
     * @param isDefault make it the project's default pool; null to omit
     */
    public Map<String, Object> createPool(String name, String strategy, Boolean isDefault) {
        return postMap("/pools", body("name", name, "strategy", strategy, "is_default", isDefault));
    }

    /** {@code GET /pools/{id}} — one pool with its members. */
    public Map<String, Object> getPool(String id) {
        return getMap("/pools/" + seg(id, "id"));
    }

    /** {@code POST /pools/{id}/numbers} — add a number to the pool. */
    public void addPoolNumber(String id, String instanceId) {
        exec("POST", "/pools/" + seg(id, "id") + "/numbers", body("instance_id", instanceId));
    }

    // ------------------------------------------------------------------
    // Webhooks (extras)
    // ------------------------------------------------------------------

    /** {@code POST /webhooks/trigger} — fire a sample event of {@code eventType} to the subscribed webhook. */
    public Map<String, Object> triggerWebhookEvent(String eventType) {
        return postMap("/webhooks/trigger", body("event_type", eventType));
    }

    // ------------------------------------------------------------------
    // Account, profile, projects (extras)
    // ------------------------------------------------------------------

    /** {@code GET /healthz} — API liveness ({@code status}, {@code version}). */
    public Map<String, Object> getHealth() {
        return getMap("/healthz");
    }

    /** {@code GET /me} — the authenticated user/account. */
    public Map<String, Object> getMe() {
        return getMap("/me");
    }

    /** {@code PATCH /me} — update the user profile. Every argument is optional (null = unchanged). */
    public Map<String, Object> updateProfile(String name, String phone, String jobTitle, String locale) {
        return requestMap("PATCH", "/me", body("name", name, "phone", phone, "job_title", jobTitle, "locale", locale));
    }

    /** {@code PATCH /account} — rename the account (admin). */
    public Map<String, Object> updateAccount(String name) {
        return requestMap("PATCH", "/account", body("name", name));
    }

    /**
     * {@code POST /projects} — create a project choosing its rail.
     *
     * @param apiMode {@code UNOFFICIAL} (WhatsApp Web) or {@code OFFICIAL} (Cloud API); immutable
     *                after creation; null for the server default
     */
    public Project createProject(String name, String apiMode) {
        return post("/projects", body("name", name, "api_mode", apiMode), Project.class);
    }

    /** {@code GET /projects/health} — number status counts per project. */
    public Map<String, Object> getProjectsHealth() {
        return getMap("/projects/health");
    }

    /** {@code PATCH /projects/{id}} — rename/recolor a project. Every field is optional (null = unchanged). */
    public void updateProject(String id, String name, String logoUrl, String color) {
        exec("PATCH", "/projects/" + seg(id, "id"), body("name", name, "logo_url", logoUrl, "color", color));
    }

    /** {@code DELETE /projects/{id}} — delete a project (admin). */
    public void deleteProject(String id) {
        exec("DELETE", "/projects/" + seg(id, "id"), null);
    }

    /** {@code GET /projects/{id}/brand} — the identity of another project's numbers. */
    public BrandProfile getProjectBrand(String id) {
        return request("GET", "/projects/" + seg(id, "id") + "/brand", null, BrandProfile.class);
    }

    /** {@code PUT /projects/{id}/brand} — update the identity of another project's numbers. */
    public BrandProfile setProjectBrand(String id, BrandProfile brand) {
        return request("PUT", "/projects/" + seg(id, "id") + "/brand", brand, BrandProfile.class);
    }

    /** {@code POST /projects/{id}/logo} (multipart) — upload the project logo; returns {@code {logo_url}}. */
    public Map<String, Object> uploadProjectLogo(String id, byte[] content, String filename, String contentType) {
        return upload("/projects/" + seg(id, "id") + "/logo", content, filename, contentType);
    }

    /** {@code POST /projects/{id}/logo} (multipart) — upload the project logo from disk. */
    public Map<String, Object> uploadProjectLogo(String id, java.nio.file.Path file) {
        return upload("/projects/" + seg(id, "id") + "/logo", file);
    }

    /** {@code POST /brand/logo} (multipart) — upload the brand logo of the active project. */
    public Map<String, Object> uploadBrandLogo(byte[] content, String filename, String contentType) {
        return upload("/brand/logo", content, filename, contentType);
    }

    /** {@code POST /brand/logo} (multipart) — upload the brand logo from disk. */
    public Map<String, Object> uploadBrandLogo(java.nio.file.Path file) {
        return upload("/brand/logo", file);
    }

    // ------------------------------------------------------------------
    // Billing: plan, add-ons cart, invoices
    // ------------------------------------------------------------------

    /** {@code GET /me/entitlements} — what the account's plan and add-ons allow. */
    public Map<String, Object> getMyEntitlements() {
        return getMap("/me/entitlements");
    }

    /** {@code POST /me/plan/upgrade} — put the Pro plan in the cart (returns the cart). */
    public Map<String, Object> upgradePlan() {
        return postMap("/me/plan/upgrade", null);
    }

    /** {@code POST /me/plan/cancel} — cancel Pro at the end of the period (returns entitlements). */
    public Map<String, Object> cancelPlan() {
        return postMap("/me/plan/cancel", null);
    }

    /** {@code POST /me/plan/uncancel} — undo a scheduled cancellation. */
    public Map<String, Object> uncancelPlan() {
        return postMap("/me/plan/uncancel", null);
    }

    /** {@code GET /me/subscription} — the recurring subscription (status, renewal, cancellation). */
    public Map<String, Object> getMySubscription() {
        return getMap("/me/subscription");
    }

    /**
     * {@code POST /me/addons} — add (+) or remove (−) add-ons in the cart.
     *
     * @param kind  e.g. {@code number}, {@code project}, {@code campaigns}, {@code retention_block}
     * @param delta units to add (positive) or remove (negative)
     */
    public Map<String, Object> changeAddon(String kind, int delta) {
        return postMap("/me/addons", body("kind", kind, "delta", delta));
    }

    /** {@code GET /me/addons/cart} — the add-on cart. */
    public Map<String, Object> getAddonCart() {
        return getMap("/me/addons/cart");
    }

    /** {@code DELETE /me/addons/cart} — empty the add-on cart. */
    public Map<String, Object> clearAddonCart() {
        return requestMap("DELETE", "/me/addons/cart", null);
    }

    /** {@code POST /me/addons/cart/checkout} — check out the cart (one invoice); {@code saveCard} may be null. */
    public Map<String, Object> checkoutAddonCart(Boolean saveCard) {
        return postMap("/me/addons/cart/checkout", body("save_card", saveCard));
    }

    /** {@code GET /me/invoices} — the account's invoices. */
    public Map<String, Object> listMyInvoices() {
        return getMap("/me/invoices");
    }

    /** {@code POST /me/invoices/{id}/pay} — start paying an open invoice (returns the Stripe client secret). */
    public Map<String, Object> payInvoice(String id) {
        return postMap("/me/invoices/" + seg(id, "id") + "/pay", null);
    }

    /** {@code GET /billing/config} — whether billing is enabled and the Stripe publishable key. */
    public Map<String, Object> getBillingConfig() {
        return getMap("/billing/config");
    }

    /** {@code GET /pricing} — public plans and prices per currency. */
    public Map<String, Object> getPricing() {
        return getMap("/pricing");
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Map<String, Object> base(SendOptions options) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(options.to(), "options.to");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("to", options.to());
        if (options.instanceId() != null) m.put("instance_id", options.instanceId());
        if (options.poolId() != null) m.put("pool_id", options.poolId());
        if (options.quotedMessageId() != null) m.put("quoted_message_id", options.quotedMessageId());
        if (options.quotedParticipant() != null) m.put("quoted_participant", options.quotedParticipant());
        if (options.clientReference() != null) m.put("client_reference", options.clientReference());
        if (options.mentions() != null) m.put("mentions", options.mentions());
        if (options.sticky() != null) m.put("sticky", options.sticky());
        if (options.scheduledAt() != null) m.put("scheduled_at", options.scheduledAt());
        if (options.groups() != null) m.put("groups", options.groups());
        if (options.tags() != null) m.put("tags", options.tags());
        if (options.force() != null) m.put("force", options.force());
        return m;
    }

    /** Send POST: {@code options.idempotencyKey()} goes in the {@code Idempotency-Key} header. */
    private SentMessage send(String path, Map<String, Object> payload, SendOptions options) {
        String key = options.idempotencyKey();
        RequestOptions opts = defaults;
        if (key != null && !key.isEmpty()) {
            opts = (defaults == null ? RequestOptions.builder() : defaults.toBuilder()).idempotencyKey(key).build();
        }
        return transport.request("POST", path, payload, SentMessage.class, opts);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        return request("POST", path, body, type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path) {
        return request("GET", path, null, Map.class);
    }

    private Map<String, Object> postMap(String path, Object body) {
        return requestMap("POST", path, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestMap(String method, String path, Object body) {
        return request(method, path, body, Map.class);
    }

    private void exec(String method, String path, Object body) {
        request(method, path, body, Void.class);
    }

    private <T> T request(String method, String path, Object body, Class<T> type) {
        return transport.request(method, path, body, type, defaults);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> upload(String path, byte[] content, String filename, String contentType) {
        return transport.multipart(path, new HttpTransport.FilePart("file", filename, contentType, content),
                null, Map.class, defaults);
    }

    private Map<String, Object> upload(String path, java.nio.file.Path file) {
        Objects.requireNonNull(file, "file");
        try {
            String type = java.nio.file.Files.probeContentType(file);
            return upload(path, java.nio.file.Files.readAllBytes(file), file.getFileName().toString(), type);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                m.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return m;
    }

    private static String enc(String value) {
        return Paths.q(value);
    }

    private static String seg(String value, String name) {
        return Paths.seg(value, name);
    }

    /** Builds a single-param query string ({@code "?key=value"}) or {@code ""} when value is null. */
    private static String query(String key, String value) {
        return value == null ? "" : "?" + key + "=" + enc(value);
    }
}
