package com.unique.unexamine.shared.manage.security;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 业务敏感值加密；与应用签名密钥分域，绑定真实字段或原动作，禁止跨数据复制密文。 */
@Component
public class RuntimeSecretCipher {
    private static final String PREFIX = "runtime-secret:v1:";
    private static final int IV_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public RuntimeSecretCipher(@Value("${app.security.application-secret-master-key}") String masterKey) {
        if (masterKey == null || masterKey.length() < 32) {
            throw new IllegalStateException("业务敏感值加密主密钥未配置");
        }
        try {
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(("unexamine-runtime-secret-v1:" + masterKey).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("业务敏感值加密不可用", exception);
        }
    }

    /** 每次保存使用独立随机向量，同值不会留下可比较的密文。 */
    public String encrypt(String value, String binding) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("业务敏感值无法保存", exception);
        }
    }

    /** 错误主密钥、跨作用域密文和旧明文都明确阻断，不降级为明文读取。 */
    public String decrypt(String value, String binding) {
        if (value == null || !value.startsWith(PREFIX)) throw unavailable();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            if (bytes.length < IV_LENGTH + 16) throw unavailable();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Arrays.copyOf(bytes, IV_LENGTH)));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Arrays.copyOfRange(bytes, IV_LENGTH, bytes.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private DomainException unavailable() {
        return new DomainException("SECRET_VALUE_UNAVAILABLE", "敏感值无法读取，请检查密钥或数据归属", HttpStatus.CONFLICT);
    }
}
