# bZapper Java SDK

Official Java client for the [bZapper](https://bzapper.com.br) WhatsApp gateway API — a
multi-tenant WhatsApp gateway with a REST HTTP API.

- Java 17+
- Built on the JDK `java.net.http.HttpClient` — the only runtime dependency is Jackson for JSON.
- Typed error model: every non-2xx response throws a `BzapperException` carrying a **stable code**.

## Install

Maven (`br.com.bernisoftware:bzapper`):

```xml
<dependency>
  <groupId>br.com.bernisoftware</groupId>
  <artifactId>bzapper</artifactId>
  <version>0.5.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("br.com.bernisoftware:bzapper:0.5.0")
```

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
        .locale("pt-BR")                  // sent as Accept-Language
        .timeout(Duration.ofSeconds(30))  // per-request timeout
        .build();
```

The 1-arg `builder(apiKey)` defaults the base URL to production. For dev/self-host,
use the 2-arg `builder(baseUrl, apiKey)` (e.g. `builder("http://localhost:8080", "bz_live_...")`).

Every request sends:

- `Authorization: Bearer <apiKey>`
- `Content-Type: application/json` (on requests with a body)
- `Accept-Language: <locale>` (only when a locale is set)

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
client.leaveGroup(g.jid(), inst);                                             // POST /groups/{jid}/leave

// --- Contacts -----------------------------------------------------------
Map<String, Object> check = client.contactsCheck(inst,
        List.of("+5511999999999", "+5511888888888"));                         // POST /contacts/check

// --- Profile ------------------------------------------------------------
Instance updated = client.setProfile(inst, ProfileUpdate.empty()             // PATCH /instances/{id}/profile
        .withDisplayName("Support")
        .withStatusMessage("We reply fast")
        .withPicture("iVBORw0KGgo..."));  // base64 image
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
throwing `WebhookSignatureException` on a bad signature).

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

## Error handling

Non-2xx responses throw `BzapperException`. The error body is
`{ "code", "message", "locale" }` — **always branch on `code`** (stable, neutral), never on
the localized `message`.

```java
import com.bernisoftware.bzapper.BzapperException;

try {
    client.sendText(SendOptions.to("+5511999999999"), "Hi");
} catch (BzapperException e) {
    switch (e.getCode()) {
        case "not_connected" -> reconnect();
        case "rate_limited"  -> backoff();        // HTTP 429
        case "unauthorized"  -> refreshApiKey();
        default -> log.error("bZapper {} ({}): {}", e.getCode(), e.getStatusCode(), e.getMessage());
    }
}
```

`getStatusCode()` returns the HTTP status (or `0` for local transport/serialization errors,
which carry codes like `network_error`).

Keys issued through bZapper Connect add two codes (constants on `BzapperException`):
`connect_suspended` (**402**, the customer's Pro is unpaid — temporary, keep the key) and
`connect_revoked` (**401**, the connection ended — final).

## Example

A runnable example lives in [`examples/QuickStart.java`](examples/QuickStart.java).

## License

[MIT](LICENSE) © Berni Software
