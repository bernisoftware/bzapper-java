package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.Group;
import com.bernisoftware.bzapper.model.GroupParticipant;
import com.bernisoftware.bzapper.model.SendOptions;
import com.bernisoftware.bzapper.webhooks.WebhookEvent;
import com.bernisoftware.bzapper.webhooks.WebhookSender;
import com.bernisoftware.bzapper.webhooks.Webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Idempotency-Key nos envios, quoted_participant no corpo, prévia de convite de
 * grupo (size/phone/lid) e phone do remetente no webhook.
 */
class SendAndGroupsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;

    private String method;
    private String uri;
    private String idempotencyKey;
    private String body;
    private String response = "{\"message_id\":\"m1\",\"status\":\"queued\"}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            method = ex.getRequestMethod();
            uri = ex.getRequestURI().toString();
            idempotencyKey = ex.getRequestHeaders().getFirst("Idempotency-Key");
            body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
            ex.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BzapperClient client() {
        return new BzapperClient(baseUrl, "bz_live_test");
    }

    @Test
    void idempotencyKeyGoesInHeaderNotBody() throws IOException {
        client().sendText(SendOptions.to("+5511999999999")
                .withQuotedMessageId("q1")
                .withQuotedParticipant("+5511988887777")
                .withIdempotencyKey("order-42"), "hi");
        assertEquals("/messages/text", uri);
        assertEquals("order-42", idempotencyKey);
        JsonNode sent = JSON.readTree(body);
        assertEquals("+5511988887777", sent.get("quoted_participant").asText());
        assertFalse(body.contains("order-42"));

        client().sendReaction(SendOptions.to("x").withIdempotencyKey("r-1"), "q1", "👍");
        assertEquals("/messages/reaction", uri);
        assertEquals("r-1", idempotencyKey);
    }

    @Test
    void generatedIdempotencyHeaderByDefault() {
        // Padrão Berni r2 (BRIEF §3): every write carries an Idempotency-Key, generated per
        // logical call when the caller doesn't pass one — it is what makes retries safe.
        client().sendText(SendOptions.to("x"), "hi");
        assertNotNull(idempotencyKey);
        assertTrue(idempotencyKey.matches("[0-9a-f-]{36}"), idempotencyKey);
        String first = idempotencyKey;
        client().sendText(SendOptions.to("x"), "hi");
        assertNotEquals(first, idempotencyKey, "a new logical call gets a new key");
    }

    @Test
    void legacySendOptionsConstructorStillWorks() {
        SendOptions o = new SendOptions("x", null, null, null, null, null, null, null);
        assertNull(o.quotedParticipant());
        assertNull(o.idempotencyKey());
        assertEquals("r", o.withClientReference("r").withIdempotencyKey("k").clientReference());
    }

    @Test
    void previewGroupInvite() {
        response = """
                {"jid":"1@g.us","name":"G","size":3,
                 "participants":[{"jid":"9@lid","phone":"+5511999999999","lid":"9@lid","is_admin":true,"is_super_admin":false}]}""";
        Group g = client().previewGroupInvite("i1", "ABC");
        assertEquals("POST", method);
        assertEquals("/groups/join/preview?instance_id=i1", uri);
        assertEquals("{\"code\":\"ABC\"}", body);
        assertEquals(3, g.size());
        GroupParticipant p = g.participants().get(0);
        assertEquals("+5511999999999", p.phone());
        assertEquals("9@lid", p.lid());
        assertEquals(Boolean.TRUE, p.isAdmin());
        // construtores antigos continuam compilando
        assertNull(new Group("j", "n", null, null, List.of(), null).size());
        assertNull(new GroupParticipant("j", true, false).phone());
    }

    @Test
    void webhookSenderPhone() throws Exception {
        String secret = "whsec_x";
        String raw = """
                {"event_id":"ev1","event_type":"group.left","timestamp":"2026-09-17T12:00:00Z","instance_id":"i1",
                 "sender":{"jid":"9@lid","lid":"9@lid","phone":"+5511999999999","name":"Ana"},
                 "payload":{"reason":"removed"}}""";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = "sha256=" + HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        WebhookEvent e = new Webhooks(secret).constructEvent(raw, sig);
        assertEquals("+5511999999999", e.sender().phone());
        assertEquals("Ana", e.sender().name());
        assertNull(new WebhookSender("j", "l", "n").phone());
    }
}
