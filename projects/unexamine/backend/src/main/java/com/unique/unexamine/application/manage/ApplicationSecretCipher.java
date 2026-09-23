package com.unique.unexamine.application.manage;

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
public class ApplicationSecretCipher {
    private static final String PREFIX = "local-aesgcm:v1:";
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApplicationSecretCipher(@Value("${app.security.application-secret-master-key}") String masterKey) {
        if (masterKey == null || masterKey.length() < 32) {
            throw new IllegalStateException("APP_APPLICATION_SECRET_MASTER_KEY must contain at least 32 characters");
        }
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Application secret cipher is unavailable", exception);
        }
    }

    public String encrypt(String secret, Long applicationId, int credentialVersion) {
        return encrypt(secret, applicationId + ":" + credentialVersion);
    }

    /** 外部平台密钥使用包含连接、用途和版本的绑定值，防止不同用途的密文被调换。 */
    public String encrypt(String secret, String binding) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(binding));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Application secret could not be encrypted", exception);
        }
    }

    public String decrypt(String reference, Long applicationId, int credentialVersion) {
        return decrypt(reference, applicationId + ":" + credentialVersion);
    }

    /** 明文只在本次协议处理调用栈中存在，调用方不得记录或返回。 */
    public String decrypt(String reference, String binding) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new IllegalStateException("Application signing secret reference is unavailable");
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
            cipher.updateAAD(aad(binding));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Application signing secret reference is invalid", exception);
        }
    }

    public String displayReference(Long applicationId, int credentialVersion) {
        return "app-secret/" + applicationId + "/v" + credentialVersion;
    }

    private byte[] aad(String binding) {
        if (binding == null || binding.isBlank()) {
            throw new IllegalArgumentException("Application secret binding is required");
        }
        return binding.getBytes(StandardCharsets.UTF_8);
    }
}
