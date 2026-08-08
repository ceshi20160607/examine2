package com.unique.examine.plat.lifecycle;

import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Authenticated envelope for tenant backup rows; key material remains behind a SecretRef. */
@Component
public class LifecyclePayloadSealer {
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final PlatformSecretResolverFacade secrets;
    private final String defaultKeyReference;
    private final SecureRandom random;

    @Autowired
    public LifecyclePayloadSealer(
            PlatformSecretResolverFacade secrets,
            @Value("${examine.platform.lifecycle.backup-secret-ref:"
                    + "env://EXAMINE_PLATFORM_LIFECYCLE_BACKUP_KEY}") String defaultKeyReference) {
        this(secrets, defaultKeyReference, new SecureRandom());
    }

    LifecyclePayloadSealer(
            PlatformSecretResolverFacade secrets, String defaultKeyReference, SecureRandom random) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.defaultKeyReference = new PlatformSecretResolverFacade.SecretRequest(defaultKeyReference).reference();
        this.random = Objects.requireNonNull(random, "random");
    }

    SealedPayload seal(byte[] plaintext, Binding binding) {
        var material = material(defaultKeyReference);
        try {
            var nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(material.key(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            var encrypted = cipher.doFinal(plaintext);
            var envelope = ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put(FORMAT_VERSION).put(nonce).put(encrypted).array();
            return new SealedPayload(envelope, defaultKeyReference, material.version(),
                    sha256(plaintext), sha256(envelope));
        } catch (GeneralSecurityException failure) {
            throw invalid();
        } finally {
            material.clear();
        }
    }

    byte[] open(SealedPayload payload, Binding binding) {
        if (!sha256(payload.ciphertext()).equals(payload.ciphertextSha256())) throw invalid();
        var material = material(payload.keyReference());
        try {
            if (!constantTime(material.version(), payload.keyVersion())) throw invalid();
            var envelope = payload.ciphertext();
            if (envelope.length < 1 + NONCE_BYTES + 16 || envelope[0] != FORMAT_VERSION) throw invalid();
            var nonce = Arrays.copyOfRange(envelope, 1, 1 + NONCE_BYTES);
            var encrypted = Arrays.copyOfRange(envelope, 1 + NONCE_BYTES, envelope.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(material.key(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            var plaintext = cipher.doFinal(encrypted);
            if (!sha256(plaintext).equals(payload.plaintextSha256())) {
                Arrays.fill(plaintext, (byte) 0);
                throw invalid();
            }
            return plaintext;
        } catch (AEADBadTagException failure) {
            throw invalid();
        } catch (GeneralSecurityException failure) {
            throw invalid();
        } finally {
            material.clear();
        }
    }

    private KeyMaterial material(String reference) {
        var request = new PlatformSecretResolverFacade.SecretRequest(reference);
        var resolved = secrets.resolve(request).orElseThrow(LifecyclePayloadSealer::unavailable);
        try (resolved) {
            var source = resolved.copyBytes();
            try {
                if (source.length < 16) throw unavailable();
                var key = digest(source);
                return new KeyMaterial(key, HexFormat.of().formatHex(key).substring(0, 16));
            } finally {
                Arrays.fill(source, (byte) 0);
            }
        }
    }

    static String sha256(byte[] value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static boolean constantTime(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static BusinessException unavailable() {
        return new BusinessException("TENANT_BACKUP_KEY_UNAVAILABLE",
                "tenant backup encryption key is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException invalid() {
        return new BusinessException("TENANT_BACKUP_ENVELOPE_INVALID",
                "tenant backup envelope cannot be authenticated", HttpStatus.CONFLICT);
    }

    record Binding(long systemId, long tenantId, long operationId, String schemaFingerprint) {
        byte[] aad() {
            var target = new ByteArrayOutputStream();
            append(target, "examine2:tenant-lifecycle-backup:v1");
            append(target, Long.toString(systemId));
            append(target, Long.toString(tenantId));
            append(target, Long.toString(operationId));
            append(target, schemaFingerprint);
            return target.toByteArray();
        }

        private static void append(ByteArrayOutputStream target, String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            target.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            target.writeBytes(bytes);
        }
    }

    record SealedPayload(
            byte[] ciphertext,
            String keyReference,
            String keyVersion,
            String plaintextSha256,
            String ciphertextSha256) { }

    private record KeyMaterial(byte[] key, String version) {
        void clear() { Arrays.fill(key, (byte) 0); }
    }
}
