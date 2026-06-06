package com.unique.examine.manage.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.base.entity.OpenapiClient;
import com.unique.examine.base.entity.OpenapiCredential;
import com.unique.examine.base.entity.OpenapiIpWhitelist;
import com.unique.examine.base.entity.OpenapiScope;
import com.unique.examine.base.service.IOpenapiClientService;
import com.unique.examine.base.service.IOpenapiCredentialService;
import com.unique.examine.base.service.IOpenapiIpWhitelistService;
import com.unique.examine.base.service.IOpenapiScopeService;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.service.OpenApiAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenApiAuthServiceImpl implements OpenApiAuthService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CREDENTIAL_PREFIX = "ENC:v1:";
    private static final int GCM_TAG_BITS = 128;

    private final IOpenapiClientService clientService;
    private final IOpenapiCredentialService credentialService;
    private final IOpenapiScopeService scopeService;
    private final IOpenapiIpWhitelistService ipService;

    @Value("${app.security.openapi-secret}")
    private String openapiSecret;

    @Override
    public Long authenticate(HttpServletRequest request, String body) {
        return authenticateClient(request, body).getId();
    }

    @Override
    public OpenapiClient authenticateClient(HttpServletRequest request, String body) {
        String clientId = requiredHeader(request, "X-Open-Client-Id");
        Integer keyVersion = parseKeyVersion(requiredHeader(request, "X-Open-Key-Version"));
        long timestamp = parseTimestamp(requiredHeader(request, "X-Open-Timestamp"));
        String nonce = requiredHeader(request, "X-Open-Nonce");
        String signature = requiredHeader(request, "X-Open-Signature");
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 300) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "时间戳已过期");
        }

        OpenapiClient client = clientService.getOne(Wrappers.<OpenapiClient>lambdaQuery()
                .eq(OpenapiClient::getClientId, clientId)
                .eq(OpenapiClient::getStatus, StatusEnums.ENABLED), false);
        if (client == null) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "OpenAPI应用无效");
        }
        OpenapiCredential credential = credentialService.getOne(Wrappers.<OpenapiCredential>lambdaQuery()
                .eq(OpenapiCredential::getClientPk, client.getId())
                .eq(OpenapiCredential::getKeyVersion, keyVersion)
                .eq(OpenapiCredential::getStatus, StatusEnums.ENABLED), false);
        if (credential == null || credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "OpenAPI凭证无效");
        }

        validateIp(client.getId(), request.getRemoteAddr());
        String secret = decryptCredentialSecret(credential.getSecretDigest());
        String canonical = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + sha256(body == null ? "" : body);
        if (!constantEquals(hmac(secret, canonical), signature)) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID);
        }
        return client;
    }

    @Override
    public void requireScope(Long clientPk, String scopeType, String scopeValue) {
        long count = scopeService.count(Wrappers.<OpenapiScope>lambdaQuery()
                .eq(OpenapiScope::getClientPk, clientPk)
                .eq(OpenapiScope::getScopeType, scopeType)
                .eq(OpenapiScope::getScopeValue, scopeValue));
        if (count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "OpenAPI授权范围不足");
        }
    }

    @Override
    public void requireRequestScope(OpenapiClient client, Long systemId, Long tenantId, Long appId, Long moduleId, String actionCode) {
        if (!client.getSystemId().equals(systemId) || !client.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "OpenAPI应用归属与请求上下文不一致");
        }
        requireScope(client.getId(), "SYSTEM", String.valueOf(systemId));
        requireScope(client.getId(), "TENANT", String.valueOf(tenantId));
        if (appId != null) {
            requireScope(client.getId(), "APP", String.valueOf(appId));
        }
        if (moduleId != null) {
            requireScope(client.getId(), "MODULE", String.valueOf(moduleId));
        }
        requireScope(client.getId(), "ACTION", actionCode);
    }

    private void validateIp(Long clientPk, String remoteAddr) {
        List<OpenapiIpWhitelist> list = ipService.list(Wrappers.<OpenapiIpWhitelist>lambdaQuery()
                .eq(OpenapiIpWhitelist::getClientPk, clientPk));
        if (!list.isEmpty() && list.stream().noneMatch(ip -> ip.getIpValue().equals(remoteAddr))) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "IP不在白名单");
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "缺少请求头：" + name);
        }
        return value;
    }

    private Integer parseKeyVersion(String rawValue) {
        try {
            return Integer.valueOf(rawValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "密钥版本必须是整数");
        }
    }

    private long parseTimestamp(String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "时间戳格式错误");
        }
    }

    private String decryptCredentialSecret(String secretDigest) {
        if (secretDigest == null || !secretDigest.startsWith(CREDENTIAL_PREFIX)) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "OpenAPI凭证密钥材料无效，请重新生成凭证");
        }
        try {
            String[] parts = secretDigest.substring(CREDENTIAL_PREFIX.length()).split(":");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "OpenAPI凭证密钥材料无效");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[1]);
            String expectedDigest = parts[2];
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            String secret = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            if (!constantEquals(sha256(secret), expectedDigest)) {
                throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "OpenAPI凭证密钥摘要不一致");
            }
            return secret;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.OPENAPI_SIGNATURE_INVALID, "OpenAPI凭证密钥材料无效");
        }
    }

    private SecretKeySpec aesKey() {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(openapiSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OpenAPI密钥派生失败");
        }
    }

    private String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OpenAPI签名生成失败");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "摘要生成失败");
        }
    }

    private boolean constantEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
