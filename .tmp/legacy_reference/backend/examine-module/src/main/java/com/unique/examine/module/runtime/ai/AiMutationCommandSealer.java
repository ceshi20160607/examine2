package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** AES-GCM envelope dedicated to confirmation-bound AI owner commands. */
@Component
public class AiMutationCommandSealer {
    private static final byte ENVELOPE_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final java.util.Set<Integer> AES_KEY_LENGTHS = java.util.Set.of(16, 24, 32);

    private final SensitiveKeyProvider keys;
    private final SecureRandom random;

    @Autowired
    public AiMutationCommandSealer(SensitiveKeyProvider keys) {
        this(keys, new SecureRandom());
    }

    AiMutationCommandSealer(SensitiveKeyProvider keys, SecureRandom random) {
        this.keys = java.util.Objects.requireNonNull(keys, "keys");
        this.random = java.util.Objects.requireNonNull(random, "random");
    }

    public AiRecordMutationFacade.SealedCommand seal(
            String canonicalCommand,
            String sealedPayload,
            Binding binding
    ) {
        var keyRing = keyRing();
        var version = keyRing.activeEncryptionVersion();
        var key = requiredKey(keyRing.encryptionKeys(), version);
        try {
            var nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            var encrypted = cipher.doFinal(sealedPayload.getBytes(StandardCharsets.UTF_8));
            var envelope = ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put(ENVELOPE_VERSION).put(nonce).put(encrypted).array();
            return new AiRecordMutationFacade.SealedCommand(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(envelope),
                    version,
                    sha256(canonicalCommand));
        } catch (GeneralSecurityException exception) {
            throw invalid();
        }
    }

    public String open(
            AiRecordMutationFacade.SealedCommand command,
            Binding binding
    ) {
        final byte[] envelope;
        try {
            envelope = Base64.getUrlDecoder().decode(command.ciphertext());
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (envelope.length < 1 + NONCE_BYTES + 16 || envelope[0] != ENVELOPE_VERSION) {
            throw invalid();
        }
        var key = requiredKey(keyRing().encryptionKeys(), command.encryptionKeyVersion());
        try {
            var nonce = java.util.Arrays.copyOfRange(envelope, 1, 1 + NONCE_BYTES);
            var encrypted = java.util.Arrays.copyOfRange(envelope, 1 + NONCE_BYTES, envelope.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw invalid();
        } catch (GeneralSecurityException exception) {
            throw invalid();
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private SensitiveKeyProvider.KeyRing keyRing() {
        return keys.current().orElseThrow(AiMutationCommandSealer::keyUnavailable);
    }

    private static byte[] requiredKey(Map<String, byte[]> keys, String version) {
        var key = version == null ? null : keys.get(version);
        if (key == null || !AES_KEY_LENGTHS.contains(key.length)) {
            throw keyUnavailable();
        }
        return key;
    }

    private static BusinessException keyUnavailable() {
        return new BusinessException(
                "SENSITIVE_KEY_UNAVAILABLE",
                "AI mutation command key service is unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException invalid() {
        return new BusinessException(
                "AI_MUTATION_COMMAND_INVALID",
                "AI mutation command authentication failed",
                HttpStatus.CONFLICT);
    }

    public record Binding(
            long systemId,
            long tenantId,
            long memberId,
            String confirmationId,
            String moduleCode,
            AiRecordMutationFacade.Operation operation
    ) {
        byte[] aad() {
            var buffer = new java.io.ByteArrayOutputStream();
            append(buffer, "examine2:ai-record-mutation:v1");
            append(buffer, Long.toString(systemId));
            append(buffer, Long.toString(tenantId));
            append(buffer, Long.toString(memberId));
            append(buffer, confirmationId);
            append(buffer, moduleCode);
            append(buffer, operation.name());
            return buffer.toByteArray();
        }

        private static void append(java.io.ByteArrayOutputStream output, String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            output.writeBytes(bytes);
        }
    }
}
