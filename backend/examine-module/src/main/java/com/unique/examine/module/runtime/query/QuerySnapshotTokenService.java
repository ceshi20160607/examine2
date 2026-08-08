package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

@Component
public class QuerySnapshotTokenService {
    private static final int TOKEN_VERSION = 1;
    private static final long TOKEN_TTL_SECONDS = 15 * 60;
    private static final int MAX_TOKEN_LENGTH = 96 * 1024;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private byte[] key;

    @Autowired
    public QuerySnapshotTokenService(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC(), null);
    }

    QuerySnapshotTokenService(ObjectMapper objectMapper, Clock clock, byte[] key) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.key = key == null ? null : Arrays.copyOf(key, key.length);
        if (this.key != null && this.key.length < 32) {
            throw new IllegalArgumentException("Query snapshot signing key must contain at least 32 bytes");
        }
    }

    @PostConstruct
    void initialize() {
        if (key != null) {
            return;
        }
        key = new byte[32];
        new SecureRandom().nextBytes(key);
    }

    public String issue(
            long systemId,
            long tenantId,
            long memberId,
            String moduleCode,
            String moduleId,
            String schemaVersionId,
            String canonicalQuery
    ) {
        ensureKey();
        var payload = new TokenPayload(
                TOKEN_VERSION,
                systemId,
                tenantId,
                memberId,
                moduleCode,
                moduleId,
                schemaVersionId,
                canonicalQuery,
                Instant.now(clock).plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond());
        try {
            var bytes = objectMapper.writeValueAsBytes(payload);
            var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            var signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(bytes));
            return encoded + "." + signature;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize query snapshot token", exception);
        }
    }

    public QuerySnapshot verify(
            String token,
            long systemId,
            long tenantId,
            long memberId,
            String moduleCode,
            String moduleId,
            String schemaVersionId
    ) {
        ensureKey();
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw invalid();
        }
        var separator = token.indexOf('.');
        if (separator <= 0 || separator != token.lastIndexOf('.') || separator == token.length() - 1) {
            throw invalid();
        }
        try {
            var payloadBytes = Base64.getUrlDecoder().decode(token.substring(0, separator));
            var suppliedSignature = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            if (!MessageDigest.isEqual(hmac(payloadBytes), suppliedSignature)) {
                throw invalid();
            }
            var payload = objectMapper.readValue(payloadBytes, TokenPayload.class);
            if (payload.version() != TOKEN_VERSION
                    || payload.systemId() != systemId
                    || payload.tenantId() != tenantId
                    || payload.memberId() != memberId
                    || !Objects.equals(payload.moduleCode(), moduleCode)
                    || !Objects.equals(payload.moduleId(), moduleId)
                    || !Objects.equals(payload.schemaVersionId(), schemaVersionId)
                    || payload.canonicalQuery() == null
                    || payload.canonicalQuery().isBlank()
                    || payload.expiresAtEpochSecond() <= Instant.now(clock).getEpochSecond()) {
                throw invalid();
            }
            return new QuerySnapshot(payload.canonicalQuery(), payload.expiresAtEpochSecond());
        } catch (IllegalArgumentException | IOException exception) {
            throw invalid();
        }
    }

    private synchronized void ensureKey() {
        if (key == null) {
            initialize();
        }
    }

    private byte[] hmac(byte[] value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(
                "QUERY_SNAPSHOT_INVALID",
                "Query snapshot is invalid, expired, or belongs to another context",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record QuerySnapshot(String canonicalQuery, long expiresAtEpochSecond) {
    }

    private record TokenPayload(
            int version,
            long systemId,
            long tenantId,
            long memberId,
            String moduleCode,
            String moduleId,
            String schemaVersionId,
            String canonicalQuery,
            long expiresAtEpochSecond
    ) {
    }
}
