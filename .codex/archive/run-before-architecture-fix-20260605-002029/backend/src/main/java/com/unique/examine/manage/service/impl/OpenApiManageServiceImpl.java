package com.unique.examine.manage.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.base.entity.OpenapiClient;
import com.unique.examine.base.entity.OpenapiCredential;
import com.unique.examine.base.entity.OpenapiIpWhitelist;
import com.unique.examine.base.entity.OpenapiScope;
import com.unique.examine.base.service.IOpenapiClientService;
import com.unique.examine.base.service.IOpenapiCredentialService;
import com.unique.examine.base.service.IOpenapiIpWhitelistService;
import com.unique.examine.base.service.IOpenapiScopeService;
import com.unique.examine.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.manage.bo.OpenApiCredentialCreateBO;
import com.unique.examine.manage.bo.OpenApiIpSaveBO;
import com.unique.examine.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.service.OpenApiManageService;
import com.unique.examine.manage.service.PermissionService;
import com.unique.examine.manage.vo.CredentialVO;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.SimpleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenApiManageServiceImpl implements OpenApiManageService {
    private static final String CREDENTIAL_PREFIX = "ENC:v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final IOpenapiClientService clientService;
    private final IOpenapiCredentialService credentialService;
    private final IOpenapiScopeService scopeService;
    private final IOpenapiIpWhitelistService ipService;
    private final PermissionService permissionService;
    private final EntityMapConverter converter;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.openapi-secret}")
    private String openapiSecret;

    @Override
    public PageResult<SimpleVO> clients(long pageNo, long pageSize, Long systemId, Long tenantId) {
        permissionService.requireAction(systemId, tenantId, "openapi:view");
        IPage<OpenapiClient> page = clientService.page(Page.of(pageNo, pageSize), Wrappers.<OpenapiClient>lambdaQuery()
                .eq(OpenapiClient::getSystemId, systemId)
                .eq(OpenapiClient::getTenantId, tenantId)
                .orderByDesc(OpenapiClient::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveClient(OpenApiClientSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "openapi:save");
        OpenapiClient client = new OpenapiClient();
        client.setSystemId(bo.getSystemId());
        client.setTenantId(bo.getTenantId());
        client.setClientId(bo.getClientId());
        client.setClientName(bo.getClientName());
        client.setRateLimitRule(bo.getRateLimitRule());
        client.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        clientService.save(client);
        return converter.toSimple(client);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CredentialVO createCredential(OpenApiCredentialCreateBO bo) {
        OpenapiClient client = clientService.getById(bo.getClientPk());
        if (client == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI应用不存在");
        }
        permissionService.requireAction(client.getSystemId(), client.getTenantId(), "openapi:credential");
        int keyVersion = parseKeyVersion(bo.getKeyVersion(), bo.getClientPk());
        String secretOnce = generateSecret();

        OpenapiCredential credential = new OpenapiCredential();
        credential.setClientPk(bo.getClientPk());
        credential.setKeyVersion(keyVersion);
        credential.setSecretDigest(encryptSecretPackage(secretOnce));
        credential.setExpiresAt(bo.getExpiresAt());
        credential.setStatus(StatusEnums.ENABLED);
        credentialService.save(credential);

        CredentialVO vo = new CredentialVO();
        vo.setId(credential.getId());
        vo.setClientPk(credential.getClientPk());
        vo.setKeyVersion(credential.getKeyVersion());
        vo.setSecretOnce(secretOnce);
        vo.setStatus(credential.getStatus());
        vo.setExpiresAt(credential.getExpiresAt());
        vo.setCreatedAt(credential.getCreatedAt() == null ? LocalDateTime.now() : credential.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveScope(OpenApiScopeSaveBO bo) {
        OpenapiClient client = clientService.getById(bo.getClientPk());
        if (client == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI应用不存在");
        }
        permissionService.requireAction(client.getSystemId(), client.getTenantId(), "openapi:scope");
        OpenapiScope scope = new OpenapiScope();
        scope.setClientPk(bo.getClientPk());
        scope.setScopeType(bo.getScopeType());
        scope.setScopeValue(bo.getScopeValue());
        scopeService.save(scope);
        return converter.toSimple(scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveIp(OpenApiIpSaveBO bo) {
        OpenapiClient client = clientService.getById(bo.getClientPk());
        if (client == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI应用不存在");
        }
        permissionService.requireAction(client.getSystemId(), client.getTenantId(), "openapi:ip");
        List<String> values = normalizeIps(bo);
        if (values.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "IP白名单不能为空");
        }
        OpenapiIpWhitelist first = null;
        for (String value : values) {
            OpenapiIpWhitelist ip = new OpenapiIpWhitelist();
            ip.setClientPk(bo.getClientPk());
            ip.setIpValue(value);
            ipService.save(ip);
            if (first == null) {
                first = ip;
            }
        }
        return converter.toSimple(first);
    }

    private int parseKeyVersion(String keyVersion, Long clientPk) {
        if (keyVersion == null || keyVersion.isBlank()) {
            return credentialService.list(Wrappers.<OpenapiCredential>lambdaQuery()
                            .eq(OpenapiCredential::getClientPk, clientPk))
                    .stream().map(OpenapiCredential::getKeyVersion).max(Integer::compareTo).orElse(0) + 1;
        }
        try {
            return Integer.parseInt(keyVersion.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "OpenAPI密钥版本必须是整数，或留空由后端生成");
        }
    }

    private List<String> normalizeIps(OpenApiIpSaveBO bo) {
        List<String> values = new ArrayList<>();
        if (bo.getIpValue() != null && !bo.getIpValue().isBlank()) {
            values.add(bo.getIpValue().trim());
        }
        if (bo.getIpList() != null) {
            bo.getIpList().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(values::add);
        }
        return values.stream().distinct().toList();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encryptSecretPackage(String secret) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return CREDENTIAL_PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted) + ":"
                    + sha256(secret);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OpenAPI密钥加密失败");
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OpenAPI密钥摘要失败");
        }
    }
}
