package com.bernisoftware.bzapper.webhooks;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Webhook receiver: verifies the HMAC-SHA256 signature, parses the envelope into
 * a typed {@link WebhookEvent} and routes it to handlers registered per event type.
 *
 * <p>The API signs every delivery with {@code X-Bzapper-Signature: sha256=<hex>}
 * where the hex is {@code HMAC_SHA256(secret, raw_body)} over the <b>raw</b> UTF-8
 * body bytes. It also sends {@code X-Bzapper-Event-Id} and {@code X-Bzapper-Event-Type}.
 *
 * <p>Quickstart:
 * <pre>{@code
 * Webhooks hooks = new Webhooks("whsec_...");   // the secret from createWebhook
 *
 * hooks.on("message.received", event ->
 *         System.out.println(event.sender().name() + ": " + event.payload().get("body")));
 *
 * // In your HTTP endpoint (framework-agnostic): verify, parse and dispatch.
 * // Pass the EXACT bytes received, never re-serialized JSON.
 * hooks.handle(rawBody, request.getHeader(Webhooks.SIGNATURE_HEADER));
 * }</pre>
 *
 * <p>Instances are safe to build once and reuse. Register handlers before serving.
 */
public final class Webhooks {

    /** Header carrying the {@code sha256=<hex>} signature of the raw body. */
    public static final String SIGNATURE_HEADER = "X-Bzapper-Signature";

    /** Header carrying the stable event id (use it for idempotency). */
    public static final String EVENT_ID_HEADER = "X-Bzapper-Event-Id";

    /** Header carrying the event type. */
    public static final String EVENT_TYPE_HEADER = "X-Bzapper-Event-Type";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final byte[] secretBytes;
    private final ObjectMapper mapper;
    private final Map<String, List<Consumer<WebhookEvent>>> handlers = new HashMap<>();
    private final List<Consumer<WebhookEvent>> any = new ArrayList<>();

    /**
     * @param secret the webhook's signing secret (returned once by {@code createWebhook})
     */
    public Webhooks(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Webhooks: `secret` is required.");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Returns {@code true} iff {@code signature} matches the HMAC of the <b>raw</b>
     * body bytes. Timing-safe. Pass the exact bytes received — never the
     * re-serialized JSON.
     */
    public boolean verify(byte[] rawBody, String signature) {
        if (signature == null || rawBody == null) {
            return false;
        }
        String expected = "sha256=" + hexHmac(rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Convenience overload: verifies against the UTF-8 bytes of {@code rawBody}. */
    public boolean verify(String rawBody, String signature) {
        return rawBody != null && verify(rawBody.getBytes(StandardCharsets.UTF_8), signature);
    }

    /**
     * Verifies the signature and parses the body into a typed {@link WebhookEvent}
     * (no dispatch).
     *
     * @throws WebhookSignatureException if the signature is missing or invalid
     */
    public WebhookEvent constructEvent(byte[] rawBody, String signature) {
        if (!verify(rawBody, signature)) {
            throw new WebhookSignatureException("invalid webhook signature");
        }
        try {
            JsonNode raw = mapper.readTree(rawBody);
            WebhookEvent event = mapper.treeToValue(raw, WebhookEvent.class);
            event.setRaw(raw);
            return event;
        } catch (IOException e) {
            throw new WebhookSignatureException("failed to parse webhook body: " + e.getMessage(), e);
        }
    }

    /** Convenience overload: parses the UTF-8 bytes of {@code rawBody}. */
    public WebhookEvent constructEvent(String rawBody, String signature) {
        return constructEvent(
                rawBody != null ? rawBody.getBytes(StandardCharsets.UTF_8) : null, signature);
    }

    /**
     * Registers a handler for an event type (e.g. {@code "message.received"}).
     * Multiple handlers may be registered for the same type; they run in order.
     *
     * @return this, for chaining
     */
    public Webhooks on(String eventType, Consumer<WebhookEvent> handler) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(handler, "handler");
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
        return this;
    }

    /**
     * Registers a handler that runs for <b>every</b> event, after the type-specific
     * handlers.
     *
     * @return this, for chaining
     */
    public Webhooks onAny(Consumer<WebhookEvent> handler) {
        Objects.requireNonNull(handler, "handler");
        any.add(handler);
        return this;
    }

    /**
     * Verifies, parses and dispatches a delivery to the matching handlers, then to
     * the {@code onAny} handlers.
     *
     * <p>Returns the parsed event (use {@link WebhookEvent#id()} for idempotency).
     *
     * @throws WebhookSignatureException if the signature is invalid — do NOT process
     */
    public WebhookEvent handle(byte[] rawBody, String signature) {
        WebhookEvent event = constructEvent(rawBody, signature);
        List<Consumer<WebhookEvent>> typed = handlers.get(event.type());
        if (typed != null) {
            for (Consumer<WebhookEvent> h : typed) {
                h.accept(event);
            }
        }
        for (Consumer<WebhookEvent> h : any) {
            h.accept(event);
        }
        return event;
    }

    /** Convenience overload: dispatches the UTF-8 bytes of {@code rawBody}. */
    public WebhookEvent handle(String rawBody, String signature) {
        return handle(rawBody != null ? rawBody.getBytes(StandardCharsets.UTF_8) : null, signature);
    }

    private String hexHmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            return toHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is mandatory on every JVM; this should never happen.
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
