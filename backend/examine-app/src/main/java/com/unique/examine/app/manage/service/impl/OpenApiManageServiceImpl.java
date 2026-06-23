package com.unique.examine.app.manage.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.ClientCredential;
import com.unique.examine.app.base.entity.ClientScope;
import com.unique.examine.app.base.entity.IpWhitelist;
import com.unique.examine.app.base.entity.RateLimitPolicy;
import com.unique.examine.app.base.service.IAccessLogService;
import com.unique.examine.app.base.service.IClientCredentialService;
import com.unique.examine.app.base.service.IClientScopeService;
import com.unique.examine.app.base.service.IClientService;
import com.unique.examine.app.base.service.IIpWhitelistService;
import com.unique.examine.app.base.service.IRateLimitPolicyService;
import com.unique.examine.app.manage.bo.OpenApiAccessLogQueryBO;
import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.bo.OpenApiClientStatusBO;
import com.unique.examine.app.manage.bo.OpenApiIpWhitelistBO;
import com.unique.examine.app.manage.bo.OpenApiRateLimitPolicyDTO;
import com.unique.examine.app.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.app.manage.service.OpenApiManageService;
import com.unique.examine.app.manage.support.OpenApiConstants;
import com.unique.examine.app.manage.vo.OpenApiAccessLogVO;
import com.unique.examine.app.manage.vo.OpenApiClientDetailVO;
import com.unique.examine.app.manage.vo.OpenApiCredentialOnceVO;
import com.unique.examine.app.manage.vo.OpenApiScopeCatalogVO;
import com.unique.examine.app.manage.vo.OpenApiScopeVO;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * OpenAPI 管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class OpenApiManageServiceImpl implements OpenApiManageService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IClientService clientService;

    private final IClientCredentialService credentialService;

    private final IClientScopeService scopeService;

    private final IIpWhitelistService ipWhitelistService;

    private final IRateLimitPolicyService rateLimitPolicyService;

    private final IAccessLogService accessLogService;

    private final ObjectMapper objectMapper;

    @Override
    public List<OpenApiClientDetailVO> listClients(Long systemId, Long tenantId, String keyword, String status) {
        return clientService.lambdaQuery()
                .eq(Client::getSystemId, systemId)
                .eq(Objects.nonNull(tenantId), Client::getTenantId, tenantId)
                .eq(StringUtils.hasText(status), Client::getStatus, status)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper.like(Client::getCode, keyword)
                        .or().like(Client::getName, keyword))
                .eq(Client::getDeleted, (byte) 0)
                .orderByDesc(Client::getUpdatedAt)
                .list()
                .stream()
                .map(client -> toClientVO(client, null))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiClientDetailVO createClient(Long systemId, OpenApiClientSaveBO saveBO) {
        LocalDateTime now = LocalDateTime.now();
        Client client = new Client()
                .setSystemId(systemId)
                .setTenantId(saveBO.getTenantId())
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setStatus(defaultText(saveBO.getStatus(), "ENABLED"))
                .setDataScopeJson(saveBO.getDataScopeJson())
                .setRateLimitPolicyJson(toJson(saveBO.getRateLimitPolicy()))
                .setExpiresAt(saveBO.getExpiresAt())
                .setVersionNo(1)
                .setDeleted((byte) 0)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        clientService.save(client);
        saveScopes(systemId, client.getId(), saveBO.getScopes());
        saveIpWhitelist(systemId, client.getId(), saveBO.getIpWhitelist());
        saveRateLimitPolicies(systemId, client, saveBO.getRateLimitPolicy());
        OpenApiCredentialOnceVO credential = rotateCredential(systemId, client.getId());
        return toClientVO(clientService.getById(client.getId()), credential.getSecretOnce());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiClientDetailVO updateClient(Long systemId, Long clientId, OpenApiClientSaveBO saveBO) {
        Client client = requireClient(systemId, clientId);
        client.setTenantId(saveBO.getTenantId())
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setStatus(defaultText(saveBO.getStatus(), client.getStatus()))
                .setDataScopeJson(saveBO.getDataScopeJson())
                .setRateLimitPolicyJson(toJson(saveBO.getRateLimitPolicy()))
                .setExpiresAt(saveBO.getExpiresAt())
                .setUpdatedAt(LocalDateTime.now());
        clientService.updateById(client);
        saveScopes(systemId, clientId, saveBO.getScopes());
        saveIpWhitelist(systemId, clientId, saveBO.getIpWhitelist());
        saveRateLimitPolicies(systemId, client, saveBO.getRateLimitPolicy());
        return toClientVO(clientService.getById(clientId), null);
    }

    @Override
    public OpenApiClientDetailVO changeStatus(Long systemId, Long clientId, OpenApiClientStatusBO statusBO) {
        Client client = requireClient(systemId, clientId);
        client.setStatus(statusBO.getStatus()).setUpdatedAt(LocalDateTime.now());
        clientService.updateById(client);
        return toClientVO(client, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiCredentialOnceVO rotateCredential(Long systemId, Long clientId) {
        Client client = requireClient(systemId, clientId);
        LocalDateTime now = LocalDateTime.now();
        credentialService.lambdaUpdate()
                .eq(ClientCredential::getClientId, clientId)
                .eq(ClientCredential::getStatus, "ACTIVE")
                .set(ClientCredential::getStatus, "REVOKED")
                .set(ClientCredential::getRevokedAt, now)
                .set(ClientCredential::getUpdatedAt, now)
                .update();
        String accessKey = "ak_" + randomToken(24);
        String secret = "sk_" + randomToken(40);
        ClientCredential credential = new ClientCredential()
                .setClientId(client.getId())
                .setAccessKey(accessKey)
                .setSecretHash(sha256Hex(secret))
                .setSignSecretEnc(Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)))
                .setMaskedSecret(maskSecret(secret))
                .setAlgorithm(OpenApiConstants.SIGN_ALGORITHM)
                .setStatus("ACTIVE")
                .setSecretVisibleOnce((byte) 1)
                .setIssuedAt(now)
                .setExpiresAt(client.getExpiresAt())
                .setCreatedAt(now)
                .setUpdatedAt(now);
        credentialService.save(credential);
        return OpenApiCredentialOnceVO.builder()
                .clientId(clientId)
                .accessKey(accessKey)
                .secretOnce(secret)
                .maskedSecret(credential.getMaskedSecret())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiClientDetailVO saveScopes(Long systemId, Long clientId, List<OpenApiScopeSaveBO> scopes) {
        Client client = requireClient(systemId, clientId);
        scopeService.lambdaUpdate().eq(ClientScope::getClientId, clientId).remove();
        if (!CollectionUtils.isEmpty(scopes)) {
            LocalDateTime now = LocalDateTime.now();
            List<ClientScope> entities = scopes.stream()
                    .map(scope -> new ClientScope()
                            .setClientId(clientId)
                            .setSystemId(systemId)
                            .setTenantId(client.getTenantId())
                            .setScopeCode(scope.getScopeCode())
                            .setModuleId(scope.getModuleId())
                            .setFieldPermissionJson(scope.getFieldPermissionJson())
                            .setDataScopeJson(scope.getDataScopeJson())
                            .setStatus(defaultText(scope.getStatus(), "ENABLED"))
                            .setCreatedAt(now)
                            .setUpdatedAt(now))
                    .toList();
            scopeService.saveBatch(entities);
        }
        return toClientVO(client, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiClientDetailVO saveIpWhitelist(Long systemId, Long clientId, List<OpenApiIpWhitelistBO> ipWhitelist) {
        Client client = requireClient(systemId, clientId);
        ipWhitelistService.lambdaUpdate().eq(IpWhitelist::getClientId, clientId).remove();
        if (!CollectionUtils.isEmpty(ipWhitelist)) {
            LocalDateTime now = LocalDateTime.now();
            List<IpWhitelist> entities = ipWhitelist.stream()
                    .map(rule -> new IpWhitelist()
                            .setClientId(clientId)
                            .setIpRule(rule.getIpRule())
                            .setRuleType(defaultText(rule.getRuleType(), "IP"))
                            .setStatus(defaultText(rule.getStatus(), "ENABLED"))
                            .setDescription(rule.getDescription())
                            .setCreatedAt(now)
                            .setUpdatedAt(now))
                    .toList();
            ipWhitelistService.saveBatch(entities);
        }
        return toClientVO(client, null);
    }

    @Override
    public PageResult<OpenApiAccessLogVO> listAccessLogs(Long systemId, OpenApiAccessLogQueryBO queryBO) {
        Page<AccessLog> page = new Page<>(safePageNo(queryBO.getPageNo()), safePageSize(queryBO.getPageSize()));
        IPage<AccessLog> result = accessLogService.lambdaQuery()
                .eq(AccessLog::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getClientId()), AccessLog::getClientId, queryBO.getClientId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), AccessLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getApiId()), AccessLog::getApiId, queryBO.getApiId())
                .eq(StringUtils.hasText(queryBO.getErrorCode()), AccessLog::getErrorCode, queryBO.getErrorCode())
                .orderByDesc(AccessLog::getCreatedAt)
                .page(page);
        Long total = accessLogService.lambdaQuery()
                .eq(AccessLog::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getClientId()), AccessLog::getClientId, queryBO.getClientId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), AccessLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getApiId()), AccessLog::getApiId, queryBO.getApiId())
                .eq(StringUtils.hasText(queryBO.getErrorCode()), AccessLog::getErrorCode, queryBO.getErrorCode())
                .count();
        return PageResult.<OpenApiAccessLogVO>builder()
                .records(result.getRecords().stream().map(this::toAccessLogVO).toList())
                .total(total)
                .pageNo(result.getCurrent())
                .pageSize(result.getSize())
                .hasNext(result.getCurrent() * result.getSize() < total)
                .build();
    }

    @Override
    public OpenApiScopeCatalogVO scopeCatalog(Long systemId) {
        return OpenApiScopeCatalogVO.builder()
                .systemId(systemId)
                .scopeCodes(List.of("record:read", "record:create", "record:update", "record:submit",
                        "flow:task:handle", "file:download"))
                .description("OpenAPI MVP 可授权 scope 目录，模块级授权通过 moduleId 限定。")
                .build();
    }

    private void saveRateLimitPolicies(Long systemId, Client client, List<OpenApiRateLimitPolicyDTO> policies) {
        rateLimitPolicyService.lambdaUpdate().eq(RateLimitPolicy::getClientId, client.getId()).remove();
        if (CollectionUtils.isEmpty(policies)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RateLimitPolicy> entities = policies.stream()
                .filter(policy -> Objects.nonNull(policy.getWindowSeconds()) && Objects.nonNull(policy.getMaxRequests()))
                .map(policy -> new RateLimitPolicy()
                        .setClientId(client.getId())
                        .setSystemId(systemId)
                        .setTenantId(client.getTenantId())
                        .setApiId(policy.getApiId())
                        .setScopeCode(policy.getScopeCode())
                        .setSourceIp(policy.getSourceIp())
                        .setWindowSeconds(policy.getWindowSeconds())
                        .setMaxRequests(policy.getMaxRequests())
                        .setBurst(Objects.requireNonNullElse(policy.getBurst(), 0))
                        .setEffectiveFrom(policy.getEffectiveFrom())
                        .setEffectiveTo(policy.getEffectiveTo())
                        .setStatus(defaultText(policy.getStatus(), "ENABLED"))
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        if (!entities.isEmpty()) {
            rateLimitPolicyService.saveBatch(entities);
        }
    }

    private Client requireClient(Long systemId, Long clientId) {
        Client client = clientService.getById(clientId);
        if (Objects.isNull(client) || !Objects.equals(client.getSystemId(), systemId)
                || Objects.equals(client.getDeleted(), (byte) 1)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return client;
    }

    private OpenApiClientDetailVO toClientVO(Client client, String secretOnce) {
        ClientCredential credential = credentialService.lambdaQuery()
                .eq(ClientCredential::getClientId, client.getId())
                .eq(ClientCredential::getStatus, "ACTIVE")
                .orderByDesc(ClientCredential::getIssuedAt)
                .last("limit 1")
                .one();
        return OpenApiClientDetailVO.builder()
                .clientId(client.getId())
                .accessKey(Objects.nonNull(credential) ? credential.getAccessKey() : null)
                .secretOnce(secretOnce)
                .maskedSecret(Objects.nonNull(credential) ? credential.getMaskedSecret() : null)
                .systemId(client.getSystemId())
                .tenantId(client.getTenantId())
                .code(client.getCode())
                .name(client.getName())
                .status(client.getStatus())
                .dataScopeJson(client.getDataScopeJson())
                .expiresAt(client.getExpiresAt())
                .lastUsedAt(client.getLastUsedAt())
                .scopes(scopeService.lambdaQuery().eq(ClientScope::getClientId, client.getId()).list()
                        .stream().map(this::toScopeVO).toList())
                .ipWhitelist(ipWhitelistService.lambdaQuery().eq(IpWhitelist::getClientId, client.getId()).list()
                        .stream().map(IpWhitelist::getIpRule).toList())
                .rateLimitPolicy(rateLimitPolicyService.lambdaQuery().eq(RateLimitPolicy::getClientId, client.getId())
                        .list().stream().map(this::toRateLimitDTO).toList())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }

    private OpenApiScopeVO toScopeVO(ClientScope scope) {
        return OpenApiScopeVO.builder()
                .scopeId(scope.getId())
                .scopeCode(scope.getScopeCode())
                .moduleId(scope.getModuleId())
                .fieldPermissionJson(scope.getFieldPermissionJson())
                .dataScopeJson(scope.getDataScopeJson())
                .status(scope.getStatus())
                .build();
    }

    private OpenApiRateLimitPolicyDTO toRateLimitDTO(RateLimitPolicy policy) {
        OpenApiRateLimitPolicyDTO dto = new OpenApiRateLimitPolicyDTO();
        dto.setApiId(policy.getApiId());
        dto.setScopeCode(policy.getScopeCode());
        dto.setSourceIp(policy.getSourceIp());
        dto.setWindowSeconds(policy.getWindowSeconds());
        dto.setMaxRequests(policy.getMaxRequests());
        dto.setBurst(policy.getBurst());
        dto.setEffectiveFrom(policy.getEffectiveFrom());
        dto.setEffectiveTo(policy.getEffectiveTo());
        dto.setStatus(policy.getStatus());
        return dto;
    }

    private OpenApiAccessLogVO toAccessLogVO(AccessLog log) {
        return OpenApiAccessLogVO.builder()
                .logId(log.getId())
                .requestId(log.getRequestId())
                .clientId(log.getClientId())
                .accessKey(log.getAccessKey())
                .systemId(log.getSystemId())
                .tenantId(log.getTenantId())
                .apiId(log.getApiId())
                .method(log.getMethod())
                .path(log.getPath())
                .statusCode(log.getHttpStatus())
                .errorCode(log.getErrorCode())
                .signatureResult(log.getSignatureResult())
                .nonceResult(log.getNonceResult())
                .idempotencyResult(log.getIdempotencyResult())
                .rateLimitResult(log.getRateLimitResult())
                .scopeResult(log.getScopeResult())
                .durationMs(log.getDurationMs())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String toJson(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.REQUEST_BODY_INVALID);
        }
    }

    private static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String maskSecret(String secret) {
        if (secret.length() <= 10) {
            return "****";
        }
        return secret.substring(0, 5) + "****" + secret.substring(secret.length() - 5);
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static long safePageNo(Long pageNo) {
        return Objects.isNull(pageNo) || pageNo < 1 ? 1L : pageNo;
    }

    private static long safePageSize(Long pageSize) {
        if (Objects.isNull(pageSize) || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200L);
    }
}
