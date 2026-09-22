# bZapper Java SDK

Official Java client for the [bZapper](https://bzapper.com.br) WhatsApp gateway API — a
multi-tenant WhatsApp gateway with a REST HTTP API.

- Java 17+
- Built on the JDK `java.net.http.HttpClient` — the only runtime dependency is Jackson for JSON.
- One method per API operation (messages, numbers, groups, chats, contacts/CRM, campaigns,
  pools, webhooks, advisories, usage, billing, projects/users/keys, brand, official rail and
  bZapper Connect).
- Typed error model: every non-2xx response throws a `BzapperException` (or a typed subclass)
  carrying a **stable code** and the **request id**.
- Safe automatic retries (network errors, 429, 502/503/504) with the same `X-Request-Id` and
  `Idempotency-Key` on every attempt.

## Install

Maven (`br.com.bernisoftware:bzapper`):

```xml
<dependency>
  <groupId>br.com.bernisoftware</groupId>
  <artifactId>bzapper</artifactId>
  <version>0.7.1</version>
</dependency>
```

Gradle:

```kotlin
implementation("br.com.bernisoftware:bzapper:0.7.1")
```

> **Pin the exact version** (`0.7.1`, not a range). Every release note states whether it
> changes the public surface (a breaking signature change) or is purely additive.

The SDK's **only** runtime dependency is **Jackson**, pulled in **transitively** — you
add nothing else (no Gson, no OkHttp, no manual dependencies). Requires **Java 17+**.

## Hello world

```java
import com.bernisoftware.bzapper.BzapperClient;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.model.SentMessage;

BzapperClient client = new BzapperClient("bz_live_...");

SentMessage msg = client.sendText(SendOptions.to("+5511999999999"), "Hello from bZapper!");
System.out.println(msg.messageId());
```

The base URL defaults to `https://api.bzapper.com.br`. Passing one is optional —
only for dev or self-hosting: `new BzapperClient("http://localhost:8080", "bz_live_...")`.

## Configuration

Use the constructor for the defaults, or the builder for `locale` and `timeout`:

```java
import java.time.Duration;

BzapperClient client = BzapperClient.builder("bz_live_...")
        .locale("pt-BR")                  // sent as Accept-Language (translated error messages)
        .timeout(Duration.ofSeconds(30))  // per-attempt timeout (default 30 s)
        .maxRetries(2)                    // retries beyond the first attempt (default 2; 0 disables)
        .projectId("project-uuid")        // optional: sent as X-Project-Id
        .build();
```

The 1-arg `builder(apiKey)` defaults the base URL to production. For dev/self-host,
use the 2-arg `builder(baseUrl, apiKey)` (e.g. `builder("http://localhost:8080", "bz_live_...")`).

Where to get the key: in the bZapper panel, **API keys** (or `createKey` below). A key belongs
to a project (numbers, inbox, keys and stats are isolated per project); `projectId(...)` only
matters for account-wide keys.

Every request sends:

- `Authorization: Bearer <apiKey>`
- `Accept: application/json`
- `Content-Type: application/json` (on requests with a body)
- `X-Bzapper-Client` and `User-Agent`: `bzapper-java/<version>` — how the API knows which
  accounts run a version affected by a fix (the "update your integration" advisories)
- `X-Request-Id`: generated per logical call, repeated on retries
- `Idempotency-Key` on POST/PUT/PATCH/DELETE: generated per logical call (or yours), repeated on retries
- `Accept-Language: <locale>` / `X-Project-Id: <projectId>` (only when set)

## `SendOptions` (common send fields)

All `send*` methods take a `SendOptions` as their first argument. Only `to` (E.164 phone
or JID) is required; chain the optional fields fluently:

```java
SendOptions opts = SendOptions.to("+5511999999999")
        .withInstanceId("inst-uuid")        // pin a specific number (skips rotation)
        .withPoolId("pool-uuid")            // rotate within a pool
        .withQuotedMessageId("wa-msg-id")   // reply / quote
        .withQuotedParticipant("+5511777777777") // quoted author — groups only, when not in bZapper history
        .withClientReference("order-42")    // echoed back on status events
        .withMentions(java.util.List.of("5511888888888@s.whatsapp.net", "+55 11 97777-7777")) // JIDs or phones
        .withGroups(java.util.List.of("customers"))  // stamp contact groups on the recipient contact
        .withTags(java.util.List.of("vip"))          // stamp tags on the recipient contact
        .withForce(true)                    // bypass an INFERRED suppression (never an explicit one)
        .withIdempotencyKey("order-42-confirmation"); // Idempotency-Key header: safe retries
```

`withIdempotencyKey` (up to 255 chars) is sent as the `Idempotency-Key` header, never in
the body. Repeating a send with the same key within 24h returns the SAME response without
sending twice (`409 idempotency_in_progress` while the first is still running;
`422 idempotency_key_reused` if the body changed).

## Sending each message type

```java
import com.bernisoftware.bzapper.model.*;
import java.util.List;

SendOptions to = SendOptions.to("+5511999999999");

// Text
client.sendText(to, "Hello!");

// Image (use url OR base64, never both)
client.sendImage(to, MediaInput.url("https://example.com/cat.png", "A cat"));
client.sendImage(to, MediaInput.base64("iVBORw0KGgo..."));

// Video
client.sendVideo(to, MediaInput.url("https://example.com/clip.mp4"));

// Document
client.sendDocument(to, MediaInput.url("https://example.com/invoice.pdf").withFilename("invoice.pdf"));

// Audio — set asVoiceNote() for a WhatsApp voice note (ptt)
client.sendAudio(to, MediaInput.url("https://example.com/voice.ogg").asVoiceNote());

// Sticker
client.sendSticker(to, MediaInput.url("https://example.com/sticker.webp"));

// Location (name/address are optional → pass null)
client.sendLocation(to, -23.5505, -46.6333, "São Paulo", "Praça da Sé");

// Contact (name and/or vCard, both optional)
client.sendContact(to, "Ada Lovelace", "BEGIN:VCARD\nVERSION:3.0\nFN:Ada Lovelace\nEND:VCARD");

// Poll (selectableCount may be null → defaults to 1)
client.sendPoll(to, "Lunch?", List.of("Pizza", "Sushi", "Salad"), 1);

// Reaction (quotedMessageId is required)
client.sendReaction(to, "wa-message-id", "👍");

// Buttons (footer optional)
client.sendButtons(to, "Pick one", "Footer", List.of(Button.of("yes", "Yes"), Button.of("no", "No")));

// List (footer/buttonText optional)
client.sendList(to, "Our menu", "Tap to choose", "Open menu",
        List.of(ListSection.of("Drinks", List.of(
                ListRow.of("c", "Coffee", "Fresh"),
                ListRow.of("t", "Tea", "Green")))));
```

> **Buttons & lists caveat.** Interactive buttons and lists are unreliable on WhatsApp
> (worse in groups). The API **always** also delivers an equivalent **numbered text menu**
> as a fallback, so recipients can still reply by number.

Every `send*` call returns a `SentMessage` (`messageId`, `status`, `clientReference`).

```java
// OTP: the code goes on its own copyable bubble (1 logical send); body/expiry optional
client.sendOTP(to, "482913", null, 10);

// Scheduling: any send with withScheduledAt(...) returns status "scheduled"
client.sendText(to.withScheduledAt("2026-12-24T12:00:00Z"), "Merry Christmas!");
client.listScheduled(50);                        // GET /messages/scheduled?limit=
client.cancelScheduled("scheduled-uuid");        // DELETE /messages/scheduled/{id}

// Edit / revoke / forward / mark read
client.editMessage("wa-message-id", "Fixed text");
client.revokeMessage("wa-message-id", true);     // for everyone
client.forwardMessage(inst, "+5511888888888", "5511999999999@s.whatsapp.net", "wa-message-id");
client.markRead("wa-message-id", inst, "5511999999999@s.whatsapp.net", null, null);
```

## Instances

```java
import com.bernisoftware.bzapper.model.*;

Map<String, Object> instances = client.listInstances();           // GET /instances
Instance created = client.createInstance("+5511999999999", "Support", null); // POST /instances
Instance one = client.getInstance(created.id());                  // GET /instances/{id}

// Connect by QR or pairing code
ConnectResult qr = client.connectInstance(created.id(), "qr");    // -> qr.qrCode()
ConnectResult code = client.connectInstance(created.id(), "code");// -> code.pairCode()

client.disconnectInstance(created.id());                          // POST /instances/{id}/disconnect
client.logoutInstance(created.id());                              // unpair the device
client.clearInstanceSession(created.id());                        // wipe the device credential (clean re-pair)
client.setInstanceProxy(created.id(), "http://user:pass@proxy:8080");
client.setInboundFilters(created.id(), Map.of("ignore_groups", true, "ignore_status", true));
client.setPrivacy(created.id(), "last", "contacts");
client.archiveInstance(created.id());                             // archived numbers: listInstances(null, true)
client.unarchiveInstance(created.id());
client.deleteInstance(created.id());

// Official rail (WhatsApp Cloud API) — project created with api_mode OFFICIAL
client.createProject("Store (official)", "OFFICIAL");
Map<String, Object> official = client.getOfficialAccount();       // GET /official/account
client.disconnectOfficialAccount();

// Number pools (rotation)
Map<String, Object> pool = client.createPool("Sales", "round_robin", false);
client.addPoolNumber((String) pool.get("id"), created.id());
client.listPools();
```

## Groups, presence and conversations

These advanced operations target a specific number, so they take an `instanceId`
(sent on the **query string** for most, in the **body** for presence/contacts, as the
brief dictates). Group/conversation `jid`s go on the **path**.

```java
import com.bernisoftware.bzapper.model.*;
import java.util.List;
import java.util.Map;

String inst = "inst-uuid";

// --- Presence (works in groups, too!) -----------------------------------
// "to" can be a contact OR a group JID — broadcast typing/recording in a group:
client.presenceChat(inst, "123456789-987654@g.us", PresenceState.TYPING);     // POST /presence/chat
client.presenceChat(inst, "+5511999999999", PresenceState.RECORDING);          // 1:1 also works
client.presenceChat(inst, "123456789-987654@g.us", PresenceState.PAUSED);

// --- Conversations ------------------------------------------------------
Map<String, Object> convos = client.listConversations(inst);                  // GET /conversations
// Page history: before=RFC3339 (exclusive), limit<=200; pass null to omit either:
Map<String, Object> history = client.conversationHistory(
        "123456789-987654@g.us", inst, "2026-06-01T00:00:00Z", 100);          // GET /conversations/{jid}/messages

// --- Chat flags (on=true to set, false to clear) ------------------------
client.archiveChat("123456789-987654@g.us", inst, true);                      // POST /chats/{jid}/archive
client.pinChat("123456789-987654@g.us", inst, true);                          // POST /chats/{jid}/pin
client.markChat("123456789-987654@g.us", inst, true);                         // POST /chats/{jid}/read (read/unread)

// --- Groups -------------------------------------------------------------
Map<String, Object> groups = client.listGroups(inst);                         // GET /groups
Group g = client.createGroup(inst, "Project X",
        List.of("5511999999999@s.whatsapp.net", "5511888888888@s.whatsapp.net")); // POST /groups
Group fetched = client.getGroup(g.jid(), inst);                               // GET /groups/{jid}
Group preview = client.previewGroupInvite(inst, "AbCdEf0123");               // name/size WITHOUT joining
Group joined = client.joinGroup(inst, "AbCdEf0123");                          // POST /groups/join {code}
client.updateGroupParticipants(g.jid(), inst, ParticipantAction.ADD,
        List.of("5511777777777@s.whatsapp.net"));                             // add|remove|promote|demote
GroupInvite invite = client.groupInvite(g.jid(), inst);                       // GET /groups/{jid}/invite
System.out.println(invite.url());
client.groupInviteLink(g.jid(), inst, true);                                  // reset=true: revoke + new link
client.updateGroup(g.jid(), inst, "Project X (2026)", "Topic", true, null);    // name/topic/announce/locked
client.listJoinRequests(g.jid(), inst);
client.updateJoinRequests(g.jid(), inst, List.of("5511666666666@s.whatsapp.net"), true); // approve
client.leaveGroup(g.jid(), inst);                                             // POST /groups/{jid}/leave

// --- Chats: mute, labels; blocking; calls --------------------------------
client.muteChat("5511999999999@s.whatsapp.net", inst, true);
Map<String, Object> label = client.createLabel(inst, "Hot lead", null);
client.applyChatLabel("5511999999999@s.whatsapp.net", inst, (String) label.get("id"), true);
client.blockContact("5511999999999@s.whatsapp.net", inst);
client.getBlocklist(inst);
client.rejectCall(inst, "call-id", "5511999999999@s.whatsapp.net");

// --- Contacts -----------------------------------------------------------
Map<String, Object> check = client.contactsCheck(inst,
        List.of("+5511999999999", "+5511888888888"));                         // POST /contacts/check

// --- Profile ------------------------------------------------------------
Instance updated = client.setProfile(inst, ProfileUpdate.empty()             // PATCH /instances/{id}/profile
        .withDisplayName("Support")
        .withStatusMessage("We reply fast")
        .withPicture("iVBORw0KGgo..."));  // base64 image
```

## Contacts (CRM base)

The contact base is captured automatically from conversations and shared across the account;
the contact↔project/number link is maintained by the API.

```java
Map<String, Object> page = client.listContacts(Map.of(
        "search", "ana", "tags", List.of("vip"), "has_email", true, "limit", 50)); // lists go as CSV
Map<String, Object> ana = client.createContact(Map.of("phone", "+5511999998888", "name", "Ana"));
String contactId = (String) ana.get("id");
client.updateContact(contactId, Map.of("email", "ana@example.com")); // a key mapped to null clears it
client.mutateContactTags(contactId, List.of("vip"), null);
client.mutateContactGroups(contactId, List.of("customers"), null);
client.addContactNote(contactId, "Called about the order");
client.getContactHistory(contactId, 50);         // timeline (messages + events)
client.optOutContact(contactId);                 // or optInContact / suppressContact
client.createTag("vip", "VIP", "#22c55e");       // listTags / deleteTag
client.createContactGroup("customers", "Customers", null); // listContactGroups / deleteContactGroup
client.createSuppression("+5511977776666", "asked to stop"); // listSuppressions / deleteSuppression
```

## Campaigns (Pro + campaigns add-on)

```java
Map<String, Object> camp = client.createCampaign(Map.of(
        "name", "Black Friday",
        "pacing_profile", "conservative",
        "variations", List.of(Map.of("body", "Hi {name}! {Deal|Offer} of the day"))));
String campaignId = (String) camp.get("id");
client.addCampaignRecipients(campaignId, Map.of("contact_filter", Map.of("tags", List.of("vip"))));
client.estimateCampaign(1000, "normal", null);   // live estimate
client.getCampaignEligibility(null);             // numbers allowed to dispatch
client.dryRunCampaign(campaignId);               // simulate
client.startCampaign(campaignId);                // pause/resume/cancel too
client.listCampaignRecipients(campaignId, 100);
client.uploadCampaignMedia(java.nio.file.Path.of("banner.png")); // multipart → {url}
```

## Projects, users, brand and billing

```java
client.listProjects();
client.updateProject("project-uuid", "Store", null, "#0ea5e9");
client.getProjectBrand("project-uuid");
client.uploadProjectLogo("project-uuid", java.nio.file.Path.of("logo.png"));
client.uploadBrandLogo(bytes, "logo.png", "image/png");
client.inviteUser("ana@example.com", "Ana", "agent");
client.getMe();
client.getMyEntitlements();                     // plan + add-ons
client.changeAddon("number", 1);                // cart: +1 number
client.checkoutAddonCart(true);                 // one invoice for the cart
client.listMyInvoices();
client.getPricing();
```

## API keys (self-serve)

```java
import com.bernisoftware.bzapper.model.*;

Map<String, Object> keys = client.listKeys();                     // GET /keys
ApiKeyCreated created = client.createKey("server", Role.AGENT);   // POST /keys
System.out.println(created.apiKey());  // raw key — shown ONCE, store it now
client.revokeKey("key-uuid");                                     // DELETE /keys/{id}
```

## Usage

```java
// GET /usage  (from/to are optional RFC3339 strings; pass null to omit)
Map<String, Object> usage = client.getUsage("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z");
Map<String, Object> all   = client.getUsage(null, null);
```

## Webhooks

**Manage** your webhook subscriptions through the client. `createWebhook` returns
the webhook plus its signing `secret` **once** — store it now, you'll need it to
verify deliveries.

```java
import java.util.List;
import java.util.Map;

// POST /webhooks — subscribe an endpoint to specific events (null/empty = all)
Map<String, Object> hook = client.createWebhook(
        "https://yourapp.com/webhooks/bzapper",
        null,                                          // secret: null = API generates one
        List.of("message.received", "message.read"),   // event_types
        null);                                         // number_filter (an instance_id)
String secret = (String) hook.get("secret");           // shown ONCE
String id = (String) hook.get("id");

client.listWebhooks();                                          // GET /webhooks
client.updateWebhook(id, null, null, null, null, false);        // pause (active=false)
client.updateWebhook(id, null, "regenerate", null, null, null); // rotate the secret
client.testWebhook(id, "message.received");                     // POST /webhooks/{id}/test
client.webhookDeliveries(id, 20);                               // GET /webhooks/{id}/deliveries
client.triggerWebhookEvent("message.received");                 // fire a sample event
client.deleteWebhook(id);                                       // DELETE /webhooks/{id}
```

**Receive and process** deliveries with `com.bernisoftware.bzapper.webhooks.Webhooks`.
It verifies the HMAC-SHA256 signature (timing-safe, over the **raw** body bytes),
parses the envelope into a typed `WebhookEvent`, and dispatches to your handlers.

```java
import com.bernisoftware.bzapper.webhooks.Webhooks;
import com.bernisoftware.bzapper.webhooks.WebhookEvent;
import com.bernisoftware.bzapper.webhooks.WebhookSignatureException;

Webhooks hooks = new Webhooks(secret);  // the secret from createWebhook

hooks.on("message.received", event -> {
    System.out.println(event.sender().name() + ": " + event.payload().get("body"));
});
hooks.onAny(event -> log.info("event {} ({})", event.id(), event.type()));

// In your HTTP endpoint (servlet / Spring / Javalin — framework-agnostic).
// Pass the EXACT raw bytes received and the signature header; never re-serialized JSON.
byte[] rawBody = readRequestBody(request);              // your framework's raw body
String signature = request.getHeader(Webhooks.SIGNATURE_HEADER); // "X-Bzapper-Signature"
try {
    WebhookEvent event = hooks.handle(rawBody, signature); // verify + parse + dispatch
    // event.id() is stable — store it to skip duplicate retries (idempotency).
} catch (WebhookSignatureException e) {
    response.setStatus(400);  // bad signature — do NOT process
}
```

Need verification without dispatch? Use `hooks.verify(rawBody, signature)` (boolean)
or `hooks.constructEvent(rawBody, signature)` (verifies + parses to a `WebhookEvent`,
throwing `WebhookSignatureException` on a bad signature). Stateless forms, same as every
Berni SDK: `Webhooks.verify(secret, rawBody, signatureHeader)` and
`Webhooks.constructEvent(secret, rawBody, signatureHeader)`. The header is
`X-Bzapper-Signature: sha256=<hex>` = HMAC-SHA256 of the raw body, compared in constant time.

## bZapper Connect (partners)

bZapper Connect lets **your software's customers** subscribe to bZapper Pro and connect
their WhatsApp **without leaving your product**. You get back an API key authorized by the
customer and operate their WhatsApp with the regular `BzapperClient`. The customer stays a
direct bZapper account; the key only works while their Pro is paid.

The flow:

1. **Backend** — `BzapperPartner.createConnectSession(...)` returns a `sessionToken` (30 min).
2. **Front-end** — opens the embedded component with that token
   (`BzapperConnect.open({ session })`); when the customer finishes (Pro paid + WhatsApp
   connected) it emits `bzapper:complete` with a one-time `code` (10 min).
3. **Backend** — `exchangeCode(code)` returns the connection with the customer's raw
   `apiKey` (`bz_live_...`), shown **once**: store it.
4. **Backend** — `new BzapperClient(apiKey)` to send messages, list numbers, etc.

`BzapperPartner` authenticates with your partner secret (`Authorization: Bearer bz_partner_...`)
and shares the HTTP, JSON and `BzapperException` handling of `BzapperClient`. **Never ship
the partner secret to a browser.**

| Method | Endpoint |
| --- | --- |
| `me()` | `GET /partner/me` |
| `createConnectSession(externalId, customer, locale)` | `POST /partner/connect-sessions` |
| `exchangeCode(code)` | `POST /partner/connect/exchange` |
| `listConnections(externalId, status)` (both nullable) | `GET /partner/connections` |
| `getConnection(id)` | `GET /partner/connections/{id}` |
| `rotateConnectionKey(id)` — new key, the old one stops working | `POST /partner/connections/{id}/rotate-key` |
| `revokeConnection(id)` — ends it (204); does not cancel the customer's plan | `DELETE /partner/connections/{id}` |

Complete backend (Spring-style; any framework works the same way):

```java
import com.bernisoftware.bzapper.BzapperClient;
import com.bernisoftware.bzapper.BzapperException;
import com.bernisoftware.bzapper.BzapperPartner;
import com.bernisoftware.bzapper.model.ConnectCustomer;
import com.bernisoftware.bzapper.model.ConnectSession;
import com.bernisoftware.bzapper.model.PartnerConnection;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.webhooks.WebhookSignatureException;
import com.bernisoftware.bzapper.webhooks.Webhooks;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/bzapper")
public class BzapperConnectController {

    private final BzapperPartner partner = new BzapperPartner(System.getenv("BZAPPER_PARTNER_SECRET"));
    private final Webhooks hooks = new Webhooks(System.getenv("BZAPPER_PARTNER_WEBHOOK_SECRET"));
    private final CustomerRepository customers; // your persistence

    public BzapperConnectController(CustomerRepository customers) {
        this.customers = customers;

        // Connect lifecycle — event.connection() says which of YOUR customers it is about.
        hooks.on(Webhooks.EVENT_CONNECT_COMPLETED, e ->
                customers.markWhatsappActive(e.connection().externalId()));
        hooks.on(Webhooks.EVENT_CONNECT_SUSPENDED, e ->   // Pro unpaid: key answers 402 until paid
                customers.markWhatsappSuspended(e.connection().externalId()));
        hooks.on(Webhooks.EVENT_CONNECT_RESUMED, e ->     // paid again: the same key works
                customers.markWhatsappActive(e.connection().externalId()));
        hooks.on(Webhooks.EVENT_CONNECT_REVOKED, e ->     // final: drop the key
                customers.clearBzapperKey(e.connection().externalId()));

        // Project events (message.received, instance.status…) of active connections also
        // arrive here, with the same connection block.
        hooks.on("message.received", e ->
                customers.onIncomingMessage(e.connection().externalId(), e.payload()));
    }

    /** 1. The logged-in customer clicks "Connect WhatsApp": open a session. */
    @PostMapping("/session")
    public Map<String, String> session(@AuthenticationPrincipal Customer me) {
        ConnectSession s = partner.createConnectSession(
                me.getId(),                                        // your id = external_id
                ConnectCustomer.of(me.getName(), me.getEmail())
                        .withCompany(me.getCompanyName())
                        .withPhone(me.getPhone())                  // E.164, pre-fills the number
                        .withCountry("BR"),
                "pt-BR");
        return Map.of("session", s.sessionToken());               // only the token goes to the browser
    }

    /** 3. The component emitted bzapper:complete — exchange the code for the key. */
    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@AuthenticationPrincipal Customer me,
                                         @RequestBody Map<String, String> req) {
        PartnerConnection conn = partner.exchangeCode(req.get("code"));
        customers.saveBzapperKey(me.getId(), conn.id(), conn.apiKey()); // shown ONCE — encrypt at rest
        return ResponseEntity.noContent().build();
    }

    /** 4. Use the customer's key with the regular client. */
    @PostMapping("/notify")
    public ResponseEntity<?> notify(@AuthenticationPrincipal Customer me,
                                    @RequestBody Map<String, String> req) {
        BzapperClient client = new BzapperClient(customers.bzapperKey(me.getId()));
        try {
            client.sendText(SendOptions.to(req.get("to")), req.get("text"));
            return ResponseEntity.accepted().build();
        } catch (BzapperException e) {
            switch (e.getCode()) {
                case BzapperException.CONNECT_SUSPENDED:   // 402: Pro unpaid — NOT final, keep the key
                    return ResponseEntity.status(402)
                            .body(Map.of("error", "Your bZapper plan is pending payment."));
                case BzapperException.CONNECT_REVOKED:     // 401: connection ended — reconnect
                    customers.clearBzapperKey(me.getId());
                    return ResponseEntity.status(409).body(Map.of("error", "Reconnect WhatsApp."));
                default:
                    throw e;
            }
        }
    }

    /** Partner webhook: same HMAC verification as regular webhooks, over the RAW body. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] rawBody,
                                        @RequestHeader(Webhooks.SIGNATURE_HEADER) String signature) {
        try {
            hooks.handle(rawBody, signature); // verify + parse + dispatch (use event.id() for idempotency)
            return ResponseEntity.ok().build();
        } catch (WebhookSignatureException e) {
            return ResponseEntity.badRequest().build(); // bad signature — do NOT process
        }
    }
}
```

Operating connections:

```java
import com.bernisoftware.bzapper.model.ConnectionStatus;

partner.me();                                                          // who this secret belongs to
partner.listConnections("customer-42", null);                          // by your customer id
partner.listConnections(null, ConnectionStatus.SUSPENDED.value());     // by status
PartnerConnection c = partner.getConnection("conn-uuid");              // status, account, numbers
PartnerConnection rotated = partner.rotateConnectionKey("conn-uuid");  // rotated.apiKey(): new key, once
partner.revokeConnection("conn-uuid");                                 // 204; sends connect.revoked
```

`ConnectionStatus`: `PENDING_ACCOUNT`, `PENDING_PAYMENT`, `PENDING_NUMBER`, `ACTIVE` (the key
works), `SUSPENDED` (Pro unpaid → 402 `connect_suspended`, resumes by itself once paid),
`REVOKED` (final → 401 `connect_revoked`). Statuses newer than your SDK map to `UNKNOWN`.

**Partner webhook envelope** = the regular envelope + `connection`
(`event.connection()` → `id`, `externalId`, `accountId`, `projectId`, `status`). It is `null`
on regular (non-partner) webhooks. Event constants: `Webhooks.EVENT_CONNECT_COMPLETED`,
`EVENT_CONNECT_SUSPENDED`, `EVENT_CONNECT_RESUMED`, `EVENT_CONNECT_REVOKED`
(all in `Webhooks.CONNECT_EVENT_TYPES`).

### Connected apps (customer side)

A bZapper account can see and disconnect the partner apps using its WhatsApp:

```java
List<PartnerConnection> apps = client.listConnectedApps();  // GET /me/connections (partnerName, partnerLogoUrl)
client.revokeConnectedApp(apps.get(0).id());                // DELETE /me/connections/{id} (admin) — partner key stops at once
```

## Errors, retries and idempotency

Every failure throws `BzapperException` (unchecked). The error body is
`{ "code", "message", "locale" }` — **always branch on `code`** (stable, neutral), never on
the localized `message`. Send `getRequestId()` to support.

| Field | Meaning |
|---|---|
| `getCode()` | `body.code`, else `body.error`, else `HTTP_<status>`; `NETWORK_ERROR`; `INVALID_RESPONSE` |
| `getMessage()` | human-readable (translated via `locale`) |
| `getStatusCode()` / `getStatus()` | HTTP status (`0` on a network error) |
| `getRequestId()` | response `X-Request-Id`, else the one the SDK sent |
| `getRetryAfter()` | `Retry-After` as a `Duration` (429 only) |
| `getRequiredScope()` | `X-Required-Scope` (403 of scope only) |
| `getBody()` | the decoded error body (structured detail of some errors) |

Typed subclasses — all extend `BzapperException`, so `catch (BzapperException e)` still
catches everything: `AuthenticationException` (401), `PermissionDeniedException` (403),
`NotFoundException` (404), `ConflictException` (409), `ValidationException` (400/422),
`RateLimitException` (429), `ServerException` (5xx), `NetworkException` (connection/timeout,
status 0, code `NETWORK_ERROR`). Any other status comes as the base class. A 2xx whose body
is not JSON throws the base class with code `INVALID_RESPONSE`. An empty, `"."` or `".."`
path parameter (or an empty API key) throws `IllegalArgumentException` before any request.

```java
import com.bernisoftware.bzapper.*;

try {
    client.sendText(SendOptions.to("+5511999999999"), "Hi");
} catch (RateLimitException e) {
    retryLater(e.getRetryAfter());                // the SDK already retried maxRetries times
} catch (BzapperException e) {
    switch (e.getCode()) {
        case "not_connected" -> reconnect();
        case "unauthorized"  -> refreshApiKey();
        default -> log.error("bZapper {} ({}) request_id={}: {}",
                e.getCode(), e.getStatusCode(), e.getRequestId(), e.getMessage());
    }
}
```

**Retries.** Network errors/timeouts, 429, 502, 503 and 504 are retried up to `maxRetries`
(default 2): waiting `Retry-After` when present (capped at 60 s), else
`min(8, 0.5 × 2^attempt)` s + up to 25% jitter. A 500 or a 4xx is never retried.

**Idempotency.** Every write carries an `Idempotency-Key` — generated per logical call and
**the same on every retry**, so a retried send is never delivered twice (the API replays the
original response with `Idempotent-Replayed: true` for 24 h). Pass your own to also
deduplicate re-runs of YOUR code (e.g. a re-executed job):

```java
client.sendText(SendOptions.to("+5511999999999").withIdempotencyKey("order-42"), "Paid!");
client.withOptions(RequestOptions.idempotencyKey("order-42-contact"))  // any other write
      .createContact(Map.of("phone", "+5511999999999"));
```

Keys issued through bZapper Connect add two codes (constants on `BzapperException`):
`connect_suspended` (**402**, the customer's Pro is unpaid — temporary, keep the key) and
`connect_revoked` (**401**, the connection ended — final).

> Since the r2 transport, local transport failures use code `NETWORK_ERROR` (was
> `network_error`) and a non-JSON error body yields `HTTP_<status>` (was `http_error`).

## Example

A runnable example lives in [`examples/QuickStart.java`](examples/QuickStart.java).

## License

[MIT](LICENSE) © Berni Software
