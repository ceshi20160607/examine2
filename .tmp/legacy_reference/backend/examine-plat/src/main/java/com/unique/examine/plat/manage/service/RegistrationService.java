package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.dto.RegisterRequest;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.manage.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class RegistrationService {
    private final PlatAccountMapper accountMapper;
    private final PlatCredentialMapper credentialMapper;
    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatMemberMapper memberMapper;
    private final AuthorizationProvisioningService authorizationProvisioningService;
    private final IdService idService;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final IdempotencyFacade idempotencyFacade;
    private final AuditFacade auditFacade;
    private final ObjectMapper objectMapper;

    public RegistrationService(
            PlatAccountMapper accountMapper,
            PlatCredentialMapper credentialMapper,
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatMemberMapper memberMapper,
            AuthorizationProvisioningService authorizationProvisioningService,
            IdService idService,
            PasswordService passwordService,
            TokenService tokenService,
            SessionService sessionService,
            IdempotencyFacade idempotencyFacade,
            AuditFacade auditFacade,
            ObjectMapper objectMapper
    ) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
        this.authorizationProvisioningService = authorizationProvisioningService;
        this.idService = idService;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.idempotencyFacade = idempotencyFacade;
        this.auditFacade = auditFacade;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IssuedSession register(
            RegisterRequest request,
            String idempotencyKey,
            ClientRequest clientRequest
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "缺少有效的 Idempotency-Key", HttpStatus.BAD_REQUEST);
        }
        var username = normalize(request.username());
        var systemCode = request.systemCode().toLowerCase(Locale.ROOT);
        var scopeKey = username + "|" + systemCode;
        var requestHash = tokenService.requestHash(write(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var existing = idempotencyFacade.find("REGISTER", scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.requestHash().equals(requestHash)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "相同幂等键对应了不同请求", HttpStatus.CONFLICT);
            }
            if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
                throw new BusinessException("REQUEST_IN_PROGRESS", "注册请求正在处理中", HttpStatus.CONFLICT);
            }
            var replay = read(record.responseBody(), RegistrationRecord.class);
            var account = accountMapper.selectById(replay.accountId());
            var issued = sessionService.issuePlatform(account);
            return issued.withResult(issued.result().withFirstSystemId(Long.toString(replay.systemId())));
        }

        ensureAvailable(username, systemCode);
        var idempotencyId = idempotencyFacade.begin(
                "REGISTER", scopeKey, idempotencyKey, requestHash, Duration.ofHours(24)
        );
        var now = LocalDateTime.now();
        var accountId = idService.nextId();
        var systemId = idService.nextId();
        var tenantId = idService.nextId();
        var memberId = idService.nextId();
        var roleId = idService.nextId();

        var account = new Account();
        account.setId(accountId);
        account.setAccountCode("ACC_" + accountId);
        account.setUsername(request.username().trim());
        account.setUsernameNormalized(username);
        account.setDisplayName(request.displayName().trim());
        account.setLocale("zh-CN");
        account.setTimeZone("Asia/Shanghai");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setCreatedBy(accountId);
        account.setUpdatedAt(now);
        account.setUpdatedBy(accountId);
        account.setVersion(0L);
        accountMapper.insert(account);

        var password = passwordService.hash(request.password());
        var credential = new Credential();
        credential.setId(idService.nextId());
        credential.setAccountId(accountId);
        credential.setCredentialType("PASSWORD");
        credential.setPasswordHash(password.encoded());
        credential.setPasswordAlgorithm(password.algorithm());
        credential.setPasswordParameters(password.parameters());
        credential.setFailedAttempts(0);
        credential.setPasswordChangedAt(now);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credential.setVersion(0L);
        credentialMapper.insert(credential);

        var system = new com.unique.examine.plat.base.entity.System();
        system.setId(systemId);
        system.setSystemCode(systemCode);
        system.setName(request.systemName().trim());
        system.setStatus("INITIALIZING");
        system.setTenantMode("SINGLE");
        system.setOwnerAccountId(accountId);
        system.setPermissionVersion(1L);
        system.setInitializedAt(now);
        system.setCreatedAt(now);
        system.setCreatedBy(accountId);
        system.setUpdatedAt(now);
        system.setUpdatedBy(accountId);
        system.setVersion(0L);
        systemMapper.insert(system);

        var tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSystemId(systemId);
        tenant.setTenantCode("default");
        tenant.setName("默认租户");
        tenant.setIsDefault(true);
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(now);
        tenant.setCreatedBy(accountId);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(accountId);
        tenant.setVersion(0L);
        tenantMapper.insert(tenant);

        var member = new Member();
        member.setId(memberId);
        member.setSystemId(systemId);
        member.setAccountId(accountId);
        member.setMemberCode("OWNER_" + memberId);
        member.setDisplayName(request.displayName().trim());
        member.setDefaultTenantId(tenantId);
        member.setStatus("ACTIVE");
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setCreatedBy(accountId);
        member.setUpdatedAt(now);
        member.setUpdatedBy(accountId);
        member.setVersion(0L);
        memberMapper.insert(member);

        authorizationProvisioningService.provisionPlatformMember(accountId, now);
        authorizationProvisioningService.provisionSystemOwner(
                accountId, systemId, tenantId, memberId, roleId, now
        );

        var registrationRecord = new RegistrationRecord(accountId, systemId);
        idempotencyFacade.complete(idempotencyId, 200, "OK", write(registrationRecord));
        auditFacade.recordSecurity(new AuditEvent(
                "REGISTER_FIRST_SYSTEM",
                accountId,
                hint(username),
                systemId,
                tenantId,
                "WEB",
                clientRequest.remoteAddress(),
                clientRequest.userAgent(),
                clientRequest.requestId(),
                clientRequest.traceId(),
                "SUCCESS",
                null,
                "{}"
        ));
        var issued = sessionService.issuePlatform(account);
        return issued.withResult(issued.result().withFirstSystemId(Long.toString(systemId)));
    }

    private void ensureAvailable(String username, String systemCode) {
        var accountCount = accountMapper.selectCount(Wrappers.<Account>lambdaQuery()
                .eq(Account::getUsernameNormalized, username));
        if (accountCount > 0) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在", HttpStatus.CONFLICT);
        }
        var systemCount = systemMapper.selectCount(Wrappers.<com.unique.examine.plat.base.entity.System>lambdaQuery()
                .eq(com.unique.examine.plat.base.entity.System::getSystemCode, systemCode));
        if (systemCount > 0) {
            throw new BusinessException("SYSTEM_CODE_EXISTS", "系统编码已存在", HttpStatus.CONFLICT);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize registration state", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read registration state", exception);
        }
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String hint(String value) {
        return tokenService.hash(value).substring(0, 16);
    }

    private record RegistrationRecord(long accountId, long systemId) {
    }
}
