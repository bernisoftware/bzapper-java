package com.bernisoftware.bzapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The version lives in two places ({@code pom.xml} and {@code Version.java}) and they must not
 * diverge: {@link Version#VERSION} goes in the {@code X-Bzapper-Client} header of EVERY request —
 * it is how the API knows whom to warn when a fix requires upgrading the SDK.
 */
class VersionTest {
    // Same patterns as scripts/release-sdks.sh (1st <version> of the pom = the artifact).
    private static final Pattern POM_RE = Pattern.compile("<artifactId>bzapper</artifactId>\\s*<version>([^<]+)</version>");
    private static final Pattern VERSION_JAVA_RE = Pattern.compile("public static final String VERSION = \"([^\"]+)\"");

    private FakeServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void constantMatchesThePom() throws IOException {
        String pom = Files.readString(ConformanceTest.projectDir().resolve("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = POM_RE.matcher(pom);
        assertTrue(m.find(), "<artifactId>bzapper</artifactId><version> in pom.xml");
        assertEquals(m.group(1), Version.VERSION, "Version.VERSION diverged from pom.xml — the bump must change both");
        assertFalse(m.find(), "a single artifact version in pom.xml");
    }

    @Test
    void releasePatternMatchesVersionJava() throws IOException {
        Path file = ConformanceTest.projectDir().resolve("src/main/java/com/bernisoftware/bzapper/Version.java");
        Matcher m = VERSION_JAVA_RE.matcher(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(m.find());
        assertEquals(Version.VERSION, m.group(1));
    }

    @Test
    void clientId() {
        assertEquals("bzapper-java/" + Version.VERSION, Version.CLIENT_ID);
        assertTrue(Version.CLIENT_ID.matches("^bzapper-java/\\d+\\.\\d+\\.\\d+$"), Version.CLIENT_ID);
    }

    @Test
    void sentAsXBzapperClientAndUserAgent() {
        server.reset(List.of(new FakeServer.Reply(200, null, Map.of("data", List.of()))));
        BzapperClient.builder(server.baseUrl(), "bz_live_unit").build().listProjects();
        FakeServer.Recorded r = server.requests().get(0);
        Pattern expected = Pattern.compile("^bzapper-java/" + Pattern.quote(Version.VERSION) + "$");
        assertNotNull(r.header("x-bzapper-client"));
        assertTrue(expected.matcher(r.header("x-bzapper-client")).matches(), r.header("x-bzapper-client"));
        assertEquals(r.header("x-bzapper-client"), r.header("user-agent"));
    }

    /** Under the {@code artifact} profile (failsafe) the suite runs against the packaged jar — check it really is the jar. */
    @Test
    void runningAgainstThePackagedJar() throws URISyntaxException, IOException {
        assumeTrue(Boolean.getBoolean("bzapper.artifactTest"), "only under the artifact profile (mvn -P artifact verify)");
        Path origin = Path.of(BzapperClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(origin.getFileName().toString().endsWith(".jar"), "the SDK came from " + origin + ", not the jar");
        try (JarFile jar = new JarFile(origin.toFile())) {
            Attributes main = jar.getManifest().getMainAttributes();
            assertEquals(Version.VERSION, main.getValue("Implementation-Version"), "Implementation-Version");
            assertTrue(jar.getEntry("com/bernisoftware/bzapper/BzapperClient.class") != null);
            assertTrue(jar.getEntry("com/bernisoftware/bzapper/FakeServer.class") == null, "test class inside the jar");
        }
    }
}
