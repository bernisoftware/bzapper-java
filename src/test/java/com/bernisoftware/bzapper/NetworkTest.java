package com.bernisoftware.bzapper;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link NetworkException}: server down, connection cut, timeout. */
class NetworkTest {

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /** TCP server that reads the request (records X-Request-Id) and never answers: hangs up or stays silent. */
    private static final class BlackHole implements AutoCloseable {
        final ServerSocket socket;
        final List<String> requestIds = Collections.synchronizedList(new ArrayList<>());
        final List<Socket> open = Collections.synchronizedList(new ArrayList<>());
        final boolean hangUp;

        BlackHole(boolean hangUp) throws IOException {
            this.hangUp = hangUp;
            this.socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::acceptLoop, "black-hole");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        private void acceptLoop() {
            while (!socket.isClosed()) {
                try {
                    Socket s = socket.accept();
                    open.add(s);
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase(Locale.ROOT).startsWith("x-request-id:")) {
                            requestIds.add(line.substring("x-request-id:".length()).trim());
                        }
                    }
                    if (hangUp) {
                        s.close();
                    }
                } catch (IOException e) {
                    return;
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
            synchronized (open) {
                for (Socket s : open) {
                    s.close();
                }
            }
        }
    }

    @Test
    void serverDownIsNetworkException() throws IOException {
        List<Duration> sleeps = new ArrayList<>();
        BzapperClient bz = BzapperClient.builder("http://127.0.0.1:" + closedPort(), "bz_live_unit")
                .timeout(Duration.ofSeconds(2)).build();
        bz.setSleeper(sleeps::add);
        NetworkException e = assertThrows(NetworkException.class, () -> bz.getInstance("i1"));
        assertInstanceOf(BzapperException.class, e);
        assertEquals(0, e.getStatusCode());
        assertEquals(0, e.getStatus());
        assertEquals("NETWORK_ERROR", e.getCode());
        assertTrue(e.getRequestId().matches("[0-9a-f]{32}"), e.getRequestId());
        assertEquals(2, sleeps.size(), "network errors are retried maxRetries (2) times");
        assertTrue(sleeps.get(0).toMillis() >= 500 && sleeps.get(0).toMillis() <= 625, "backoff 1: " + sleeps.get(0));
        assertTrue(sleeps.get(1).toMillis() >= 1000 && sleeps.get(1).toMillis() <= 1250, "backoff 2: " + sleeps.get(1));

        BzapperClient noRetry = BzapperClient.builder("http://127.0.0.1:" + closedPort(), "bz_live_unit").maxRetries(0).build();
        noRetry.setSleeper(d -> {
            throw new AssertionError("must not wait with maxRetries(0)");
        });
        assertThrows(NetworkException.class, noRetry::listProjects);
    }

    @Test
    void connectionCutCarriesTheSentRequestId() throws IOException {
        try (BlackHole hole = new BlackHole(true)) {
            List<Duration> sleeps = new ArrayList<>();
            BzapperClient bz = BzapperClient.builder(hole.baseUrl(), "bz_live_unit").maxRetries(1).build();
            bz.setSleeper(sleeps::add);
            NetworkException e = assertThrows(NetworkException.class, () -> bz.deleteWebhook("w1"));
            assertFalse(hole.requestIds.isEmpty());
            for (String sent : hole.requestIds) {
                assertEquals(sent, e.getRequestId(), "requestId = the X-Request-Id sent (on every attempt)");
            }
            assertEquals(1, sleeps.size());
        }
    }

    @Test
    void timeoutRepeatsTheSameRequestId() throws Exception {
        try (BlackHole hole = new BlackHole(false)) {
            List<Duration> sleeps = new ArrayList<>();
            BzapperClient bz = BzapperClient.builder(hole.baseUrl(), "bz_live_unit")
                    .timeout(Duration.ofMillis(200)).maxRetries(1).build();
            bz.setSleeper(sleeps::add);
            NetworkException e = assertThrows(NetworkException.class, () -> bz.getInstance("i1"));
            long deadline = System.currentTimeMillis() + 2000;
            while (hole.requestIds.size() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(2, hole.requestIds.size());
            assertEquals(hole.requestIds.get(0), hole.requestIds.get(1), "the retry repeats the X-Request-Id");
            assertEquals(hole.requestIds.get(0), e.getRequestId());
            assertEquals(1, sleeps.size());
        }
    }
}
