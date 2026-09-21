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
                b.locale, b.timeout, b.connectTimeout, b.httpClient);
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
        return requestMap("DELETE", "/messages/scheduled/" + enc(scheduledId), null);
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
        return getMap("/campaigns/" + enc(id));
    }

    /**
     * {@code PATCH /campaigns/{id}} — edit a not-yet-started campaign. Only allowed
     * while the campaign is draft/scheduled (409 once started). {@code body} carries
     * any of {@code name}, {@code pool_id}, {@code pacing_profile}, {@code start_at}
     * and {@code variations} (when sent, it replaces the existing variations).
     */
    public Map<String, Object> updateCampaign(String id, Map<String, Object> body) {
        return requestMap("PATCH", "/campaigns/" + enc(id), body);
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
        return postMap("/campaigns/" + enc(id) + "/recipients", body);
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
        return getMap("/campaigns/" + enc(id) + "/recipients");
    }

    /** {@code POST /campaigns/{id}/start} — start (or schedule) the campaign. */
    public Map<String, Object> startCampaign(String id) {
        return postMap("/campaigns/" + enc(id) + "/start", null);
    }

    /** {@code POST /campaigns/{id}/pause} — pause the campaign. */
    public Map<String, Object> pauseCampaign(String id) {
        return postMap("/campaigns/" + enc(id) + "/pause", null);
    }

    /** {@code POST /campaigns/{id}/resume} — resume the campaign. */
    public Map<String, Object> resumeCampaign(String id) {
        return postMap("/campaigns/" + enc(id) + "/resume", null);
    }

    /** {@code POST /campaigns/{id}/cancel} — cancel the campaign. */
    public Map<String, Object> cancelCampaign(String id) {
        return postMap("/campaigns/" + enc(id) + "/cancel", null);
    }

    /** {@code POST /campaigns/{id}/dry-run} — simulate without sending. */
    public Map<String, Object> dryRunCampaign(String id) {
        return postMap("/campaigns/" + enc(id) + "/dry-run", null);
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
        return request("GET", "/instances/" + enc(id), null, Instance.class);
    }

    /**
     * {@code POST /instances/{id}/connect?method=qr|code} — start connecting.
     *
     * @param method {@code "qr"} (default) or {@code "code"}; null uses the server default
     */
    public ConnectResult connectInstance(String id, String method) {
        String path = "/instances/" + enc(id) + "/connect";
        if (method != null && !method.isEmpty()) {
            path += "?method=" + enc(method);
        }
        return request("POST", path, Map.of(), ConnectResult.class);
    }

    /** {@code POST /instances/{id}/disconnect} — disconnect (reconnectable). */
    public void disconnectInstance(String id) {
        request("POST", "/instances/" + enc(id) + "/disconnect", Map.of(), Void.class);
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
        request("POST", "/instances/" + enc(id) + "/clear-session", Map.of(), Void.class);
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
        request("DELETE", "/keys/" + enc(id), null, Void.class);
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
        StringBuilder path = new StringBuilder("/conversations/").append(enc(jid)).append("/messages");
        List<String> q = new ArrayList<>();
        if (instanceId != null) q.add("instance_id=" + enc(instanceId));
        if (before != null) q.add("before=" + enc(before));
        if (limit != null) q.add("limit=" + limit);
        if (!q.isEmpty()) path.append('?').append(String.join("&", q));
        return getMap(path.toString());
    }

    /** {@code POST /chats/{jid}/archive} — archive ({@code on=true}) or unarchive a chat. */
    public void archiveChat(String jid, String instanceId, boolean on) {
        post("/chats/" + enc(jid) + "/archive", chatToggle(instanceId, on), Void.class);
    }

    /** {@code POST /chats/{jid}/pin} — pin ({@code on=true}) or unpin a chat. */
    public void pinChat(String jid, String instanceId, boolean on) {
        post("/chats/" + enc(jid) + "/pin", chatToggle(instanceId, on), Void.class);
    }

    /** {@code POST /chats/{jid}/read} — mark a chat read ({@code on=true}) or unread. */
    public void markChat(String jid, String instanceId, boolean on) {
        post("/chats/" + enc(jid) + "/read", chatToggle(instanceId, on), Void.class);
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
        return request("GET", "/groups/" + enc(jid) + query("instance_id", instanceId), null, Group.class);
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
        return post("/groups/" + enc(jid) + "/participants" + query("instance_id", instanceId),
                payload, Group.class);
    }

    /** {@code POST /groups/{jid}/leave?instance_id=} — leave a group. */
    public void leaveGroup(String jid, String instanceId) {
        request("POST", "/groups/" + enc(jid) + "/leave" + query("instance_id", instanceId),
                Map.of(), Void.class);
    }

    /** {@code GET /groups/{jid}/invite?instance_id=} — fetch the group's invite code/link. */
    public GroupInvite groupInvite(String jid, String instanceId) {
        return request("GET", "/groups/" + enc(jid) + "/invite" + query("instance_id", instanceId),
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
        return request("PATCH", "/instances/" + enc(id) + "/profile", profile, Instance.class);
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
        return request("POST", "/brand/apply", Map.of(), BrandApplyResult.class);
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
        request("PATCH", "/users/" + enc(id), payload, Void.class);
    }

    /** {@code DELETE /users/{id}} — remove a user from the account (admin). */
    public void removeUser(String id) {
        request("DELETE", "/users/" + enc(id), null, Void.class);
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
        return postMap("/advisories/" + enc(advisoryId) + "/read", null);
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
        return requestMap("PATCH", "/webhooks/" + enc(id), payload);
    }

    /** {@code DELETE /webhooks/{id}} — delete a webhook. */
    public void deleteWebhook(String id) {
        request("DELETE", "/webhooks/" + enc(id), null, Void.class);
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
        return postMap("/webhooks/" + enc(id) + "/test", payload);
    }

    /**
     * {@code GET /webhooks/{id}/deliveries} — recent delivery attempts for a webhook.
     *
     * @param limit optional cap on results; null for the server default
     */
    public Map<String, Object> webhookDeliveries(String id, Integer limit) {
        String path = "/webhooks/" + enc(id) + "/deliveries";
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
        request("DELETE", "/me/connections/" + enc(id), null, Void.class);
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
        return m;
    }

    /** Send POST: {@code options.idempotencyKey()} goes in the {@code Idempotency-Key} header. */
    private SentMessage send(String path, Map<String, Object> payload, SendOptions options) {
        String key = options.idempotencyKey();
        if (key == null || key.isEmpty()) {
            return post(path, payload, SentMessage.class);
        }
        return transport.request("POST", path, payload, SentMessage.class, Map.of("Idempotency-Key", key));
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

    private <T> T request(String method, String path, Object body, Class<T> type) {
        return transport.request(method, path, body, type);
    }

    private static String enc(String value) {
        return HttpTransport.enc(value);
    }

    /** Builds a single-param query string ({@code "?key=value"}) or {@code ""} when value is null. */
    private static String query(String key, String value) {
        return value == null ? "" : "?" + key + "=" + enc(value);
    }
}
