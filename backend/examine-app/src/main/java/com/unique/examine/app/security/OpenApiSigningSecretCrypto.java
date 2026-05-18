package com.unique.examine.app.security;

import com.unique.examine.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 开放 API 签名验签用：将 SK 以 AES-256-GCM 加密后存入 {@code sign_secret_enc}（不落明文 SK）。
 */
@Component
public class OpenApiSigningSecretCrypto {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final byte[] aesKey;

    public OpenApiSigningSecretCrypto(
            @Value("${examine.openapi.signing-master-key:change-me-openapi-signing-key-32bytes}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("examine.openapi.signing-master-key 未配置");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            this.aesKey = sha.digest(masterKey.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化签名密钥失败", e);
        }
    }

    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw new BusinessException(400, "secret 不能为空");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(ct, 0, packed, nonce.length, ct.length);
            return "v1:" + Base64.getEncoder().encodeToString(packed);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(500, "加密 SK 失败");
        }
    }

    public String decrypt(String enc) {
        if (enc == null || enc.isBlank()) {
            return null;
        }
        if (!enc.startsWith("v1:")) {
            throw new BusinessException(500, "sign_secret_enc 版本不支持");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(enc.substring(3));
            if (packed.length <= NONCE_BYTES) {
                throw new BusinessException(500, "sign_secret_enc 损坏");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(packed, 0, nonce, 0, NONCE_BYTES);
            byte[] ct = new byte[packed.length - NONCE_BYTES];
            System.arraycopy(packed, NONCE_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(500, "解密 SK 失败");
        }
    }
}
