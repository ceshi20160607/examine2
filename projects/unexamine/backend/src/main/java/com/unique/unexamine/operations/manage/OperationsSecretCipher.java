package com.unique.unexamine.operations.manage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class OperationsSecretCipher {
    private static final String PREFIX = "ops-aesgcm:v1:";
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public OperationsSecretCipher(@Value("${app.security.application-secret-master-key}") String masterKey) {
        if (masterKey == null || masterKey.length() < 32) {
            throw new IllegalStateException("APP_APPLICATION_SECRET_MASTER_KEY must contain at least 32 characters");
        }
        try {
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Operations secret cipher is unavailable", exception);
        }
    }

    public String encrypt(String value, long secretRefId, String version) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(secretRefId, version));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Operations secret could not be encrypted", exception);
        }
    }

    public String decrypt(String reference, long secretRefId, String version) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new IllegalStateException("Operations secret material reference is unavailable");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(reference.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) throw new GeneralSecurityException("encrypted payload is incomplete");
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(secretRefId, version));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Operations secret material reference is invalid", exception);
        }
    }

    private byte[] aad(long secretRefId, String version) {
        return (secretRefId + ":" + version).getBytes(StandardCharsets.UTF_8);
    }
}
