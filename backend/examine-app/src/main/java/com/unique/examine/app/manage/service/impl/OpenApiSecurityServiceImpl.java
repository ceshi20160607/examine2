package com.unique.examine.app.manage.service.impl;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.ClientCredential;
import com.unique.examine.app.base.entity.ClientScope;
import com.unique.examine.app.base.entity.IdempotencyRecord;
import com.unique.examine.app.base.entity.IpWhitelist;
import com.unique.examine.app.base.entity.Nonce;
import com.unique.examine.app.base.entity.RateLimitCounter;
import com.unique.examine.app.base.entity.RateLimitPolicy;
import com.unique.examine.app.base.service.IAccessLogService;
import com.unique.examine.app.base.service.IClientCredentialService;
import com.unique.examine.app.base.service.IClientScopeService;
import com.unique.examine.app.base.service.IClientService;
import com.unique.examine.app.base.service.IIdempotencyRecordService;
import com.unique.examine.app.base.service.IIpWhitelistService;
import com.unique.examine.app.base.service.INonceService;
import com.unique.examine.app.base.service.IRateLimitCounterService;
import com.unique.examine.app.base.service.IRateLimitPolicyService;
import com.unique.examine.app.manage.enums.OpenApiErrorCode;
import com.unique.examine.app.manage.service.OpenApiSecurityService;
import com.unique.examine.app.manage.support.OpenApiConstants;
import com.unique.examine.app.manage.vo.OpenApiRequestContext;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.service.IModelService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * OpenAPI 签名与安全校验服务实现。
 */
@Service
@RequiredArgsConstructor
public class OpenApiSecurityServiceImpl implements OpenApiSecurityService {

    private final IClientService clientService;

    private final IClientCredentialService credentialService;

    private final IClientScopeService scopeService;

    private final IIpWhitelistService ipWhitelistService;

    private final INonceService nonceService;

    private final IIdempotencyRecordService idempotencyRecordService;

    private final IRateLimitPolicyService rateLimitPolicyService;

    private final IRateLimitCounterService rateLimitCounterService;

    private final IAccessLogService accessLogService;

    private final IModelService modelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiRequestContext verify(HttpServletRequest request, String rawBody, String apiId, String scopeCode,
            String moduleCode, boolean idempotent) {
        String body = Objects.requireNonNullElse(rawBody, "");
        long startNano = System.nanoTime();
        AccessLog log = createLog(request, body, apiId);
        try {
            String accessKey = requireHeader(request, "X-OpenApi-AccessKey", CommonErrorCode.UNAUTHORIZED);
            ClientCredential credential = requireCredential(accessKey);
            Client client = requireClient(credential.getClientId());
            Long moduleId = resolveModuleId(client, moduleCode);
            log.setClientId(client.getId())
                    .setAccessKey(accessKey)
                    .setSystemId(client.getSystemId())
                    .setTenantId(client.getTenantId());
            accessLogService.updateById(log);

            validateClient(client);
            validateIp(client.getId(), log.getSourceIp());
            validateTimestamp(request);
            validateNonce(client, credential, request, log);
            validateBodyHash(request, body);
            validateSignature(request, body, credential);
            validateScope(client, scopeCode, moduleId);
            validateRateLimit(client, apiId, scopeCode, log.getSourceIp(), log.getRequestId());
            validateIdempotencyIfNeeded(client, apiId, scopeCode, request, body, idempotent);

            log.setSignatureResult("PASS")
                    .setNonceResult("PASS")
                    .setScopeResult("PASS")
                    .setRateLimitResult("PASS")
                    .setIdempotencyResult(idempotent ? "NEW" : "NOT_CHECKED")
                    .setUpdatedAt(LocalDateTime.now());
            accessLogService.updateById(log);
            return OpenApiRequestContext.builder()
                    .logId(log.getId())
                    .requestId(log.getRequestId())
                    .apiId(apiId)
                    .scopeCode(scopeCode)
                    .client(client)
                    .credential(credential)
                    .moduleId(moduleId)
                    .sourceIp(log.getSourceIp())
                    .startNanoTime(startNano)
                    .build();
        } catch (BusinessException e) {
            markFailure(log, e.getErrorCode());
            throw e;
        }
    }

