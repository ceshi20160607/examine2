package com.unique.unexamine.application.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 飞书请求地址模式的签名与加密载荷算法，格式与官方服务端 SDK 保持一致。 */
final class FeishuCallbackCodec {
    private static final SecureRandom RANDOM = new SecureRandom();

    private FeishuCallbackCodec() {
    }

    static void verifySignature(
            String encryptKey, String timestamp, String nonce, String rawBody, String suppliedSignature) {
        if (timestamp == null || nonce == null || suppliedSignature == null) {
            throw unauthorized("FEISHU_CALLBACK_SIGNATURE_REQUIRED", "飞书加密事件缺少签名请求头");
        }
        String expected = sha256(timestamp + nonce + encryptKey + rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                suppliedSignature.getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized("FEISHU_CALLBACK_SIGNATURE_INVALID", "飞书事件签名无效");
        }
    }

    static String decrypt(String encryptKey, String encrypted) {
        try {
            byte[] source = Base64.getDecoder().decode(encrypted);
            if (source.length <= 16) throw new GeneralSecurityException("ciphertext too short");
            byte[] iv = java.util.Arrays.copyOfRange(source, 0, 16);
            byte[] ciphertext = java.util.Arrays.copyOfRange(source, 16, source.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key(encryptKey), new IvParameterSpec(iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw unauthorized("FEISHU_CALLBACK_DECRYPT_FAILED", "飞书事件加密载荷无法解密");
        }
    }

    /** 仅用于本地协议诊断和独立验收向量，不承担外部发送。 */
    static String encrypt(String encryptKey, String plaintext) {
        try {
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key(encryptKey), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-256-CBC unavailable", exception);
        }
    }

    static String signature(String encryptKey, String timestamp, String nonce, String rawBody) {
        return sha256(timestamp + nonce + encryptKey + rawBody);
    }

    private static SecretKeySpec key(String encryptKey) throws GeneralSecurityException {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(encryptKey.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static DomainException unauthorized(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNAUTHORIZED);
    }
}
