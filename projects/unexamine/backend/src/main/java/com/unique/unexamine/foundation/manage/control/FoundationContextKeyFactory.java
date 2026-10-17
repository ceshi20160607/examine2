package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class FoundationContextKeyFactory {
    /** 同一 Redis 可供多个数据库使用，缓存键必须区分实际连接的数据库。 */
    private final String databaseNamespace;

    public FoundationContextKeyFactory(@Value("${spring.datasource.url}") String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalArgumentException("Database URL is required for Redis key isolation");
        }
        this.databaseNamespace = digest(databaseUrl);
    }

    public String commandContext(AuthenticatedContext context, String applicationCode, String operationCode) {
        return "account:" + context.accountId()
                + ":system:" + context.systemId()
                + ":tenant:" + context.tenantId()
                + ":application:" + digest(normalize(applicationCode))
                + ":operation:" + normalize(operationCode);
    }

    public String rateLimitKey(AuthenticatedContext context, String applicationCode, String operationCode) {
        return "unexamine:rate:v2:database:" + databaseNamespace + ":"
                + commandContext(context, applicationCode, operationCode);
    }

    public String permissionCacheKey(Long systemId, Long tenantId, Long tenantMemberId, long epoch) {
        return "unexamine:permission:v2:database:" + databaseNamespace + ":system:" + systemId + ":tenant:" + tenantId
                + ":member:" + tenantMemberId + ":epoch:" + epoch;
    }

    /** 作业 ID 由各数据库独立分配，共用 Redis 时队列、状态与锁必须使用同一数据库前缀。 */
    public String backgroundJobKeyPrefix() {
        return "unexamine:jobs:v2:database:" + databaseNamespace + ":";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "none";
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
