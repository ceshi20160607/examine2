package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Actor- and draft-bound AES-GCM envelope for configuration field commands. */
@Component
public class AiConfigurationFieldCommandSealer {
    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Set<Integer> AES_KEY_LENGTHS = Set.of(16, 24, 32);

    private final SensitiveKeyProvider keys;
    private final SecureRandom random;

    @Autowired
    public AiConfigurationFieldCommandSealer(SensitiveKeyProvider keys) {
        this(keys, new SecureRandom());
    }

    AiConfigurationFieldCommandSealer(
            SensitiveKeyProvider keys, SecureRandom random) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.random = Objects.requireNonNull(random, "random");
    }

    AiConfigurationFieldFacade.SealedCommand seal(
            String plaintext, Binding binding) {
        var material = material(null);
        try {
            var nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(material.key(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            var encrypted = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            var envelope = ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put(VERSION).put(nonce).put(encrypted).array();
            return new AiConfigurationFieldFacade.SealedCommand(
                    Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(envelope),
                    material.version(), sha256(plaintext));
        } catch (GeneralSecurityException failure) {
            throw invalid();
        } finally {
            material.clear();
        }
    }

    String open(
            AiConfigurationFieldFacade.SealedCommand command,
            Binding binding) {
        var material = material(command.encryptionKeyVersion());
        try {
            final byte[] envelope;
            try {
                envelope = Base64.getUrlDecoder().decode(command.ciphertext());
            } catch (IllegalArgumentException failure) {
                throw invalid();
            }
            if (envelope.length < 1 + NONCE_BYTES + 16
                    || envelope[0] != VERSION) throw invalid();
            var nonce = Arrays.copyOfRange(envelope, 1, 1 + NONCE_BYTES);
            var encrypted = Arrays.copyOfRange(
                    envelope, 1 + NONCE_BYTES, envelope.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(material.key(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException failure) {
            throw invalid();
        } catch (GeneralSecurityException failure) {
            throw invalid();
        } finally {
            material.clear();
        }
    }

    private KeyMaterial material(String requestedVersion) {
        var ring = keys.current().orElseThrow(
                AiConfigurationFieldCommandSealer::keyUnavailable);
        var version = requestedVersion == null
                ? ring.activeEncryptionVersion() : requestedVersion;
        var values = ring.encryptionKeys();
        var selected = values.get(version);
        if (selected == null || !AES_KEY_LENGTHS.contains(selected.length)) {
            values.values().forEach(value -> Arrays.fill(value, (byte) 0));
            throw keyUnavailable();
        }
        var key = selected.clone();
        values.values().forEach(value -> Arrays.fill(value, (byte) 0));
        return new KeyMaterial(version, key);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    static boolean equal(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static BusinessException keyUnavailable() {
        return new BusinessException(
                "AI_CONFIG_FIELD_COMMAND_KEY_UNAVAILABLE",
                "The configuration field command key is unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException invalid() {
        return AiConfigurationFieldCommandCodec.invalid();
    }

    record Binding(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            String proposalId,
            long configRootId,
            long moduleId,
            String moduleCode,
            long expectedDraftRevision
    ) {
        byte[] aad() {
            var output = new ByteArrayOutputStream();
            append(output, "examine2:ai-config-field:v1");
            append(output, Long.toString(accountId));
            append(output, Long.toString(systemId));
            append(output, Long.toString(tenantId));
            append(output, Long.toString(memberId));
            append(output, proposalId);
            append(output, Long.toString(configRootId));
            append(output, Long.toString(moduleId));
            append(output, moduleCode);
            append(output, Long.toString(expectedDraftRevision));
            return output.toByteArray();
        }

        private static void append(
                ByteArrayOutputStream output, String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeBytes(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(bytes.length).array());
            output.writeBytes(bytes);
        }
    }

    private record KeyMaterial(String version, byte[] key) {
        private void clear() {
            Arrays.fill(key, (byte) 0);
        }
    }
}
