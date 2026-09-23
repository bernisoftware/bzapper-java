package com.bernisoftware.bzapper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code exportContacts} answers {@code text/csv}, NOT JSON, so it is deliberately out of the
 * generated conformance cases (BRIEF §6) and covered here: a fake server that replies with a CSV
 * attachment checks the path + query, that the request asks for CSV and the response is never run
 * through the JSON decoder, and that the text comes back byte-for-byte — including a row whose
 * fields carry a comma and quotes.
 */
class ContactsExportTest {

    /** A row with a comma and doubled quotes inside quoted fields — what a naive parser mangles. */
    private static final String CSV = "phone,name,email,status,source,tags,groups,created_at,last_activity_at\n"
            + "+5511999990000,Ana,ana@example.com,active,inbound,vip;novo,,2026-09-01T10:00:00Z,2026-09-20T18:30:00Z\n"
            + "+5511999990001,\"Silva, Bruno \"\"Bru\"\"\",bruno@example.com,pending_validation,import,\"a;b\",vendas,"
            + "2026-09-02T11:00:00Z,\n";

    private HttpServer server;
    private String baseUrl;
    private volatile HttpExchange last;
    private volatile byte[] lastBody;
    private volatile int errorStatus;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        InetSocketAddress a = server.getAddress();
        baseUrl = "http://" + a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            lastBody = exchange.getRequestBody().readAllBytes();
            last = exchange;
            if (errorStatus > 0) {
                byte[] error = ("{\"code\":\"admin_required\",\"message\":\"Só administradores exportam.\","
                        + "\"locale\":\"pt-BR\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(errorStatus, error.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(error);
                }
                return;
            }
            byte[] payload = CSV.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/csv; charset=utf-8");
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"contacts.csv\"");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        } finally {
            exchange.close();
        }
    }

    private BzapperClient client() {
        return BzapperClient.builder(baseUrl, "bz_live_unit").build();
    }

    private String header(String name) {
        List<String> values = last.getRequestHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private Map<String, String> query() {
        Map<String, String> out = new LinkedHashMap<>();
        URI uri = last.getRequestURI();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split("&", -1)) {
            int eq = part.indexOf('=');
            out.put(java.net.URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    /** The CSV text arrives intact — commas and quotes included — and nothing tries to parse JSON. */
    @Test
    void returnsTheRawCsvText() {
        String csv = client().exportContacts(Map.of());

        assertEquals(CSV, csv, "the CSV must come back exactly as the API produced it");
        assertTrue(csv.contains("\"Silva, Bruno \"\"Bru\"\"\""), "a field with a comma and quotes survives");
        assertEquals(4, csv.split("\n", -1).length, "header + 2 rows + trailing newline");
        assertEquals("GET", last.getRequestMethod());
        assertEquals("/contacts/export", last.getRequestURI().getRawPath(), "path");
        assertEquals(Map.of(), query(), "no filters = no query");
        assertEquals(0, lastBody.length, "a GET has no body");
        assertNull(header("Content-Type"), "no Content-Type without a body");
    }

    /** The request asks for CSV (never {@code application/json}) and carries the usual headers. */
    @Test
    void asksForCsvAndNotJson() {
        client().exportContacts(null);

        assertEquals("text/csv", header("Accept"), "Accept must be text/csv, not application/json");
        assertEquals("Bearer bz_live_unit", header("Authorization"));
        assertEquals(Version.CLIENT_ID, header("X-Bzapper-Client"));
        assertEquals(Version.CLIENT_ID, header("User-Agent"));
        assertNotNull(header("X-Request-Id"));
        assertNull(header("Idempotency-Key"), "a read carries no Idempotency-Key");
        assertEquals("/contacts/export", last.getRequestURI().getRawPath());
    }

    /** No-arg overload: same path, still no query. */
    @Test
    void noArgOverloadExportsEverything() {
        assertEquals(CSV, client().exportContacts());
        assertEquals("/contacts/export", last.getRequestURI().getRawPath());
        assertNull(last.getRequestURI().getRawQuery());
    }

    /** The same filters as listContacts: lists as CSV, booleans as true/false, dates ISO 8601 UTC. */
    @Test
    void sendsTheSameFiltersAsListContacts() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", "ana silva");
        filters.put("tags", List.of("vip", "novo"));
        filters.put("tags_match", "all");
        filters.put("groups", List.of("vendas"));
        filters.put("status", "active");
        filters.put("has_email", true);
        filters.put("created_after", Instant.parse("2026-01-01T00:00:00Z"));
        filters.put("sort", "name");
        filters.put("limit", 50);
        filters.put("city", null); // omitted

        client().exportContacts(filters);

        assertEquals("/contacts/export", last.getRequestURI().getRawPath());
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("search", "ana silva");
        expected.put("tags", "vip,novo");
        expected.put("tags_match", "all");
        expected.put("groups", "vendas");
        expected.put("status", "active");
        expected.put("has_email", "true");
        expected.put("created_after", "2026-01-01T00:00:00Z");
        expected.put("sort", "name");
        expected.put("limit", "50");
        assertEquals(expected, query(), "query");
        String raw = last.getRequestURI().getRawQuery();
        assertTrue(raw.contains("search=ana+silva") || raw.contains("search=ana%20silva"), raw);
        assertTrue(raw.toLowerCase(Locale.ROOT).contains("tags=vip%2cnovo"), "list joined with an encoded comma: " + raw);
    }

    /** An error on the CSV route still raises the normal typed exception from the JSON envelope. */
    @Test
    void errorsStillUseTheJsonEnvelope() {
        errorStatus = 403;
        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> client().exportContacts(Map.of("limit", 1)));
        assertEquals("admin_required", e.getCode());
        assertEquals(403, e.getStatusCode());
    }
}