    @Override
    public void markSuccess(OpenApiRequestContext context, String bizType, String bizId) {
        AccessLog log = accessLogService.getById(context.getLogId());
        if (Objects.isNull(log)) {
            return;
        }
        log.setResult("SUCCESS")
                .setHttpStatus(200)
                .setBizType(bizType)
                .setBizId(bizId)
                .setDurationMs(durationMs(context.getStartNanoTime()))
                .setUpdatedAt(LocalDateTime.now());
        accessLogService.updateById(log);
        Client client = context.getClient();
        client.setLastUsedAt(LocalDateTime.now()).setUpdatedAt(LocalDateTime.now());
        clientService.updateById(client);
        ClientCredential credential = context.getCredential();
        credential.setLastUsedAt(LocalDateTime.now()).setUpdatedAt(LocalDateTime.now());
        credentialService.updateById(credential);
        idempotencyRecordService.lambdaUpdate()
                .eq(IdempotencyRecord::getRequestId, context.getRequestId())
                .set(IdempotencyRecord::getStatus, "SUCCESS")
                .set(IdempotencyRecord::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    @Override
    public void markFailure(OpenApiRequestContext context, String errorCode) {
        if (Objects.isNull(context) || Objects.isNull(context.getLogId())) {
            return;
        }
        AccessLog log = accessLogService.getById(context.getLogId());
        if (Objects.isNull(log)) {
            return;
        }
        log.setResult("FAILED")
                .setHttpStatus(500)
                .setErrorCode(errorCode)
                .setDurationMs(durationMs(context.getStartNanoTime()))
                .setUpdatedAt(LocalDateTime.now());
        accessLogService.updateById(log);
        idempotencyRecordService.lambdaUpdate()
                .eq(IdempotencyRecord::getRequestId, context.getRequestId())
                .set(IdempotencyRecord::getStatus, "FAILED")
                .set(IdempotencyRecord::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private AccessLog createLog(HttpServletRequest request, String body, String apiId) {
        LocalDateTime now = LocalDateTime.now();
        AccessLog log = new AccessLog()
                .setRequestId(resolveRequestId(request))
                .setTraceId(resolveRequestId(request))
                .setApiId(apiId)
                .setMethod(request.getMethod())
                .setPath(request.getRequestURI())
                .setSourceIp(resolveSourceIp(request))
                .setBodyHash(sha256Hex(body))
                .setSignatureResult("NOT_CHECKED")
                .setNonceResult("NOT_CHECKED")
                .setScopeResult("NOT_CHECKED")
                .setRateLimitResult("NOT_CHECKED")
                .setIdempotencyResult("NOT_CHECKED")
                .setResult("FAILED")
                .setCreatedAt(now)
                .setUpdatedAt(now);
        accessLogService.save(log);
        return log;
    }

    private ClientCredential requireCredential(String accessKey) {
        ClientCredential credential = credentialService.lambdaQuery()
                .eq(ClientCredential::getAccessKey, accessKey)
                .eq(ClientCredential::getStatus, "ACTIVE")
                .last("limit 1")
                .one();
        if (Objects.isNull(credential)) {
            throw new BusinessException(OpenApiErrorCode.CLIENT_NOT_FOUND);
        }
        return credential;
    }

    private Client requireClient(Long clientId) {
        Client client = clientService.getById(clientId);
        if (Objects.isNull(client) || Objects.equals(client.getDeleted(), (byte) 1)) {
            throw new BusinessException(OpenApiErrorCode.CLIENT_NOT_FOUND);
        }
        return client;
    }

    private void validateClient(Client client) {
        LocalDateTime now = LocalDateTime.now();
        if (!"ENABLED".equals(client.getStatus()) || (Objects.nonNull(client.getExpiresAt())
                && client.getExpiresAt().isBefore(now))) {
            throw new BusinessException(OpenApiErrorCode.CLIENT_DISABLED);
        }
    }

    private void validateIp(Long clientId, String sourceIp) {
        List<IpWhitelist> rules = ipWhitelistService.lambdaQuery()
                .eq(IpWhitelist::getClientId, clientId)
                .eq(IpWhitelist::getStatus, "ENABLED")
                .list();
        if (CollectionUtils.isEmpty(rules)) {
            return;
        }
        boolean matched = rules.stream().anyMatch(rule -> ipMatches(sourceIp, rule.getIpRule()));
        if (!matched) {
            throw new BusinessException(OpenApiErrorCode.IP_DENIED);
        }
    }

    private void validateTimestamp(HttpServletRequest request) {
        String timestamp = requireHeader(request, "X-OpenApi-Timestamp", OpenApiErrorCode.TIMESTAMP_EXPIRED);
        try {
            long requestTime = Long.parseLong(timestamp);
            long now = Instant.now().toEpochMilli();
            if (Math.abs(now - requestTime) > OpenApiConstants.TIMESTAMP_WINDOW_SECONDS * 1000) {
                throw new BusinessException(OpenApiErrorCode.TIMESTAMP_EXPIRED);
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(OpenApiErrorCode.TIMESTAMP_EXPIRED);
        }
    }

    private void validateNonce(Client client, ClientCredential credential, HttpServletRequest request, AccessLog log) {
        String nonce = requireHeader(request, "X-OpenApi-Nonce", OpenApiErrorCode.NONCE_REPLAY);
        LocalDateTime now = LocalDateTime.now();
        Nonce existing = nonceService.lambdaQuery()
                .eq(Nonce::getClientId, client.getId())
                .eq(Nonce::getAccessKey, credential.getAccessKey())
                .eq(Nonce::getNonce, nonce)
                .gt(Nonce::getExpiresAt, now)
                .last("limit 1")
                .one();
        if (Objects.nonNull(existing)) {
            throw new BusinessException(OpenApiErrorCode.NONCE_REPLAY);
        }
        nonceService.save(new Nonce()
                .setClientId(client.getId())
                .setAccessKey(credential.getAccessKey())
                .setNonce(nonce)
                .setRequestId(log.getRequestId())
                .setSourceIp(log.getSourceIp())
                .setExpiresAt(now.plusSeconds(OpenApiConstants.NONCE_TTL_SECONDS))
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private void validateBodyHash(HttpServletRequest request, String body) {
        String bodyHash = requireHeader(request, "X-OpenApi-Body-Sha256", OpenApiErrorCode.BODY_HASH_MISMATCH);
        if (!sha256Hex(body).equalsIgnoreCase(bodyHash)) {
            throw new BusinessException(OpenApiErrorCode.BODY_HASH_MISMATCH);
        }
    }

    private void validateSignature(HttpServletRequest request, String body, ClientCredential credential) {
        String actual = requireHeader(request, "X-OpenApi-Signature", OpenApiErrorCode.SIGNATURE_INVALID);
        String secret = new String(Base64.getDecoder().decode(credential.getSignSecretEnc()), StandardCharsets.UTF_8);
        String expected = hmacSha256Hex(secret, stringToSign(request, body));
        if (!expected.equals(actual)) {
            throw new BusinessException(OpenApiErrorCode.SIGNATURE_INVALID);
        }
    }

    private void validateScope(Client client, String scopeCode, Long moduleId) {
        List<ClientScope> scopes = scopeService.lambdaQuery()
                .eq(ClientScope::getClientId, client.getId())
                .eq(ClientScope::getScopeCode, scopeCode)
                .eq(ClientScope::getStatus, "ENABLED")
                .list();
        boolean matched = scopes.stream().anyMatch(scope -> Objects.isNull(scope.getModuleId())
                || Objects.isNull(moduleId) || Objects.equals(scope.getModuleId(), moduleId));
        if (!matched) {
            throw new BusinessException(OpenApiErrorCode.SCOPE_DENIED);
        }
    }

    private void validateRateLimit(Client client, String apiId, String scopeCode, String sourceIp, String requestId) {
        LocalDateTime now = LocalDateTime.now();
        List<RateLimitPolicy> policies = rateLimitPolicyService.lambdaQuery()
                .eq(RateLimitPolicy::getClientId, client.getId())
                .eq(RateLimitPolicy::getStatus, "ENABLED")
                .list()
                .stream()
                .filter(policy -> !StringUtils.hasText(policy.getApiId()) || apiId.equals(policy.getApiId()))
                .filter(policy -> !StringUtils.hasText(policy.getScopeCode()) || scopeCode.equals(policy.getScopeCode()))
                .filter(policy -> !StringUtils.hasText(policy.getSourceIp()) || sourceIp.equals(policy.getSourceIp()))
                .filter(policy -> Objects.isNull(policy.getEffectiveFrom()) || !policy.getEffectiveFrom().isAfter(now))
                .filter(policy -> Objects.isNull(policy.getEffectiveTo()) || policy.getEffectiveTo().isAfter(now))
                .toList();
        if (policies.isEmpty()) {
            return;
        }
        RateLimitPolicy strictest = policies.stream().min(Comparator.comparing(RateLimitPolicy::getMaxRequests)).get();
        String dimensionKey = client.getId() + ":" + client.getSystemId() + ":" + client.getTenantId() + ":" + apiId
                + ":" + scopeCode + ":" + sourceIp;
        synchronized (dimensionKey.intern()) {
            LocalDateTime windowStart = now.minusSeconds(now.getSecond() % strictest.getWindowSeconds());
            LocalDateTime windowEnd = windowStart.plusSeconds(strictest.getWindowSeconds());
            RateLimitCounter counter = rateLimitCounterService.lambdaQuery()
                    .eq(RateLimitCounter::getPolicyId, strictest.getId())
                    .eq(RateLimitCounter::getDimensionKey, dimensionKey)
                    .eq(RateLimitCounter::getWindowStartAt, windowStart)
                    .last("limit 1")
                    .one();
            int limit = strictest.getMaxRequests() + Objects.requireNonNullElse(strictest.getBurst(), 0);
            if (Objects.nonNull(counter) && counter.getRequestCount() >= limit) {
                throw new BusinessException(OpenApiErrorCode.RATE_LIMITED);
            }
            if (Objects.isNull(counter)) {
                counter = new RateLimitCounter()
                        .setPolicyId(strictest.getId())
                        .setDimensionKey(dimensionKey)
                        .setWindowStartAt(windowStart)
                        .setWindowEndAt(windowEnd)
                        .setRequestCount(1)
                        .setLastRequestId(requestId)
                        .setCreatedAt(now)
                        .setUpdatedAt(now);
                rateLimitCounterService.save(counter);
            } else {
                counter.setRequestCount(counter.getRequestCount() + 1)
                        .setLastRequestId(requestId)
                        .setUpdatedAt(now);
                rateLimitCounterService.updateById(counter);
            }
        }
    }

    private void validateIdempotencyIfNeeded(Client client, String apiId, String scopeCode, HttpServletRequest request,
            String body, boolean idempotent) {
        if (!idempotent) {
            return;
        }
        String key = request.getHeader("X-Idempotency-Key");
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "OpenAPI 写接口必须传 X-Idempotency-Key");
        }
        String scopeKey = "OPENAPI:" + client.getId() + ":" + client.getSystemId() + ":" + client.getTenantId()
                + ":" + apiId + ":" + scopeCode + ":" + key;
        String requestHash = sha256Hex(request.getMethod() + "\n" + request.getRequestURI() + "\n" + body);
        IdempotencyRecord existing = idempotencyRecordService.lambdaQuery()
                .eq(IdempotencyRecord::getScopeKey, scopeKey)
                .gt(IdempotencyRecord::getExpiresAt, LocalDateTime.now())
                .last("limit 1")
                .one();
        if (Objects.nonNull(existing)) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BusinessException(OpenApiErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if ("PROCESSING".equals(existing.getStatus())) {
                throw new BusinessException(OpenApiErrorCode.IDEMPOTENCY_PROCESSING);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        idempotencyRecordService.save(new IdempotencyRecord()
                .setClientId(client.getId())
                .setSystemId(client.getSystemId())
                .setTenantId(client.getTenantId())
                .setApiId(apiId)
                .setBizAction(scopeCode)
                .setIdempotencyKey(key)
                .setScopeKey(scopeKey)
                .setRequestHash(requestHash)
                .setStatus("PROCESSING")
                .setSignatureResult("PASS")
                .setScopeResult("PASS")
                .setRequestId(resolveRequestId(request))
                .setExpiresAt(now.plusHours(72))
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private Long resolveModuleId(Client client, String moduleCode) {
        if (!StringUtils.hasText(moduleCode)) {
            return null;
        }
        Model model = modelService.lambdaQuery()
                .eq(Model::getSystemId, client.getSystemId())
                .eq(Model::getTenantId, client.getTenantId())
                .eq(Model::getCode, moduleCode)
                .last("limit 1")
                .one();
        if (Objects.isNull(model)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return model.getModuleId();
    }

    private String stringToSign(HttpServletRequest request, String body) {
        return OpenApiConstants.SIGN_ALGORITHM + "\n" + requireHeader(request, "X-OpenApi-Timestamp",
                OpenApiErrorCode.TIMESTAMP_EXPIRED) + "\n" + sha256Hex(canonicalRequest(request, body));
    }

    private String canonicalRequest(HttpServletRequest request, String body) {
        String canonicalHeaders = OpenApiConstants.SIGNED_HEADER_NAMES.stream()
                .map(header -> header + ":" + normalizeHeaderValue(Optional.ofNullable(request.getHeader(header))
                        .orElse("")) + "\n")
                .collect(Collectors.joining());
        return request.getMethod().toUpperCase() + "\n"
                + request.getRequestURI() + "\n"
                + canonicalQueryString(request) + "\n"
                + canonicalHeaders + "\n"
                + OpenApiConstants.SIGNED_HEADERS + "\n"
                + sha256Hex(body);
    }

    private String canonicalQueryString(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap.isEmpty()) {
            return "";
        }
        return parameterMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> Arrays.stream(entry.getValue()).sorted()
                        .map(value -> entry.getKey() + "=" + value))
                .collect(Collectors.joining("&"));
    }

    private void markFailure(AccessLog log, ErrorCode errorCode) {
        log.setResult("FAILED")
                .setHttpStatus(errorCode.getHttpStatus().value())
                .setErrorCode(errorCode.getCode())
                .setDurationMs(0)
                .setUpdatedAt(LocalDateTime.now());
        if (OpenApiErrorCode.NONCE_REPLAY.equals(errorCode)) {
            log.setNonceResult("REPLAY");
        } else if (OpenApiErrorCode.SCOPE_DENIED.equals(errorCode)) {
            log.setScopeResult("DENIED");
        } else if (OpenApiErrorCode.RATE_LIMITED.equals(errorCode)) {
            log.setRateLimitResult("LIMITED");
        } else if (OpenApiErrorCode.SIGNATURE_INVALID.equals(errorCode)
                || OpenApiErrorCode.BODY_HASH_MISMATCH.equals(errorCode)) {
            log.setSignatureResult("FAIL");
        }
        accessLogService.updateById(log);
    }

    private static String requireHeader(HttpServletRequest request, String name, ErrorCode errorCode) {
        String value = request.getHeader(name);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(errorCode);
        }
        return value.strip();
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return StringUtils.hasText(requestId) ? requestId : "openapi-" + Instant.now().toEpochMilli();
    }

    private static String resolveSourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private static String normalizeHeaderValue(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private static boolean ipMatches(String sourceIp, String rule) {
        if (!StringUtils.hasText(rule)) {
            return false;
        }
        if (!rule.contains("/")) {
            return sourceIp.equals(rule);
        }
        try {
            String[] parts = rule.split("/");
            int prefix = Integer.parseInt(parts[1]);
            byte[] source = InetAddress.getByName(sourceIp).getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            if (source.length != network.length || source.length != 4) {
                return false;
            }
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            int sourceInt = bytesToInt(source);
            int networkInt = bytesToInt(network);
            return (sourceInt & mask) == (networkInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(OpenApiErrorCode.SIGNATURE_INVALID);
        }
    }

    private static int durationMs(long startNanoTime) {
        return (int) ((System.nanoTime() - startNanoTime) / 1_000_000);
    }
}
