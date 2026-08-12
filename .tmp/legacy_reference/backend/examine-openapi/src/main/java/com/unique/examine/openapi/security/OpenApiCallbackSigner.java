package com.unique.examine.openapi.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class OpenApiCallbackSigner {
    private OpenApiCallbackSigner() { }

    public static String payloadHash(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public static String sign(byte[] secret, String timestamp, long deliveryId,
                              String eventType, String payloadHash) {
        if (secret == null || secret.length < 32 || timestamp == null || eventType == null
                || payloadHash == null || deliveryId <= 0) {
            throw new IllegalArgumentException("OpenAPI callback signing input is invalid");
        }
        var canonical = timestamp + "." + deliveryId + "." + eventType + "." + payloadHash;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
        }
    }
}
