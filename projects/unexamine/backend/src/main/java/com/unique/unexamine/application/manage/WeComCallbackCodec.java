package com.unique.unexamine.application.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** 企业微信回调协议算法；只负责官方签名与AES载荷，不处理业务路由。 */
public final class WeComCallbackCodec {
    private static final int BLOCK_SIZE = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WeComCallbackCodec() {
    }

    public static String signature(String token, String timestamp, String nonce, String encrypted) {
        try {
            List<String> values = new ArrayList<>(List.of(token, timestamp, nonce, encrypted));
            values.sort(String::compareTo);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-1 unavailable", exception);
        }
    }

    public static void verify(String token, String timestamp, String nonce, String encrypted, String supplied) {
        if (supplied == null || !MessageDigest.isEqual(
                signature(token, timestamp, nonce, encrypted).getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw invalid("PROVIDER_CALLBACK_SIGNATURE_INVALID", "企业微信回调签名校验失败", HttpStatus.UNAUTHORIZED);
        }
    }

    public static Decoded decrypt(String encodingAesKey, String encrypted) {
        try {
            byte[] key = key(encodingAesKey);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            byte[] plain = unpad(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
            if (plain.length < 20) throw new GeneralSecurityException("payload is incomplete");
            int length = ByteBuffer.wrap(plain, 16, 4).getInt();
            if (length < 0 || 20 + length > plain.length) throw new GeneralSecurityException("payload length is invalid");
            String message = new String(plain, 20, length, StandardCharsets.UTF_8);
            String receiveId = new String(plain, 20 + length, plain.length - 20 - length, StandardCharsets.UTF_8);
            return new Decoded(message, receiveId);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw invalid("PROVIDER_CALLBACK_DECRYPT_FAILED", "企业微信回调载荷无法解密", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** 仅用于本地协议诊断和验收向量，不用于伪造外部联通证明。 */
    public static String encrypt(String encodingAesKey, String message, String receiveId) {
        try {
            byte[] key = key(encodingAesKey);
            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            byte[] receiver = receiveId.getBytes(StandardCharsets.UTF_8);
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            ByteBuffer buffer = ByteBuffer.allocate(20 + body.length + receiver.length);
            buffer.put(random).putInt(body.length).put(body).put(receiver);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            return Base64.getEncoder().encodeToString(cipher.doFinal(pad(buffer.array())));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw invalid("PROVIDER_ENCODING_KEY_INVALID", "企业微信EncodingAESKey无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static byte[] key(String value) {
        if (value == null || value.length() != 43) throw new IllegalArgumentException("key length");
        byte[] key = Base64.getDecoder().decode(value + "=");
        if (key.length != 32) throw new IllegalArgumentException("key bytes");
        return key;
    }

    private static byte[] pad(byte[] input) {
        int padding = BLOCK_SIZE - input.length % BLOCK_SIZE;
        byte[] output = java.util.Arrays.copyOf(input, input.length + padding);
        java.util.Arrays.fill(output, input.length, output.length, (byte) padding);
        return output;
    }

    private static byte[] unpad(byte[] input) throws GeneralSecurityException {
        if (input.length == 0) throw new GeneralSecurityException("empty payload");
        int padding = input[input.length - 1] & 0xff;
        if (padding < 1 || padding > BLOCK_SIZE || padding > input.length) {
            throw new GeneralSecurityException("padding is invalid");
        }
        for (int i = input.length - padding; i < input.length; i++) {
            if ((input[i] & 0xff) != padding) throw new GeneralSecurityException("padding differs");
        }
        return java.util.Arrays.copyOf(input, input.length - padding);
    }

    private static DomainException invalid(String code, String message, HttpStatus status) {
        return new DomainException(code, message, status);
    }

    public record Decoded(String message, String receiveId) {
    }
}
