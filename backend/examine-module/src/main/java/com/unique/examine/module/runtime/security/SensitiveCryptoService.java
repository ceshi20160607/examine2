package com.unique.examine.module.runtime.security;

import com.unique.examine.core.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Component
public final class SensitiveCryptoService {
    private static final byte ENVELOPE_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SensitiveKeyProvider keyProvider;
    private final SecureRandom secureRandom;

    @Autowired
    public SensitiveCryptoService(SensitiveKeyProvider keyProvider) {
        this(keyProvider, new SecureRandom());
    }

    SensitiveCryptoService(SensitiveKeyProvider keyProvider, SecureRandom secureRandom) {
        this.keyProvider = keyProvider;
        this.secureRandom = secureRandom;
    }

    public boolean available() {
        return keyProvider.available();
    }

    public EncryptedValue encrypt(String plaintext, ValueContext context) {
        var keyRing = requireKeyRing();
        var encryptionVersion = keyRing.activeEncryptionVersion();
        var hashVersion = keyRing.activeHashVersion();
        var encryptionKey = requiredKey(keyRing.encryptionKeys(), encryptionVersion);
        var hashKey = requiredKey(keyRing.hashKeys(), hashVersion);
        try {
            var nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.aad());
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var envelope = ByteBuffer.allocate(1 + nonce.length + ciphertext.length)
                    .put(ENVELOPE_VERSION).put(nonce).put(ciphertext).array();
            return new EncryptedValue(envelope, encryptionVersion,
                    hmacHex(hashKey, context.blindIndexContext().aad(), plaintext), hashVersion);
        } catch (GeneralSecurityException exception) {
            throw invalidSensitiveValue();
        }
    }

    public String decrypt(byte[] envelope, String encryptionVersion, ValueContext context) {
        var keyRing = requireKeyRing();
        var key = requiredKey(keyRing.encryptionKeys(), encryptionVersion);
        if (envelope == null || envelope.length < 1 + NONCE_BYTES + 16 || envelope[0] != ENVELOPE_VERSION) {
            throw invalidSensitiveValue();
        }
        try {
            var nonce = java.util.Arrays.copyOfRange(envelope, 1, 1 + NONCE_BYTES);
            var ciphertext = java.util.Arrays.copyOfRange(envelope, 1 + NONCE_BYTES, envelope.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.aad());
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw invalidSensitiveValue();
        } catch (GeneralSecurityException exception) {
            throw invalidSensitiveValue();
        }
    }

    public String equalityHash(String plaintext, String hashVersion, ValueContext context) {
        return equalityHash(plaintext, hashVersion, context.blindIndexContext());
    }

    public String equalityHash(String plaintext, String hashVersion, BlindIndexContext context) {
        var keyRing = requireKeyRing();
        return hmacHex(requiredKey(keyRing.hashKeys(), hashVersion), context.aad(), plaintext);
    }

    public List<EqualityHash> equalityHashes(String plaintext, BlindIndexContext context) {
        var keyRing = requireKeyRing();
        return keyRing.queryHashVersions().stream()
                .map(version -> new EqualityHash(version,
                        hmacHex(requiredKey(keyRing.hashKeys(), version), context.aad(), plaintext)))
                .toList();
    }

    private SensitiveKeyProvider.KeyRing requireKeyRing() {
        return keyProvider.current().orElseThrow(SensitiveCryptoService::keyUnavailable);
    }

    private static byte[] requiredKey(java.util.Map<String, byte[]> keys, String version) {
        var key = version == null ? null : keys.get(version);
        if (key == null) {
            throw keyUnavailable();
        }
        return key;
    }

    private static String hmacHex(byte[] key, byte[] domain, String plaintext) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(domain);
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw invalidSensitiveValue();
        }
    }

    private static BusinessException keyUnavailable() {
        return new BusinessException("SENSITIVE_KEY_UNAVAILABLE",
                "Sensitive value key service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException invalidSensitiveValue() {
        return new BusinessException("SENSITIVE_VALUE_INVALID",
                "Sensitive value authentication failed", HttpStatus.CONFLICT);
    }

    public record ValueContext(
            long systemId,
            long tenantId,
            long logicalModuleId,
            long logicalFieldId,
            long recordId,
            long fieldSnapshotId,
            int ordinal,
            String fieldType
    ) {
        byte[] aad() {
            return (systemId + "|" + tenantId + "|" + logicalModuleId + "|" + logicalFieldId + "|"
                    + recordId + "|" + fieldSnapshotId + "|" + ordinal + "|" + fieldType)
                    .getBytes(StandardCharsets.UTF_8);
        }

        public BlindIndexContext blindIndexContext() {
            return new BlindIndexContext(systemId, tenantId, logicalModuleId, logicalFieldId, fieldType);
        }
    }

    public record BlindIndexContext(
            long systemId,
            long tenantId,
            long logicalModuleId,
            long logicalFieldId,
            String fieldType
    ) {
        byte[] aad() {
            return ("examine2:blind-index:v1|" + systemId + "|" + tenantId + "|" + logicalModuleId + "|"
                    + logicalFieldId + "|" + fieldType).getBytes(StandardCharsets.UTF_8);
        }
    }

    public record EqualityHash(String hashKeyVersion, String valueHash) { }

    public record EncryptedValue(
            byte[] envelope,
            String encryptionKeyVersion,
            String valueHash,
            String hashKeyVersion
    ) {
        public EncryptedValue {
            envelope = envelope.clone();
        }

        @Override
        public byte[] envelope() {
            return envelope.clone();
        }
    }
}
