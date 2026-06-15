package com.unique.examine.app.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 开放 API 请求签名 v1：
 * <pre>
 * bodyHash = hex(SHA256(body))
 * canonical = METHOD + "\n" + pathWithQuery + "\n" + timestamp + "\n" + bodyHash
 * signature = Base64(HMAC-SHA256(secret, canonical))
 * </pre>
 */
public final class OpenApiSignatureSupport {

    public static final String VERSION = "v1";
    public static final long MAX_CLOCK_SKEW_SECONDS = 300L;

    private OpenApiSignatureSupport() {
    }

    public static String canonicalPath(String requestUri, String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return requestUri == null ? "" : requestUri;
        }
        return (requestUri == null ? "" : requestUri) + "?" + queryString;
    }

    public static String sha256Hex(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body == null ? new byte[0] : body);
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String buildCanonical(String method, String pathWithQuery, String timestamp, byte[] body) {
        String m = method == null ? "" : method.trim().toUpperCase();
        String p = pathWithQuery == null ? "" : pathWithQuery;
        String ts = timestamp == null ? "" : timestamp.trim();
        return m + "\n" + p + "\n" + ts + "\n" + sha256Hex(body);
    }

    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    public static boolean verify(String secret, String canonical, String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        String expected = sign(secret, canonical);
        return constantTimeEquals(expected, signatureBase64.trim());
    }

    public static boolean isTimestampValid(String timestampHeader, long nowEpochSeconds) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        // 支持毫秒时间戳
        if (timestampHeader.trim().length() >= 13) {
            ts = ts / 1000L;
        }
        return Math.abs(nowEpochSeconds - ts) <= MAX_CLOCK_SKEW_SECONDS;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
