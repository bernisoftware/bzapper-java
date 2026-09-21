package com.bernisoftware.bzapper;

import com.bernisoftware.bzapper.model.ConnectCustomer;
import com.bernisoftware.bzapper.model.ConnectSession;
import com.bernisoftware.bzapper.model.ConnectionStatus;
import com.bernisoftware.bzapper.model.Partner;
import com.bernisoftware.bzapper.model.PartnerConnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bZapper Connect: cliente do parceiro + "apps conectados" do cliente, contra um
 * servidor HTTP local que grava a requisição e devolve uma resposta enlatada.
 */
class ConnectTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONNECTION = """
            {"id":"c1","external_id":"cust-42","status":"active","account_id":"t1","project_id":"p1",
             "customer":{"name":"Ana","email":"ana@boxy.com","company":"Boxy"},
             "numbers":[{"id":"n1","phone":"+5511988887777","status":"connected"}],
             "activated_at":"2026-09-01T10:00:00Z","created_at":"2026-09-01T09:00:00Z"}""";

    private HttpServer server;
    private String baseUrl;

    // Última requisição recebida.
    private String method;
    private String uri;
    private String auth;
    private String body;

    // Próxima resposta.
    private int status = 200;
    private String response = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            method = ex.getRequestMethod();
            uri = ex.getRequestURI().toString();
            auth = ex.getRequestHeaders().getFirst("Authorization");
            body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            if (status == 204) {
                ex.sendResponseHeaders(204, -1);
            } else {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(status, out.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(out);
                }
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

    private BzapperPartner partner() {
        return new BzapperPartner(baseUrl, "bz_partner_test");
    }

    @Test
    void meUsesPartnerSecret() {
        response = """
                {"id":"pa1","slug":"boxy","name":"Boxy","logo_url":"https://x/logo.png",
                 "allowed_origins":["https://app.boxy.com"],"webhook_url":"https://boxy.com/wh",
                 "key_scopes":["messages:send"]}""";
        Partner p = partner().me();
        assertEquals("GET", method);
        assertEquals("/partner/me", uri);
        assertEquals("Bearer bz_partner_test", auth);
        assertEquals("boxy", p.slug());
        assertEquals(List.of("https://app.boxy.com"), p.allowedOrigins());
    }

    @Test
    void createConnectSession() throws IOException {
        status = 201;
        response = "{\"session_token\":\"cs_abc\",\"expires_at\":\"2026-09-17T12:30:00Z\",\"connection\":"
                + CONNECTION.replace("\"active\"", "\"pending_account\"") + "}";

        ConnectSession s = partner().createConnectSession("cust-42",
                ConnectCustomer.of("Ana", "ana@boxy.com").withCompany("Boxy").withCountry("BR"), "pt-BR");

        assertEquals("POST", method);
        assertEquals("/partner/connect-sessions", uri);
        JsonNode sent = JSON.readTree(body);
        assertEquals("cust-42", sent.get("external_id").asText());
        assertEquals("pt-BR", sent.get("locale").asText());
        assertEquals("ana@boxy.com", sent.get("customer").get("email").asText());
        assertEquals("BR", sent.get("customer").get("country").asText());
        // Campos não informados não viajam como null.
        assertFalse(sent.get("customer").has("phone"));

        assertEquals("cs_abc", s.sessionToken());
        assertEquals(ConnectionStatus.PENDING_ACCOUNT, s.connection().status());
        assertNull(s.connection().apiKey());
    }

    @Test
    void createConnectSessionWithoutLocaleOmitsIt() throws IOException {
        status = 201;
        response = "{\"session_token\":\"cs_abc\",\"connection\":" + CONNECTION + "}";
        partner().createConnectSession("cust-42", ConnectCustomer.of("Ana", "ana@boxy.com"));
        assertFalse(JSON.readTree(body).has("locale"));
    }

    @Test
    void exchangeCodeReturnsApiKey() throws IOException {
        response = CONNECTION.replace("\"id\":\"c1\"", "\"id\":\"c1\",\"api_key\":\"bz_live_secret\"");

        PartnerConnection c = partner().exchangeCode("cc_91ab");

        assertEquals("POST", method);
        assertEquals("/partner/connect/exchange", uri);
        assertEquals("cc_91ab", JSON.readTree(body).get("code").asText());
        assertEquals("bz_live_secret", c.apiKey());
        assertEquals("cust-42", c.externalId());
        assertTrue(c.isActive());
        assertEquals("+5511988887777", c.numbers().get(0).phone());
        assertEquals("Boxy", c.customer().company());
    }

    @Test
    void exchangeInvalidCodeRaises() {
        status = 400;
        response = "{\"code\":\"invalid_code\",\"message\":\"Code inválido\",\"locale\":\"pt-BR\"}";
        BzapperException e = assertThrows(BzapperException.class, () -> partner().exchangeCode("bad"));
        assertEquals("invalid_code", e.getCode());
        assertEquals(400, e.getStatusCode());
    }

    @Test
    void listConnectionsFilters() {
        response = "{\"data\":[" + CONNECTION + "]}";

        List<PartnerConnection> list = partner().listConnections("cust 42/x", ConnectionStatus.ACTIVE.value());

        assertEquals("GET", method);
        assertEquals("/partner/connections?external_id=cust+42%2Fx&status=active", uri);
        assertEquals(1, list.size());
        assertEquals("c1", list.get(0).id());
    }

    @Test
    void listConnectionsWithoutFilters() {
        response = "{\"data\":[]}";
        assertTrue(partner().listConnections(null, null).isEmpty());
        assertEquals("/partner/connections", uri);

        partner().listConnections(null, "suspended");
        assertEquals("/partner/connections?status=suspended", uri);
    }

    @Test
    void unknownStatusDoesNotBreak() {
        response = "{\"data\":[" + CONNECTION.replace("\"active\"", "\"brand_new_status\"") + "]}";
        assertEquals(ConnectionStatus.UNKNOWN, partner().listConnections().get(0).status());
    }

    @Test
    void getConnection() {
        response = CONNECTION;
        PartnerConnection c = partner().getConnection("c1");
        assertEquals("GET", method);
        assertEquals("/partner/connections/c1", uri);
        assertEquals("p1", c.projectId());
    }

    @Test
    void rotateConnectionKey() {
        response = CONNECTION.replace("\"id\":\"c1\"", "\"id\":\"c1\",\"api_key\":\"bz_live_new\"");
        PartnerConnection c = partner().rotateConnectionKey("c1");
        assertEquals("POST", method);
        assertEquals("/partner/connections/c1/rotate-key", uri);
        assertEquals("bz_live_new", c.apiKey());
    }

    @Test
    void rotateConnectionKeyNotActive() {
        status = 409;
        response = "{\"code\":\"connection_not_active\",\"message\":\"x\"}";
        BzapperException e = assertThrows(BzapperException.class, () -> partner().rotateConnectionKey("c1"));
        assertEquals("connection_not_active", e.getCode());
    }

    @Test
    void revokeConnection204() {
        status = 204;
        partner().revokeConnection("c1");
        assertEquals("DELETE", method);
        assertEquals("/partner/connections/c1", uri);
    }

    @Test
    void listConnectedApps() {
        response = "{\"data\":[" + CONNECTION.replace("\"id\":\"c1\"",
                "\"id\":\"c1\",\"partner_name\":\"Boxy\",\"partner_logo_url\":\"https://x/logo.png\"") + "]}";

        List<PartnerConnection> apps = new BzapperClient(baseUrl, "bz_live_customer").listConnectedApps();

        assertEquals("GET", method);
        assertEquals("/me/connections", uri);
        assertEquals("Bearer bz_live_customer", auth);
        assertEquals("Boxy", apps.get(0).partnerName());
        assertEquals("https://x/logo.png", apps.get(0).partnerLogoUrl());
    }

    @Test
    void revokeConnectedApp204() {
        status = 204;
        new BzapperClient(baseUrl, "bz_live_customer").revokeConnectedApp("c1");
        assertEquals("DELETE", method);
        assertEquals("/me/connections/c1", uri);
    }

    @Test
    void suspendedKeyRaises402() {
        status = 402;
        response = "{\"code\":\"connect_suspended\",\"message\":\"Pro em aberto\"}";
        BzapperException e = assertThrows(BzapperException.class,
                () -> new BzapperClient(baseUrl, "bz_live_customer").listInstances());
        assertEquals(BzapperException.CONNECT_SUSPENDED, e.getCode());
        assertEquals(402, e.getStatusCode());
    }
}
