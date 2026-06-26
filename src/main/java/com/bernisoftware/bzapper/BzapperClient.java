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
import com.bernisoftware.bzapper.model.PresenceState;
import com.bernisoftware.bzapper.model.Project;
import com.bernisoftware.bzapper.model.ProfileUpdate;
import com.bernisoftware.bzapper.model.Role;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.model.SentMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * BzapperClient client = new BzapperClient("https://api.bzapper.com.br", "bz_live_...");
 * SentMessage msg = client.sendText(SendOptions.to("+5511999999999"), "Hello!");
 * }</pre>
 *
 * <p>Every non-2xx response raises a {@link BzapperException}; branch on its
 * stable {@link BzapperException#getCode() code}, never on the localized message.
 *
 * <p>Instances are thread-safe and reusable.
 */
public final class BzapperClient {

    private final String baseUrl;
    private final String apiKey;
    private final String locale;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    /**
     * Creates a client with default options (no locale, 30s timeout).
     *
     * @param baseUrl base API URL, e.g. {@code https://api.bzapper.com.br} or
     *                {@code http://localhost:8080}
     * @param apiKey  tenant API key, e.g. {@code bz_live_...}
     */
    public BzapperClient(String baseUrl, String apiKey) {
        this(builder(baseUrl, apiKey));
    }

    private BzapperClient(Builder b) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(b.baseUrl, "baseUrl"));
        this.apiKey = Objects.requireNonNull(b.apiKey, "apiKey");
        this.locale = b.locale;
        this.requestTimeout = b.timeout != null ? b.timeout : Duration.ofSeconds(30);
        this.http = b.httpClient != null
                ? b.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(b.connectTimeout != null ? b.connectTimeout : Duration.ofSeconds(10))
                        .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

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
        return post("/messages/text", payload, SentMessage.class);
    }

    /**
     * {@code POST /messages/otp} — send a verification code. Sends the context
     * text and the code on its own copyable bubble (counts as one send). The API
     * generates the context text in the account language when none is given.
     */
    public SentMessage sendOTP(SendOptions options, String code) {
        Map<String, Object> payload = base(options);
        payload.put("code", code);
        return post("/messages/otp", payload, SentMessage.class);
    }

    /** {@code POST /messages/otp} — send a verification code with custom context text. */
    public SentMessage sendOTP(SendOptions options, String code, String body) {
        Map<String, Object> payload = base(options);
        payload.put("code", code);
        payload.put("body", body);
        return post("/messages/otp", payload, SentMessage.class);
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
        return post(path, payload, SentMessage.class);
    }

    /** {@code POST /messages/location} — send a location pin. */
    public SentMessage sendLocation(SendOptions options, double latitude, double longitude,
                                    String name, String address) {
        Map<String, Object> payload = base(options);
        payload.put("latitude", latitude);
        payload.put("longitude", longitude);
        if (name != null) payload.put("name", name);
        if (address != null) payload.put("address", address);
        return post("/messages/location", payload, SentMessage.class);
    }

    /** {@code POST /messages/contact} — send a contact card (name and/or vCard). */
    public SentMessage sendContact(SendOptions options, String contactName, String contactVcard) {
        Map<String, Object> payload = base(options);
        if (contactName != null) payload.put("contact_name", contactName);
        if (contactVcard != null) payload.put("contact_vcard", contactVcard);
        return post("/messages/contact", payload, SentMessage.class);
    }

    /** {@code POST /messages/poll} — send a poll. {@code selectableCount} may be null (defaults to 1). */
    public SentMessage sendPoll(SendOptions options, String name, List<String> pollOptions,
                                Integer selectableCount) {
        Map<String, Object> payload = base(options);
        payload.put("name", name);
        payload.put("options", pollOptions);
        if (selectableCount != null) payload.put("selectable_count", selectableCount);
        return post("/messages/poll", payload, SentMessage.class);
    }

    /** {@code POST /messages/reaction} — react to a message with an emoji. */
    public SentMessage sendReaction(SendOptions options, String quotedMessageId, String emoji) {
        Map<String, Object> payload = base(options);
        payload.put("quoted_message_id", quotedMessageId);
        payload.put("emoji", emoji);
        return post("/messages/reaction", payload, SentMessage.class);
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
        return post("/messages/buttons", payload, SentMessage.class);
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
        return post("/messages/list", payload, SentMessage.class);
    }

    // ------------------------------------------------------------------
    // Instances
    // ------------------------------------------------------------------

    /** {@code GET /instances} — list the tenant's numbers. Returns the parsed body. */
    public Map<String, Object> listInstances() {
        return getMap("/instances");
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
        StringBuilder path = new StringBuilder("/contacts");
        List<String> q = new ArrayList<>();
        if (search != null) q.add("search=" + enc(search));
        if (projectId != null) q.add("project_id=" + enc(projectId));
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
        if (options.clientReference() != null) m.put("client_reference", options.clientReference());
        if (options.mentions() != null) m.put("mentions", options.mentions());
        if (options.sticky() != null) m.put("sticky", options.sticky());
        return m;
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
        byte[] payload = serialize(body);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json");

        if (locale != null && !locale.isEmpty()) {
            rb.header("Accept-Language", locale);
        }

        if (body != null) {
            rb.header("Content-Type", "application/json");
            rb.method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<byte[]> response;
        try {
            response = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new BzapperException("network_error", "HTTP request failed: " + e.getMessage(),
                    0, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BzapperException("interrupted", "HTTP request interrupted", 0, null, e);
        }

        int status = response.statusCode();
        byte[] data = response.body();

        if (status < 200 || status >= 300) {
            throw toException(status, data);
        }

        return deserialize(data, type);
    }

    private byte[] serialize(Object body) {
        if (body == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new BzapperException("serialization_error",
                    "Failed to serialize request body: " + e.getMessage(), 0, null, e);
        }
    }

    private <T> T deserialize(byte[] data, Class<T> type) {
        if (type == Void.class || data == null || data.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(data, type);
        } catch (IOException e) {
            throw new BzapperException("deserialization_error",
                    "Failed to parse response body: " + e.getMessage(), 0, null, e);
        }
    }

    private BzapperException toException(int status, byte[] data) {
        String code = "http_error";
        String message = "HTTP " + status;
        String errLocale = null;
        if (data != null && data.length > 0) {
            try {
                JsonNode node = mapper.readTree(data);
                if (node.hasNonNull("code")) code = node.get("code").asText();
                if (node.hasNonNull("message")) message = node.get("message").asText();
                if (node.hasNonNull("locale")) errLocale = node.get("locale").asText();
            } catch (IOException ignored) {
                // Non-JSON error body; keep generic code/message.
            }
        }
        return new BzapperException(code, message, status, errLocale);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Builds a single-param query string ({@code "?key=value"}) or {@code ""} when value is null. */
    private static String query(String key, String value) {
        return value == null ? "" : "?" + key + "=" + enc(value);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
