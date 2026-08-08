package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** AES-GCM envelope for one module-generated draft proposal. */
@Component
public class AiModuleGeneratedDraftCommandSealer {
    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final PlatformSecretResolverFacade secrets;
    private final String secretReference;
    private final SecureRandom random;

    @Autowired
    public AiModuleGeneratedDraftCommandSealer(
            PlatformSecretResolverFacade secrets,
            @Value("${examine.module.ai-generated-draft.command-secret-ref:"
                    + "env://EXAMINE_MODULE_AI_GENERATED_DRAFT_COMMAND_KEY}")
            String secretReference) {
        this(secrets, secretReference, new SecureRandom());
    }

    AiModuleGeneratedDraftCommandSealer(
            PlatformSecretResolverFacade secrets,
            String secretReference,
            SecureRandom random) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.secretReference = Objects.requireNonNull(
                secretReference, "secretReference");
        this.random = Objects.requireNonNull(random, "random");
    }

    AiModuleGeneratedDraftFacade.SealedCommand seal(
            String plaintext, Binding binding) {
        var material = material();
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
            var envelope = ByteBuffer.allocate(
                            1 + nonce.length + encrypted.length)
                    .put(VERSION).put(nonce).put(encrypted).array();
            return new AiModuleGeneratedDraftFacade.SealedCommand(
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
            AiModuleGeneratedDraftFacade.SealedCommand command,
            Binding binding) {
        var material = material();
        try {
            if (!equal(material.version(), command.encryptionKeyVersion())) {
                throw invalid();
            }
            final byte[] envelope;
            try {
                envelope = Base64.getUrlDecoder().decode(command.ciphertext());
            } catch (IllegalArgumentException failure) {
                throw invalid();
            }
            if (envelope.length < 1 + NONCE_BYTES + 16
                    || envelope[0] != VERSION) {
                throw invalid();
            }
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

    private KeyMaterial material() {
        var resolved = secrets.resolve(
                        new PlatformSecretResolverFacade.SecretRequest(
                                secretReference))
                .orElseThrow(AiModuleGeneratedDraftCommandSealer::unavailable);
        try (resolved) {
            var bytes = resolved.copyBytes();
            try {
                if (bytes.length < 16) throw unavailable();
                var key = digest(bytes);
                return new KeyMaterial(
                        key, HexFormat.of().formatHex(key).substring(0, 16));
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    static String sha256(String value) {
        return HexFormat.of().formatHex(digest(
                value.getBytes(StandardCharsets.UTF_8)));
    }

    static boolean equal(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                "AI_MODULE_DRAFT_COMMAND_KEY_UNAVAILABLE",
                "The module-generated draft command key is unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    static BusinessException invalid() {
        return new BusinessException(
                "AI_MODULE_DRAFT_COMMAND_INVALID",
                "The module-generated draft owner command is invalid",
                HttpStatus.CONFLICT);
    }

    record Binding(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            String proposalId,
            String sessionId,
            String turnId,
            AiModuleGeneratedDraftFacade.Operation operation
    ) {
        byte[] aad() {
            var result = new ByteArrayOutputStream();
            append(result, "examine2:module-ai-generated-draft-owner:v1");
            append(result, Long.toString(accountId));
            append(result, Long.toString(systemId));
            append(result, Long.toString(tenantId));
            append(result, Long.toString(memberId));
            append(result, proposalId);
            append(result, sessionId);
            append(result, turnId);
            append(result, operation.name());
            return result.toByteArray();
        }

        private static void append(
                ByteArrayOutputStream target, String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            target.writeBytes(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(bytes.length).array());
            target.writeBytes(bytes);
        }
    }

    private record KeyMaterial(byte[] key, String version) {
        private void clear() {
            Arrays.fill(key, (byte) 0);
        }
    }
}
