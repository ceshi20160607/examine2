package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class FoundationContextKeyFactory {
    public String commandContext(AuthenticatedContext context, String applicationCode, String operationCode) {
        return "account:" + context.accountId()
                + ":system:" + context.systemId()
                + ":tenant:" + context.tenantId()
                + ":application:" + digest(normalize(applicationCode))
                + ":operation:" + normalize(operationCode);
    }

    public String rateLimitKey(AuthenticatedContext context, String applicationCode, String operationCode) {
        return "unexamine:rate:v1:" + commandContext(context, applicationCode, operationCode);
    }

    public String permissionCacheKey(Long systemId, Long tenantId, Long tenantMemberId, long epoch) {
        return "unexamine:permission:v1:system:" + systemId + ":tenant:" + tenantId
                + ":member:" + tenantMemberId + ":epoch:" + epoch;
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
