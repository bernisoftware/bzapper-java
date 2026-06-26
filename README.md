# bZapper Java SDK

Official Java client for the [bZapper](https://bzapper.com.br) WhatsApp gateway API — a
multi-tenant WhatsApp gateway with a REST HTTP API.

- Java 17+
- Built on the JDK `java.net.http.HttpClient` — the only runtime dependency is Jackson for JSON.
- Typed error model: every non-2xx response throws a `BzapperException` carrying a **stable code**.

## Install

Maven (`com.bernisoftware:bzapper`):

```xml
<dependency>
  <groupId>com.bernisoftware</groupId>
  <artifactId>bzapper</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.bernisoftware:bzapper:0.1.0")
```

## Hello world

```java
import com.bernisoftware.bzapper.BzapperClient;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.model.SentMessage;

BzapperClient client = new BzapperClient("https://api.bzapper.com.br", "bz_live_...");

SentMessage msg = client.sendText(SendOptions.to("+5511999999999"), "Hello from bZapper!");
System.out.println(msg.messageId());
```

## Configuration

Use the constructor for the defaults, or the builder for `locale` and `timeout`:

```java
import java.time.Duration;

BzapperClient client = BzapperClient.builder("http://localhost:8080", "bz_live_...")
        .locale("pt-BR")                  // sent as Accept-Language
        .timeout(Duration.ofSeconds(30))  // per-request timeout
        .build();
```

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
        .withClientReference("order-42")    // echoed back on status events
        .withMentions(java.util.List.of("5511888888888@s.whatsapp.net"));
```

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

## Example

A runnable example lives in [`examples/QuickStart.java`](examples/QuickStart.java).

## License

[MIT](LICENSE) © Berni Software
