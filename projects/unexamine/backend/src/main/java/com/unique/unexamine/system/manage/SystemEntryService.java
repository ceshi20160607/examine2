package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    public SystemEntryService(
            SystemDefinitionBaseService systemService,
            SystemMemberBaseService memberService,
            SystemTenantBaseService tenantService,
            SystemTenantMemberBaseService tenantMemberService,
            PermissionResolver permissionResolver,
            AuthenticationService authenticationService,
            AuditRecorder auditRecorder) {
        this.systemService = systemService;
        this.memberService = memberService;
        this.tenantService = tenantService;
        this.tenantMemberService = tenantMemberService;
        this.permissionResolver = permissionResolver;
        this.authenticationService = authenticationService;
        this.auditRecorder = auditRecorder;
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
            throw new DomainException("SYSTEM_CONFIGURATION_INVALID", "系统缺少可用的默认主租户", HttpStatus.CONFLICT);
        }
        return enterResolved(accountId, system, tenant, previousSystemId, previousTenantId, traceId);
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
            SystemTenant tenant = first(tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                    .eq(SystemTenant::getSystemId, system.getId())
                    .eq(SystemTenant::getMain, true)));
            result.add(new AccessibleSystem(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                    system.getStatus(), tenant == null ? null : tenant.getId(), tenant == null ? null : tenant.getName()));
        }
        return result.stream().sorted(java.util.Comparator.comparing(AccessibleSystem::systemId).reversed()).toList();
    }

    private void deny(Long accountId, Long systemId, String traceId) {
        auditRecorder.record(traceId, accountId, systemId, null, null, "SYSTEM_ENTER", "SYSTEM",
                systemId == null ? null : systemId.toString(), "SYSTEM_ACCESS_DENIED",
                Map.of("reason", "NO_ACTIVE_SYSTEM_MEMBERSHIP"));
        throw new DomainException("SYSTEM_ACCESS_DENIED", "没有该系统的有效成员身份", HttpStatus.FORBIDDEN);
    }

    private <T> T first(List<T> values) {
        return values.isEmpty() ? null : values.getFirst();
    }
}
