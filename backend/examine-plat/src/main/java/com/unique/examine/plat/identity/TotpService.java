package com.unique.examine.plat.identity;

import com.unique.examine.core.api.PlatformSecretResolverFacade;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

@Component
final class TotpService {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final PlatformSecretResolverFacade secrets;

    TotpService(PlatformSecretResolverFacade secrets) {
        this.secrets = secrets;
    }

    long verify(String secretRef, String code, Instant now) {
        if (code == null || !code.matches("^[0-9]{6}$")) return -1;
        byte[] encoded = null;
        byte[] key = null;
        try (var resolved = secrets.resolve(new PlatformSecretResolverFacade.SecretRequest(secretRef))
                .orElseThrow(() -> new MfaException("MFA_SECRET_UNRESOLVED"))) {
            encoded = resolved.copyBytes();
            key = decodeBase32(new String(encoded, StandardCharsets.US_ASCII));
            var current = Math.floorDiv(now.getEpochSecond(), 30);
            for (long step = current - 1; step <= current + 1; step++) {
                if (constantTime(code, generate(key, step))) return step;
            }
            return -1;
        } finally {
            if (encoded != null) Arrays.fill(encoded, (byte) 0);
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    private static String generate(byte[] key, long step) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            var digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            var offset = digest[digest.length - 1] & 0x0f;
            var binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", e);
        }
    }

    private static byte[] decodeBase32(String raw) {
        var value = raw.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (value.length() < 16 || !value.matches("^[A-Z2-7]+$")) {
            throw new MfaException("MFA_SECRET_FORMAT_INVALID");
        }
        var output = new byte[value.length() * 5 / 8];
        int buffer = 0, bits = 0, position = 0;
        for (int i = 0; i < value.length(); i++) {
            buffer = (buffer << 5) | ALPHABET.indexOf(value.charAt(i));
            bits += 5;
            if (bits >= 8) {
                output[position++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return output;
    }

    static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static boolean constantTime(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    static final class MfaException extends RuntimeException {
        final String code;
        MfaException(String code) { super(code); this.code = code; }
    }
}

