package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticationService;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.SessionTokens;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class SystemEntryService {
    private final SystemDefinitionBaseService systemService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantBaseService tenantService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final PermissionResolver permissionResolver;
    private final AuthenticationService authenticationService;
    private final AuditRecorder auditRecorder;
    private final AuditEventBaseService auditService;
    private final ObjectMapper objectMapper;

    /** 专属登录页只披露有效系统和空间的展示信息，实际进入仍逐项校验成员及角色。 */
    @Transactional(readOnly = true)
    public SystemAccessEntryView accessEntry(String systemCode, String tenantCode) {
        if (systemCode == null || tenantCode == null || systemCode.length() > 100 || tenantCode.length() > 100) {
            throw new DomainException("ACCESS_ENTRY_NOT_FOUND", "访问地址不存在或已停用", HttpStatus.NOT_FOUND);
        }
        List<SystemDefinition> systems = systemService.selectList(Wrappers.<SystemDefinition>lambdaQuery()
                .eq(SystemDefinition::getCode, systemCode).eq(SystemDefinition::getDeleted, false)
                .eq(SystemDefinition::getStatus, "ACTIVE"));
        // 系统编码仅在平台内唯一；跨平台同名时不能任取第一条作为登录目标。
        if (systems.size() != 1) {
            throw new DomainException("ACCESS_ENTRY_NOT_FOUND", "访问地址不存在或已停用", HttpStatus.NOT_FOUND);
        }
        SystemDefinition system = systems.get(0);
        SystemTenant tenant = first(tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, system.getId()).eq(SystemTenant::getCode, tenantCode)
                .eq(SystemTenant::getStatus, "ACTIVE")));
        if (tenant == null || ("SINGLE".equals(system.getTenantMode()) && !Boolean.TRUE.equals(tenant.getMain()))) {
            throw new DomainException("ACCESS_ENTRY_NOT_FOUND", "访问地址不存在或已停用", HttpStatus.NOT_FOUND);
        }
        return viewAccessEntry(system, tenant);
    }

    /** 已验证的域名按绑定主键解析，避免跨平台同名系统编码混淆。 */
    @Transactional(readOnly = true)
    public SystemAccessEntryView accessEntry(Long systemId, Long tenantId) {
        SystemDefinition system = systemService.selectById(systemId);
        SystemTenant tenant = tenantService.selectById(tenantId);
        if (system == null || Boolean.TRUE.equals(system.getDeleted()) || !"ACTIVE".equals(system.getStatus())
                || tenant == null || !systemId.equals(tenant.getSystemId()) || !"ACTIVE".equals(tenant.getStatus())
                || ("SINGLE".equals(system.getTenantMode()) && !Boolean.TRUE.equals(tenant.getMain()))) {
            throw new DomainException("ACCESS_ENTRY_NOT_FOUND", "访问地址不存在或已停用", HttpStatus.NOT_FOUND);
        }
        return viewAccessEntry(system, tenant);
    }

    private SystemAccessEntryView viewAccessEntry(SystemDefinition system, SystemTenant tenant) {
        return new SystemAccessEntryView(system.getId(), system.getCode(), system.getName(),
                system.getTenantMode(), tenant.getId(), tenant.getCode(), tenant.getName(),
                tenant.getLoginTitle(), tenant.getLoginSubtitle());
    }

    public SystemEntryService(
            SystemDefinitionBaseService systemService,
            SystemMemberBaseService memberService,
            SystemTenantBaseService tenantService,
            SystemTenantMemberBaseService tenantMemberService,
            PermissionResolver permissionResolver,
            AuthenticationService authenticationService,
            AuditRecorder auditRecorder,
            AuditEventBaseService auditService,
            ObjectMapper objectMapper) {
        this.systemService = systemService;
        this.memberService = memberService;
        this.tenantService = tenantService;
        this.tenantMemberService = tenantMemberService;
        this.permissionResolver = permissionResolver;
        this.authenticationService = authenticationService;
        this.auditRecorder = auditRecorder;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SystemEntryResult enter(Long accountId, Long systemId, String traceId) {
        return enter(accountId, systemId, null, null, traceId);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SystemEntryResult enter(
            Long accountId, Long systemId, Long previousSystemId, Long previousTenantId, String traceId) {
        SystemDefinition system = systemService.selectById(systemId);
        if (system == null || Boolean.TRUE.equals(system.getDeleted()) || !"ACTIVE".equals(system.getStatus())) {
            deny(accountId, system == null ? null : system.getId(), traceId);
        }
        SystemTenant tenant = first(tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, systemId)
                .eq(SystemTenant::getMain, true)
                .eq(SystemTenant::getStatus, "ACTIVE")));
        if (tenant == null) {
            recordSsoSystemOutcome(traceId, accountId, systemId, null, null, null, "SYSTEM_CONFIGURATION_INVALID");
            throw new DomainException("SYSTEM_CONFIGURATION_INVALID", "系统缺少可用的默认主租户", HttpStatus.CONFLICT);
        }
        SystemMember member = first(memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE")));
        if (member == null) deny(accountId, systemId, traceId);
        SystemTenant permittedTenant = defaultTenant(system, member);
        if (permittedTenant == null) denyTenant(accountId, systemId, null, traceId, "NO_ACTIVE_TENANT_MEMBERSHIP");
        return enterResolved(accountId, system, permittedTenant, previousSystemId, previousTenantId, traceId);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SystemEntryResult enterTenant(Long accountId, Long systemId, Long tenantId, String traceId) {
        return enterTenant(accountId, systemId, tenantId, null, null, traceId);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SystemEntryResult enterTenant(
            Long accountId, Long systemId, Long tenantId, Long previousSystemId, Long previousTenantId, String traceId) {
        SystemDefinition system = systemService.selectById(systemId);
        if (system == null || Boolean.TRUE.equals(system.getDeleted()) || !"ACTIVE".equals(system.getStatus())) {
            deny(accountId, system == null ? null : system.getId(), traceId);
        }
        if (!"MULTI".equals(system.getTenantMode())) {
            recordSsoSystemOutcome(traceId, accountId, systemId, tenantId, null, null, "TENANT_SWITCH_NOT_AVAILABLE");
            throw new DomainException("TENANT_SWITCH_NOT_AVAILABLE", "单租户系统不提供租户切换", HttpStatus.CONFLICT);
        }
        SystemTenant tenant = tenantService.selectById(tenantId);
        if (tenant == null || !systemId.equals(tenant.getSystemId()) || !"ACTIVE".equals(tenant.getStatus())) {
            Long auditableTenantId = tenant != null && systemId.equals(tenant.getSystemId()) ? tenantId : null;
            denyTenant(accountId, systemId, auditableTenantId, traceId, "TENANT_NOT_ACTIVE");
        }
        return enterResolved(accountId, system, tenant, previousSystemId, previousTenantId, traceId);
    }

    private SystemEntryResult enterResolved(
            Long accountId,
            SystemDefinition system,
            SystemTenant tenant,
            Long previousSystemId,
            Long previousTenantId,
            String traceId) {
        Long systemId = system.getId();
        SystemMember member = first(memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId)
                .eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE")));
        if (member == null) {
            deny(accountId, systemId, traceId);
        }
        SystemTenantMember tenantMember = first(tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, systemId)
                .eq(SystemTenantMember::getTenantId, tenant.getId())
                .eq(SystemTenantMember::getSystemMemberId, member.getId())
                .eq(SystemTenantMember::getStatus, "ACTIVE")));
        if (tenantMember == null) {
            denyTenant(accountId, systemId, tenant.getId(), traceId, "NO_ACTIVE_TENANT_MEMBERSHIP");
        }

        ResolvedPermissions resolved = permissionResolver.resolve(systemId, tenant.getId(), tenantMember.getId());
        SessionTokens tokens = authenticationService.createSystemSession(
                accountId, systemId, tenant.getId(), member.getId(), tenantMember.getId(), resolved,
                AuthenticationContextHolder.require().mfaLevel());
        auditRecorder.record(traceId, accountId, systemId, tenant.getId(), member.getId(),
                "SYSTEM_ENTER", "SYSTEM", systemId.toString(), "SUCCESS",
                Map.of("roleIds", resolved.roleIds(), "tenantMode", system.getTenantMode()));
        recordSsoSystemOutcome(traceId, accountId, systemId, tenant.getId(), member.getId(),
                tenantMember.getId(), "SUCCESS");
        Map<String, Object> switchDetail = new LinkedHashMap<>();
        switchDetail.put("previousSystemId", previousSystemId);
        switchDetail.put("previousTenantId", previousTenantId);
        switchDetail.put("systemId", systemId);
        switchDetail.put("tenantId", tenant.getId());
        switchDetail.put("redrawScopes", List.of("NAVIGATION", "DASHBOARD", "MODULES", "TODO", "MESSAGES", "FIELD_PERMISSIONS", "DATA_SCOPE"));
        auditRecorder.record(traceId, accountId, systemId, tenant.getId(), member.getId(),
                "SYSTEM_CONTEXT_SWITCHED", "TENANT", tenant.getId().toString(), "SUCCESS", switchDetail);
        TenantSwitchContext tenantSwitchContext = new TenantSwitchContext(tenant.getId(), resolved.roleIds(),
                resolved.dataScopes(), "MULTI".equals(system.getTenantMode()),
                "MULTI".equals(system.getTenantMode()) ? null : "单租户系统不显示租户切换");
        return new SystemEntryResult(systemId, system.getCode(), system.getName(), system.getTenantMode(),
                tenant.getId(), tenant.getName(), member.getId(), tenantMember.getId(), resolved.roleIds(), resolved.permissions(),
                resolved.dataScopes(), traceId,
                List.of("NAVIGATION", "DASHBOARD", "MODULES", "TODO", "MESSAGES", "FIELD_PERMISSIONS", "DATA_SCOPE"),
                tenantSwitchContext, tokens);
    }

    private void denyTenant(Long accountId, Long systemId, Long tenantId, String traceId, String reason) {
        auditRecorder.record(traceId, accountId, systemId, tenantId, null, "TENANT_ENTER", "TENANT",
                tenantId == null ? null : tenantId.toString(), "TENANT_ACCESS_DENIED", Map.of("reason", reason));
        recordSsoSystemOutcome(traceId, accountId, systemId, tenantId, null, null, "TENANT_ACCESS_DENIED");
        throw new DomainException("TENANT_ACCESS_DENIED", "没有该租户的有效成员身份", HttpStatus.FORBIDDEN);
    }

    @Transactional(readOnly = true)
    public List<AccessibleSystem> listAccessible(Long accountId) {
        List<SystemMember> memberships = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE"));
        List<AccessibleSystem> result = new ArrayList<>();
        for (SystemMember membership : memberships) {
            SystemDefinition system = systemService.selectById(membership.getSystemId());
            if (system == null || Boolean.TRUE.equals(system.getDeleted())) {
                continue;
            }
            SystemTenant tenant = defaultTenant(system, membership);
            if (tenant == null) continue; // 没有可进入工作空间的系统不能列为可访问。
            result.add(new AccessibleSystem(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                    system.getStatus(), tenant == null ? null : tenant.getId(), tenant == null ? null : tenant.getName()));
        }
        return result.stream().sorted(java.util.Comparator.comparing(AccessibleSystem::systemId).reversed()).toList();
    }

    /** 运行态只列当前账号实际可进入的租户；单租户系统始终只返回默认主租户。 */
    @Transactional(readOnly = true)
    public List<AccessibleTenant> listAccessibleTenants(Long accountId, Long systemId) {
        SystemDefinition system = systemService.selectById(systemId);
        if (system == null || Boolean.TRUE.equals(system.getDeleted()) || !"ACTIVE".equals(system.getStatus())) {
            throw new DomainException("SYSTEM_NOT_FOUND", "系统不存在或已停用", HttpStatus.NOT_FOUND);
        }
        SystemMember member = first(memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE")));
        if (member == null) {
            throw new DomainException("SYSTEM_ACCESS_DENIED", "没有该系统的有效成员身份", HttpStatus.FORBIDDEN);
        }
        List<Long> tenantIds = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, systemId)
                        .eq(SystemTenantMember::getSystemMemberId, member.getId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE")).stream()
                .map(SystemTenantMember::getTenantId).distinct().toList();
        if (tenantIds.isEmpty()) return List.of();
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, systemId)
                        .eq(SystemTenant::getStatus, "ACTIVE")
                        .in(SystemTenant::getId, tenantIds)).stream()
                .filter(tenant -> "MULTI".equals(system.getTenantMode()) || Boolean.TRUE.equals(tenant.getMain()))
                .sorted(java.util.Comparator.comparing((SystemTenant tenant) -> Boolean.TRUE.equals(tenant.getMain()))
                        .reversed().thenComparing(SystemTenant::getId))
                .map(tenant -> new AccessibleTenant(tenant.getId(), tenant.getCode(), tenant.getName(),
                        Boolean.TRUE.equals(tenant.getMain())))
                .toList();
    }

    /** 默认进入实际获权空间：主空间优先，多空间系统再选最早的有效成员空间。 */
    private SystemTenant defaultTenant(SystemDefinition system, SystemMember member) {
        List<Long> tenantIds = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, system.getId())
                        .eq(SystemTenantMember::getSystemMemberId, member.getId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE")).stream()
                .map(SystemTenantMember::getTenantId).toList();
        if (tenantIds.isEmpty()) return null;
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, system.getId()).eq(SystemTenant::getStatus, "ACTIVE")
                        .in(SystemTenant::getId, tenantIds)).stream()
                .filter(tenant -> "MULTI".equals(system.getTenantMode()) || Boolean.TRUE.equals(tenant.getMain()))
                .sorted(java.util.Comparator.comparing((SystemTenant tenant) -> Boolean.TRUE.equals(tenant.getMain()))
                        .reversed().thenComparing(SystemTenant::getId))
                .findFirst().orElse(null);
    }

    private void deny(Long accountId, Long systemId, String traceId) {
        auditRecorder.record(traceId, accountId, systemId, null, null, "SYSTEM_ENTER", "SYSTEM",
                systemId == null ? null : systemId.toString(), "SYSTEM_ACCESS_DENIED",
                Map.of("reason", "NO_ACTIVE_SYSTEM_MEMBERSHIP"));
        recordSsoSystemOutcome(traceId, accountId, systemId, null, null, null, "SYSTEM_ACCESS_DENIED");
        throw new DomainException("SYSTEM_ACCESS_DENIED", "没有该系统的有效成员身份", HttpStatus.FORBIDDEN);
    }

    /** 以服务端平台会话主键找到企业登录来源，再记录这次真实系统成员进入或拒绝。 */
    private void recordSsoSystemOutcome(String traceId, Long accountId, Long systemId, Long tenantId,
            Long memberId, Long tenantMemberId, String resultCode) {
        AuthenticatedContext context = AuthenticationContextHolder.currentOrNull();
        if (context == null || context.sessionId() == null || !accountId.equals(context.accountId())) return;
        AuditEvent origin = first(auditService.selectList(Wrappers.<AuditEvent>lambdaQuery()
                .eq(AuditEvent::getEventCode, "SSO_LOGIN")
                .eq(AuditEvent::getObjectType, "AUTH_SESSION")
                .eq(AuditEvent::getObjectId, context.sessionId().toString())
                .eq(AuditEvent::getActorAccountId, accountId)
                .eq(AuditEvent::getResultCode, "SUCCESS")
                .orderByDesc(AuditEvent::getId).last("LIMIT 1")));
        if (origin == null) return; // 本地登录或旧版企业会话不能被误报为当前企业登录。
        JsonNode source;
        try {
            source = objectMapper.readTree(origin.getDetailJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("企业登录审计来源无法读取", exception);
        }
        if (!context.sessionId().toString().equals(source.path("platformSessionId").asText())) {
            throw new IllegalStateException("企业登录审计来源与平台会话不一致");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("identityProvider", source.path("identityProvider").asText());
        detail.put("externalUserId", source.path("externalUserId").asText());
        detail.put("accountBinding", "MATCHED");
        detail.put("systemMemberMapping", "SUCCESS".equals(resultCode) ? "MATCHED" : "DENIED");
        detail.put("systemMemberId", memberId);
        detail.put("tenantMemberId", tenantMemberId);
        detail.put("requestedTenantId", tenantId);
        detail.put("authenticationMethod", source.path("authenticationMethod").asText());
        detail.put("mfa", context.mfaLevel());
        detail.put("loginTraceId", origin.getTraceId());
        detail.put("loginRequestId", origin.getRequestId());
        detail.put("requestId", traceId);
        detail.put("ip", source.path("ip").asText());
        detail.put("device", source.path("device").asText());
        detail.put("failureReason", "SUCCESS".equals(resultCode) ? "NONE" : resultCode);
        // 拒绝时目标租户尚非已授权上下文，作为系统级安全事件记录，并在详情保留目标标识。
        Long auditedTenantId = "SUCCESS".equals(resultCode) ? tenantId : null;
        auditRecorder.record(traceId, accountId, systemId, auditedTenantId, memberId,
                "SSO_LOGIN_SYSTEM_ENTER", "AUTH_SESSION", context.sessionId().toString(), resultCode, detail);
    }

    private <T> T first(List<T> values) {
        return values.isEmpty() ? null : values.getFirst();
    }
}
