package com.unique.examine.ai;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AiSupport {
    private AiSupport() {
    }

    public static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public static BusinessException unavailable(String code, String message) {
        return new BusinessException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException("AI_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String redactedSummary(String kind, String value) {
        var normalized = value == null ? "" : value.strip();
        return "[" + kind + ":sha256=" + sha256(normalized).substring(0, 16)
                + ":chars=" + normalized.codePointCount(0, normalized.length()) + "]";
    }
}
