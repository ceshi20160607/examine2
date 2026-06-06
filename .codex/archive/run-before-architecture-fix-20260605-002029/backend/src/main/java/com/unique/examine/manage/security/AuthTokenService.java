package com.unique.examine.manage.security;

import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final ObjectMapper objectMapper;

    @Value("${app.security.token-secret}")
    private String tokenSecret;

    @Value("${app.security.token-ttl-minutes:720}")
    private long tokenTtlMinutes;

    public String issue(CurrentUser currentUser) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accountId", currentUser.getAccountId());
            payload.put("account", currentUser.getAccount());
            payload.put("realName", currentUser.getRealName());
            payload.put("systemId", currentUser.getSystemId());
            payload.put("tenantId", currentUser.getTenantId());
            payload.put("exp", Instant.now().plusSeconds(tokenTtlMinutes * 60).getEpochSecond());
            String body = base64Url(objectMapper.writeValueAsBytes(payload));
            return body + "." + sign(body);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "令牌生成失败");
        }
    }

    public CurrentUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !constantEquals(sign(parts[0]), parts[1])) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "令牌签名无效");
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() {});
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));
            if (Instant.now().getEpochSecond() > exp) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "令牌已过期");
            }
            CurrentUser user = new CurrentUser();
            user.setAccountId(asLong(payload.get("accountId")));
            user.setAccount(String.valueOf(payload.get("account")));
            user.setRealName(String.valueOf(payload.get("realName")));
            user.setSystemId(asLong(payload.get("systemId")));
            user.setTenantId(asLong(payload.get("tenantId")));
            return user;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "令牌解析失败");
        }
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return base64Url(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Long asLong(Object value) {
        if (value == null || "null".equals(String.valueOf(value))) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean constantEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
