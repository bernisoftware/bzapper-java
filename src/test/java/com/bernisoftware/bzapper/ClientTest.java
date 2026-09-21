package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.SendOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transport behaviour: idempotency keys, headers, argument errors, error fields, retries. */
class ClientTest {

    private FakeServer server;
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = new FakeServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private BzapperClient client(FakeServer.Reply... replies) {
        server.reset(List.of(replies));
        BzapperClient c = BzapperClient.builder(server.baseUrl(), "bz_live_unit").locale("pt-BR").projectId("p1").build();
        c.setSleeper(sleeps::add);
        return c;
    }

    private static FakeServer.Reply ok(Object body) {
        return new FakeServer.Reply(200, null, body);
    }

    @Test
    void userIdempotencyKeyIsSentVerbatim() {
        BzapperClient c = client(ok(Map.of("id", "t1")), new FakeServer.Reply(202, null, Map.of("message_id", "m1")));
        c.withOptions(RequestOptions.idempotencyKey("pedido-4471/Ab_9")).createTag("vip", "VIP", null);
        c.sendText(SendOptions.to("+5511999999999").withIdempotencyKey("order-42"), "hi");
        List<FakeServer.Recorded> r = server.requests();
        assertEquals("pedido-4471/Ab_9", r.get(0).header("idempotency-key"));
        assertEquals("order-42", r.get(1).header("idempotency-key"));
    }

    @Test
    void optionalHeadersAndNoKeyOnReads() {
        BzapperClient c = client(ok(Map.of("data", List.of())));
        c.listTags();
        FakeServer.Recorded r = server.requests().get(0);
        assertEquals("pt-BR", r.header("accept-language"));
        assertEquals("p1", r.header("x-project-id"));
        assertNull(r.header("idempotency-key"));
        assertTrue(r.header("x-request-id").matches("[0-9a-f]{32}"));
    }

    @Test
    void argumentErrorsBeforeAnyRequest() {
        BzapperClient c = client();
        assertThrows(IllegalArgumentException.class, () -> c.getContact(""));
        assertThrows(IllegalArgumentException.class, () -> c.getContact("."));
        assertThrows(IllegalArgumentException.class, () -> c.deleteWebhook(".."));
        assertThrows(IllegalArgumentException.class, () -> new BzapperClient(" "));
        assertThrows(IllegalArgumentException.class, () -> BzapperClient.builder("bz_live_x").maxRetries(-1));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void pathSegmentsArePercentEncoded() {
        BzapperClient c = client(new FakeServer.Reply(204, null, null));
        c.deleteLabel("a/b c", "i 1");
        FakeServer.Recorded r = server.requests().get(0);
        assertEquals("/labels/a%2Fb%20c", r.rawPath);
        assertEquals(List.of("instance_id=i 1"), r.sortedQuery());
    }

    @Test
    void listFiltersGoAsCsvAndBooleansAsText() {
        BzapperClient c = client(ok(Map.of("data", List.of())));
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("tags", List.of("vip", "lead"));
        filters.put("has_email", true);
        filters.put("created_after", java.time.Instant.parse("2026-09-21T12:00:00Z"));
        filters.put("city", null);
        c.listContacts(filters);
        assertEquals(List.of("created_after=2026-09-21T12:00:00Z", "has_email=true", "tags=vip,lead"),
                server.requests().get(0).sortedQuery());
    }

    @Test
    void errorFields() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("X-Request-Id", "req-1");
        h.put("X-Required-Scope", "contacts:write");
        BzapperClient c = client(new FakeServer.Reply(403, h,
                Map.of("code", "insufficient_scope", "message", "Escopo insuficiente", "locale", "pt-BR")));
        PermissionDeniedException e = assertThrows(PermissionDeniedException.class, () -> c.createTag("k", "n", null));
        assertEquals("insufficient_scope", e.getCode());
        assertEquals("Escopo insuficiente", e.getMessage());
        assertEquals("pt-BR", e.getLocale());
        assertEquals(403, e.getStatusCode());
        assertEquals("req-1", e.getRequestId());
        assertEquals("contacts:write", e.getRequiredScope());
        assertEquals("insufficient_scope", ((Map<?, ?>) e.getBody()).get("code"));
        assertTrue(sleeps.isEmpty(), "403 is not retried");
    }

    @Test
    void errorCodeFallsBackToErrorFieldThenStatus() {
        BzapperClient c = client(new FakeServer.Reply(400, null, Map.of("error", "bad_thing")),
                new FakeServer.Reply(402, null, "Payment Required"));
        ValidationException v = assertThrows(ValidationException.class, c::getMe);
        assertEquals("bad_thing", v.getCode());
        BzapperException b = assertThrows(BzapperException.class, c::getMe);
        assertSame(BzapperException.class, b.getClass());
        assertEquals("HTTP_402", b.getCode());
        assertEquals("Payment Required", b.getBody());
    }

    @Test
    void invalidSuccessBody() {
        BzapperClient c = client(ok("<html>proxy</html>"));
        BzapperException e = assertThrows(BzapperException.class, c::getMe);
        assertEquals(BzapperException.INVALID_RESPONSE, e.getCode());
        assertEquals(200, e.getStatusCode());
    }

    @Test
    void retriesReuseIdsAndHonourRetryAfter() {
        Map<String, String> ra = Map.of("Retry-After", "120");
        BzapperClient c = client(new FakeServer.Reply(503, null, Map.of("code", "unavailable")),
                new FakeServer.Reply(429, ra, Map.of("code", "rate_limited")),
                ok(Map.of("id", "t1")));
        assertEquals("t1", c.createTag("k", "n", "#fff").get("id"));
        List<FakeServer.Recorded> r = server.requests();
        assertEquals(3, r.size());
        assertEquals(r.get(0).header("x-request-id"), r.get(2).header("x-request-id"));
        assertEquals(r.get(0).header("idempotency-key"), r.get(2).header("idempotency-key"));
        assertEquals(2, sleeps.size());
        assertTrue(sleeps.get(0).toMillis() >= 500 && sleeps.get(0).toMillis() <= 625, "backoff: " + sleeps.get(0));
        assertEquals(Duration.ofSeconds(60), sleeps.get(1), "Retry-After is capped at 60 s");
    }

    @Test
    void partnerClientSharesTheTransport() {
        server.reset(List.of(new FakeServer.Reply(401, null, Map.of("code", "unauthorized"))));
        BzapperPartner p = BzapperPartner.builder(server.baseUrl(), "bz_partner_x").maxRetries(0).build();
        BzapperException e = assertThrows(BzapperException.class, p::me);
        assertInstanceOf(AuthenticationException.class, e);
        assertEquals("Bearer bz_partner_x", server.requests().get(0).header("authorization"));
    }
}
