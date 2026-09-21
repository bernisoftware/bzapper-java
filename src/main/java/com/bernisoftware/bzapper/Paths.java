package com.bernisoftware.bzapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Path segments (percent-encoding per segment) and query strings. */
final class Paths {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Paths() {
    }

    /**
     * One percent-encoded path segment ({@code abc 1} → {@code abc%201}, {@code /} → {@code %2F}).
     * Empty, {@code "."} and {@code ".."} are rejected before any request (the HTTP stack
     * would resolve them).
     */
    static String seg(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' must not be empty");
        }
        if (value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("'" + name + "' must not be '.' or '..'");
        }
        return encode(value);
    }

    /** RFC 3986: keeps {@code A-Z a-z 0-9 - . _ ~}; everything else becomes {@code %XX} of the UTF-8 bytes. */
    static String encode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length + 8);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
            }
        }
        return sb.toString();
    }

    /** Query value encoding (form style, as the SDK always did). */
    static String q(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Query builder: omits nulls; booleans as true/false; lists as CSV; dates as ISO 8601 UTC {@code Z}. */
    static final class Query {
        private final List<String> parts = new ArrayList<>();

        Query add(String name, Object value) {
            if (value == null) {
                return this;
            }
            parts.add(q(name) + "=" + q(format(value)));
            return this;
        }

        Query addAll(Map<String, ?> values) {
            if (values != null) {
                for (Map.Entry<String, ?> e : values.entrySet()) {
                    add(e.getKey(), e.getValue());
                }
            }
            return this;
        }

        @Override
        public String toString() {
            return parts.isEmpty() ? "" : "?" + String.join("&", parts);
        }

        static String format(Object value) {
            if (value instanceof Boolean) {
                return ((Boolean) value) ? "true" : "false";
            }
            if (value instanceof Instant) {
                return DateTimeFormatter.ISO_INSTANT.format((Instant) value);
            }
            if (value instanceof OffsetDateTime) {
                return DateTimeFormatter.ISO_INSTANT.format(((OffsetDateTime) value).toInstant());
            }
            if (value instanceof ZonedDateTime) {
                return DateTimeFormatter.ISO_INSTANT.format(((ZonedDateTime) value).toInstant());
            }
            if (value instanceof Collection) {
                List<String> items = new ArrayList<>();
                for (Object item : (Collection<?>) value) {
                    if (item != null) {
                        items.add(format(item));
                    }
                }
                return String.join(",", items);
            }
            return String.valueOf(value);
        }
    }

    static Query query() {
        return new Query();
    }
}
