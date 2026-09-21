package com.bernisoftware.bzapper.webhooks;

import com.bernisoftware.bzapper.model.ConnectionStatus;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Webhooks do parceiro: mesmo HMAC, envelope + bloco {@code connection}. */
class ConnectWebhooksTest {

    private static final String SECRET = "whsec_partner";

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void partnerEnvelopeCarriesConnection() throws Exception {
        String body = """
                {"event_id":"ev1","event_type":"connect.suspended","timestamp":"2026-09-17T12:00:00Z",
                 "payload":{"status":"suspended"},
                 "connection":{"id":"c1","external_id":"cust-42","account_id":"t1","project_id":"p1","status":"suspended"}}""";

        List<String> seen = new ArrayList<>();
        Webhooks hooks = new Webhooks(SECRET)
                .on(Webhooks.EVENT_CONNECT_SUSPENDED, e -> seen.add(e.connection().externalId()));

        WebhookEvent ev = hooks.handle(body, sign(body));

        assertEquals(List.of("cust-42"), seen);
        assertEquals("c1", ev.connection().id());
        assertEquals("t1", ev.connection().accountId());
        assertEquals("p1", ev.connection().projectId());
        assertEquals(ConnectionStatus.SUSPENDED, ev.connection().status());
    }

    @Test
    void regularEnvelopeHasNoConnection() throws Exception {
        String body = "{\"event_id\":\"ev2\",\"event_type\":\"message.received\",\"payload\":{\"body\":\"oi\"}}";
        assertNull(new Webhooks(SECRET).constructEvent(body, sign(body)).connection());
    }

    @Test
    void badSignatureRejected() {
        String body = "{\"event_id\":\"ev3\",\"event_type\":\"connect.revoked\"}";
        assertThrows(WebhookSignatureException.class,
                () -> new Webhooks(SECRET).handle(body, "sha256=00"));
    }

    @Test
    void connectEventConstants() {
        assertEquals(List.of("connect.completed", "connect.suspended", "connect.resumed", "connect.revoked"),
                Webhooks.CONNECT_EVENT_TYPES);
    }
}
