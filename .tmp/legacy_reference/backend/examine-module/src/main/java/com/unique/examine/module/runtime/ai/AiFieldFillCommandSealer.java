package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiFieldFillFacade;
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
import java.util.Set;

/** Proposal- and actor-bound AES-GCM envelope for one AI_FILL command. */
@Component
public class AiFieldFillCommandSealer {
    private static final byte ENVELOPE_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Set<Integer> AES_KEY_LENGTHS = Set.of(16, 24, 32);

    private final SensitiveKeyProvider keys;
    private final SecureRandom random;

    @Autowired
    public AiFieldFillCommandSealer(SensitiveKeyProvider keys) {
        this(keys, new SecureRandom());
    }

    AiFieldFillCommandSealer(SensitiveKeyProvider keys, SecureRandom random) {
        this.keys = keys;
        this.random = random;
    }

    public AiFieldFillFacade.SealedCommand seal(String canonicalCommand, Binding binding) {
        var ring = keyRing();
        var version = ring.activeEncryptionVersion();
        var key = requiredKey(ring.encryptionKeys(), version);
        try {
            var nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(binding.aad());
            var encrypted = cipher.doFinal(canonicalCommand.getBytes(StandardCharsets.UTF_8));
            var envelope = ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put(ENVELOPE_VERSION).put(nonce).put(encrypted).array();
            return new AiFieldFillFacade.SealedCommand(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(envelope),
                    version, sha256(canonicalCommand));
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    public String open(AiFieldFillFacade.SealedCommand command, Binding binding) {
        final byte[] envelope;
        try {
            envelope = Base64.getUrlDecoder().decode(command.ciphertext());
        } catch (IllegalArgumentException failure) {
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
        } catch (AEADBadTagException failure) {
            throw invalid();
        } catch (GeneralSecurityException failure) {
            throw invalid();
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private SensitiveKeyProvider.KeyRing keyRing() {
        return keys.current().orElseThrow(AiFieldFillCommandSealer::keyUnavailable);
    }

    private static byte[] requiredKey(Map<String, byte[]> keys, String version) {
        var key = version == null ? null : keys.get(version);
        if (key == null || !AES_KEY_LENGTHS.contains(key.length)) throw keyUnavailable();
        return key;
    }

    private static BusinessException keyUnavailable() {
        return new BusinessException(
                "SENSITIVE_KEY_UNAVAILABLE",
                "AI_FILL command key service is unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException invalid() {
        return new BusinessException(
                "AI_FILL_COMMAND_INVALID",
                "AI_FILL owner command authentication failed",
                HttpStatus.CONFLICT);
    }

    public record Binding(
            long systemId,
            long tenantId,
            long memberId,
            String proposalId,
            String moduleCode,
            String recordId,
            String fieldCode
    ) {
        byte[] aad() {
            var output = new java.io.ByteArrayOutputStream();
            append(output, "examine2:ai-fill:v1");
            append(output, Long.toString(systemId));
            append(output, Long.toString(tenantId));
            append(output, Long.toString(memberId));
            append(output, proposalId);
            append(output, moduleCode);
            append(output, recordId);
            append(output, fieldCode);
            return output.toByteArray();
        }

        private static void append(java.io.ByteArrayOutputStream output, String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            output.writeBytes(bytes);
        }
    }
}
