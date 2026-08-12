package com.unique.examine.openapi.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;

public final class OpenApiCanonicalRequest {
    private OpenApiCanonicalRequest() {
    }

    public static String canonical(
            String method,
            String rawPath,
            String rawQuery,
            byte[] body,
            String timestamp,
            String nonce,
            String idempotencyKey
    ) {
        return normalizedMethod(method) + "\n"
                + normalizedPath(rawPath) + "\n"
                + sortedEncodedQuery(rawQuery) + "\n"
                + sha256(body == null ? new byte[0] : body) + "\n"
                + required(timestamp, "timestamp") + "\n"
                + required(nonce, "nonce") + "\n"
                + required(idempotencyKey, "idempotencyKey");
    }

    public static String signature(byte[] secret, String canonical) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("OpenAPI signing secret is required");
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public static boolean verify(byte[] secret, String canonical, String candidate) {
        if (candidate == null || !candidate.matches("^[0-9a-f]{64}$")) {
            return false;
        }
        var expected = signature(secret, canonical).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.US_ASCII));
    }

    public static String normalizedPath(String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/") || rawPath.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("OpenAPI request path is invalid");
        }
        try {
            var path = URI.create("https://canonical.invalid" + rawPath)
                    .normalize()
                    .getRawPath();
            var value = new StringBuilder(path.length());
            for (int index = 0; index < path.length(); index++) {
                var current = path.charAt(index);
                if (current != '%') {
                    value.append(current);
                    continue;
                }
                if (index + 2 >= path.length()
                        || Character.digit(path.charAt(index + 1), 16) < 0
                        || Character.digit(path.charAt(index + 2), 16) < 0) {
                    throw new IllegalArgumentException("OpenAPI request path encoding is invalid");
                }
                value.append('%')
                        .append(Character.toUpperCase(path.charAt(index + 1)))
                        .append(Character.toUpperCase(path.charAt(index + 2)));
                index += 2;
            }
            return value.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OpenAPI request path is invalid", exception);
        }
    }

    public static String sortedEncodedQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        var values = new ArrayList<QueryValue>();
        for (var part : rawQuery.split("&", -1)) {
            var separator = part.indexOf('=');
            var rawName = separator < 0 ? part : part.substring(0, separator);
            var rawValue = separator < 0 ? "" : part.substring(separator + 1);
            values.add(new QueryValue(
                    encode(decode(rawName)),
                    encode(decode(rawValue))
            ));
        }
        values.sort(Comparator.comparing(QueryValue::name).thenComparing(QueryValue::value));
        return values.stream()
                .map(value -> value.name() + "=" + value.value())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalizedMethod(String method) {
        var value = required(method, "method").toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z]{3,12}$")) {
            throw new IllegalArgumentException("OpenAPI request method is invalid");
        }
        return value;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OpenAPI query encoding is invalid", exception);
        }
    }

    private static String encode(String value) {
        var output = new StringBuilder();
        for (var octet : value.getBytes(StandardCharsets.UTF_8)) {
            var unsigned = octet & 0xff;
            if (unsigned >= 'A' && unsigned <= 'Z'
                    || unsigned >= 'a' && unsigned <= 'z'
                    || unsigned >= '0' && unsigned <= '9'
                    || unsigned == '-' || unsigned == '.' || unsigned == '_' || unsigned == '~') {
                output.append((char) unsigned);
            } else {
                output.append('%');
                var encoded = Integer.toHexString(unsigned).toUpperCase(Locale.ROOT);
                if (encoded.length() == 1) {
                    output.append('0');
                }
                output.append(encoded);
            }
        }
        return output.toString();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("OpenAPI canonical " + field + " is invalid");
        }
        return value;
    }

    private record QueryValue(String name, String value) {
    }
}
