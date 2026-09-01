package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.platform.base.entity.PlatSsoIdentity;
import com.unique.unexamine.platform.base.entity.PlatSsoProvider;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatSsoIdentityBaseService;
import com.unique.unexamine.platform.base.service.PlatSsoProviderBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SysAccessRequest;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SysAccessRequestBaseService;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AccessRequestService {
    private final SysAccessRequestBaseService requestService;
    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemRoleBaseService roleService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final PlatformAccountBaseService accountService;
    private final PlatSsoIdentityBaseService ssoIdentityService;
    private final PlatSsoProviderBaseService ssoProviderService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AccessRequestService(
            SysAccessRequestBaseService requestService,
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemRoleBaseService roleService,
            SystemMemberRoleBaseService memberRoleService,
            PlatformAccountBaseService accountService,
            PlatSsoIdentityBaseService ssoIdentityService,
            PlatSsoProviderBaseService ssoProviderService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.requestService = requestService;
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.roleService = roleService;
        this.memberRoleService = memberRoleService;
        this.accountService = accountService;
        this.ssoIdentityService = ssoIdentityService;
        this.ssoProviderService = ssoProviderService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SystemDirectoryItem> directory(AuthenticatedContext context) {
        List<SystemDefinition> systems = systemService.selectList(Wrappers.<SystemDefinition>lambdaQuery()
                .eq(SystemDefinition::getStatus, "ACTIVE")
                .eq(SystemDefinition::getDeleted, false)
                .orderByAsc(SystemDefinition::getId));
        return systems.stream().map(system -> directoryItem(context.accountId(), system)).toList();
    }

    @Transactional
    public AccessRequestView submit(
            AuthenticatedContext context,
            Long systemId,
            SubmitAccessRequest input,
            String traceId) {
        SystemDefinition system = requireSystem(systemId);
        SystemTenant tenant = input.tenantId() == null
                ? requireMainTenant(systemId)
                : requireTenant(systemId, input.tenantId());
        if (hasActiveMembership(context.accountId(), systemId, tenant.getId())) {
            throw new DomainException("SYSTEM_MEMBERSHIP_EXISTS", "当前账号已经可以进入该系统", HttpStatus.CONFLICT);
        }
        List<SysAccessRequest> pending = requestService.selectList(Wrappers.<SysAccessRequest>lambdaQuery()
                .eq(SysAccessRequest::getSystemId, systemId)
                .eq(SysAccessRequest::getTenantId, tenant.getId())
                .eq(SysAccessRequest::getAccountId, context.accountId())
                .eq(SysAccessRequest::getStatus, "PENDING"));
        if (!pending.isEmpty()) {
            SysAccessRequest existing = pending.getFirst();
            existing.setRequestReason(input.reason().strip());
            existing.setRequestedRole(blankToNull(input.requestedRole()));
            if (requestService.updateById(existing) != 1) {
                throw conflict();
            }
            auditRecorder.record(traceId, context.accountId(), systemId, tenant.getId(), null,
                    "SYSTEM_ACCESS_REQUEST_MERGED", "SYSTEM_ACCESS_REQUEST", existing.getId().toString(), "SUCCESS",
                    Map.of("originalTraceId", existing.getRequestTraceId()));
            return view(requestService.selectById(existing.getId()));
        }

        IdentityReference identity = identity(context.accountId());
        SysAccessRequest request = new SysAccessRequest();
        request.setSystemId(system.getId());
        request.setTenantId(tenant.getId());
        request.setAccountId(context.accountId());
        request.setIdentityProvider(identity.provider());
        request.setExternalUserId(identity.externalUserId());
        request.setRequestReason(input.reason().strip());
        request.setRequestedRole(blankToNull(input.requestedRole()));
        request.setStatus("PENDING");
        request.setRequestTraceId(traceId);
        requestService.insert(request);
        auditRecorder.record(traceId, context.accountId(), systemId, tenant.getId(), null,
                "SYSTEM_ACCESS_REQUEST_SUBMITTED", "SYSTEM_ACCESS_REQUEST", request.getId().toString(), "SUCCESS",
                Map.of("identityProvider", identity.provider(), "requestedRole",
                        request.getRequestedRole() == null ? "" : request.getRequestedRole()));
        return view(requestService.selectById(request.getId()));
    }

    @Transactional(readOnly = true)
    public List<AccessRequestView> mine(AuthenticatedContext context) {
        return requestService.selectList(Wrappers.<SysAccessRequest>lambdaQuery()
                        .eq(SysAccessRequest::getAccountId, context.accountId())
                        .orderByDesc(SysAccessRequest::getCreatedAt, SysAccessRequest::getId))
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<AccessRequestView> queue(AuthenticatedContext context, String status) {
        requireSystemContext(context);
        var query = Wrappers.<SysAccessRequest>lambdaQuery()
                .eq(SysAccessRequest::getSystemId, context.systemId())
                .eq(SysAccessRequest::getTenantId, context.tenantId());
        if (status != null && !status.isBlank()) {
            query.eq(SysAccessRequest::getStatus, status.strip().toUpperCase());
        }
        query.orderByAsc(SysAccessRequest::getCreatedAt, SysAccessRequest::getId);
        return requestService.selectList(query).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<SystemRoleOption> roles(AuthenticatedContext context) {
        requireSystemContext(context);
        return roleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                        .eq(SystemRole::getSystemId, context.systemId())
                        .eq(SystemRole::getTenantId, context.tenantId())
                        .eq(SystemRole::getStatus, "ACTIVE")
                        .orderByAsc(SystemRole::getName, SystemRole::getId))
                .stream().map(role -> new SystemRoleOption(role.getId(), role.getCode(), role.getName())).toList();
    }

    @Transactional
    public AccessRequestView decide(
            AuthenticatedContext context,
            Long requestId,
            DecideAccessRequest input,
            String traceId) {
        requireSystemContext(context);
        SysAccessRequest request = requestService.selectById(requestId);
        if (request == null || !context.systemId().equals(request.getSystemId())
                || !context.tenantId().equals(request.getTenantId())) {
            throw new DomainException("ACCESS_REQUEST_NOT_FOUND", "访问申请不存在", HttpStatus.NOT_FOUND);
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new DomainException("ACCESS_REQUEST_ALREADY_DECIDED", "访问申请已经处理", HttpStatus.CONFLICT);
        }
        if (!input.version().equals(request.getVersion())) {
            throw conflict();
        }

        List<Long> roleIds = normalizedRoleIds(input.roleIds());
        String status;
        Map<String, Object> scope = Map.of();
        if ("APPROVE".equals(input.decision())) {
            if (roleIds.isEmpty()) {
                throw new DomainException("ACCESS_REQUEST_ROLE_REQUIRED", "批准申请时至少选择一个角色", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateRoles(context, roleIds);
            Membership membership = ensureMembership(context, request.getAccountId());
            for (Long roleId : roleIds) {
                if (memberRoleService.selectList(Wrappers.<SystemMemberRole>lambdaQuery()
                        .eq(SystemMemberRole::getTenantId, context.tenantId())
                        .eq(SystemMemberRole::getTenantMemberId, membership.tenantMemberId())
                        .eq(SystemMemberRole::getRoleId, roleId)).isEmpty()) {
                    SystemMemberRole assignment = new SystemMemberRole();
                    assignment.setTenantId(context.tenantId());
                    assignment.setTenantMemberId(membership.tenantMemberId());
                    assignment.setRoleId(roleId);
                    memberRoleService.insert(assignment);
                }
            }
            scope = Map.of("source", "ROLE_PERMISSIONS", "roleIds", roleIds);
            status = "APPROVED";
        } else {
            if (input.comment() == null || input.comment().isBlank()) {
                throw new DomainException("ACCESS_REQUEST_REJECTION_REASON_REQUIRED", "拒绝申请时必须填写原因",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            roleIds = List.of();
            status = "REJECTED";
        }

        request.setStatus(status);
        request.setDecidedByMemberId(context.memberId());
        request.setDecisionComment(blankToNull(input.comment()));
        request.setApprovedRoleIdsJson(toJson(roleIds));
        request.setApprovedDataScopeJson(toJson(scope));
        request.setDecisionTraceId(traceId);
        request.setDecidedAt(LocalDateTime.now());
        if (requestService.updateById(request) != 1) {
            throw conflict();
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_ACCESS_REQUEST_" + status, "SYSTEM_ACCESS_REQUEST", requestId.toString(), "SUCCESS",
                Map.of("applicantAccountId", request.getAccountId(), "roleIds", roleIds,
                        "requestTraceId", request.getRequestTraceId()));
        return view(requestService.selectById(requestId));
    }

    private SystemDirectoryItem directoryItem(Long accountId, SystemDefinition system) {
        SystemTenant tenant = requireMainTenant(system.getId());
        boolean accessible = hasActiveMembership(accountId, system.getId(), tenant.getId());
        SysAccessRequest latest = requestService.selectList(Wrappers.<SysAccessRequest>lambdaQuery()
                        .eq(SysAccessRequest::getSystemId, system.getId())
                        .eq(SysAccessRequest::getTenantId, tenant.getId())
                        .eq(SysAccessRequest::getAccountId, accountId)
                        .orderByDesc(SysAccessRequest::getCreatedAt, SysAccessRequest::getId))
                .stream().findFirst().orElse(null);
        return new SystemDirectoryItem(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                tenant.getId(), tenant.getName(), accessible, latest == null ? null : latest.getId(),
                latest == null ? null : latest.getStatus());
    }

    private boolean hasActiveMembership(Long accountId, Long systemId, Long tenantId) {
        List<Long> memberIds = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, systemId)
                        .eq(SystemMember::getAccountId, accountId)
                        .eq(SystemMember::getStatus, "ACTIVE"))
                .stream().map(SystemMember::getId).toList();
        return !memberIds.isEmpty() && !tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, systemId)
                .eq(SystemTenantMember::getTenantId, tenantId)
                .in(SystemTenantMember::getSystemMemberId, memberIds)
                .eq(SystemTenantMember::getStatus, "ACTIVE")).isEmpty();
    }

    private Membership ensureMembership(AuthenticatedContext context, Long accountId) {
        SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())
                        .eq(SystemMember::getAccountId, accountId))
                .stream().findFirst().orElse(null);
        if (member == null) {
            PlatformAccount account = accountService.selectById(accountId);
            if (account == null || !"ACTIVE".equals(account.getStatus())) {
                throw new DomainException("ACCESS_REQUEST_ACCOUNT_INVALID", "申请账号不存在或已停用", HttpStatus.CONFLICT);
            }
            member = new SystemMember();
            member.setSystemId(context.systemId());
            member.setAccountId(accountId);
            member.setDisplayName(account.getDisplayName());
            member.setStatus("ACTIVE");
            memberService.insert(member);
        } else if (!"ACTIVE".equals(member.getStatus())) {
            member.setStatus("ACTIVE");
            memberService.updateById(member);
        }
        SystemTenantMember tenantMember = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, member.getId()))
                .stream().findFirst().orElse(null);
        if (tenantMember == null) {
            tenantMember = new SystemTenantMember();
            tenantMember.setSystemId(context.systemId());
            tenantMember.setTenantId(context.tenantId());
            tenantMember.setSystemMemberId(member.getId());
            tenantMember.setTenantAdmin(false);
            tenantMember.setStatus("ACTIVE");
            tenantMemberService.insert(tenantMember);
        } else if (!"ACTIVE".equals(tenantMember.getStatus())) {
            tenantMember.setStatus("ACTIVE");
            tenantMemberService.updateById(tenantMember);
        }
        return new Membership(member.getId(), tenantMember.getId());
    }

    private void validateRoles(AuthenticatedContext context, List<Long> roleIds) {
        List<SystemRole> roles = roleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                .in(SystemRole::getId, roleIds)
                .eq(SystemRole::getSystemId, context.systemId())
                .eq(SystemRole::getTenantId, context.tenantId())
                .eq(SystemRole::getStatus, "ACTIVE"));
        if (roles.size() != roleIds.size()) {
            throw new DomainException("ACCESS_REQUEST_ROLE_INVALID", "所选角色不属于当前系统租户或已停用", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private IdentityReference identity(Long accountId) {
        PlatSsoIdentity identity = ssoIdentityService.selectList(Wrappers.<PlatSsoIdentity>lambdaQuery()
                        .eq(PlatSsoIdentity::getAccountId, accountId)
                        .orderByDesc(PlatSsoIdentity::getLastLoginAt, PlatSsoIdentity::getId))
                .stream().findFirst().orElse(null);
        if (identity == null) {
            return new IdentityReference("LOCAL", null);
        }
        PlatSsoProvider provider = ssoProviderService.selectById(identity.getProviderId());
        return provider == null
                ? new IdentityReference("SSO", identity.getExternalSubject())
                : new IdentityReference(provider.getCode(), identity.getExternalSubject());
    }

    private AccessRequestView view(SysAccessRequest request) {
        SystemDefinition system = systemService.selectById(request.getSystemId());
        SystemTenant tenant = tenantService.selectById(request.getTenantId());
        PlatformAccount account = accountService.selectById(request.getAccountId());
        return new AccessRequestView(request.getId(), request.getSystemId(), system == null ? "" : system.getName(),
                request.getTenantId(), tenant == null ? "" : tenant.getName(), request.getAccountId(),
                account == null ? "" : account.getDisplayName(), request.getIdentityProvider(), request.getExternalUserId(),
                request.getRequestReason(), request.getRequestedRole(), request.getStatus(), request.getDecidedByMemberId(),
                request.getDecisionComment(), readList(request.getApprovedRoleIdsJson()),
                readMap(request.getApprovedDataScopeJson()), request.getRequestTraceId(), request.getDecisionTraceId(),
                request.getCreatedAt(), request.getDecidedAt(), request.getVersion());
    }

    private SystemDefinition requireSystem(Long systemId) {
        SystemDefinition system = systemService.selectById(systemId);
        if (system == null || Boolean.TRUE.equals(system.getDeleted()) || !"ACTIVE".equals(system.getStatus())) {
            throw new DomainException("SYSTEM_NOT_FOUND", "系统不存在或不可申请", HttpStatus.NOT_FOUND);
        }
        return system;
    }

    private SystemTenant requireMainTenant(Long systemId) {
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, systemId)
                        .eq(SystemTenant::getMain, true)
                        .eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "SYSTEM_CONFIGURATION_INVALID", "系统缺少可用的默认主租户", HttpStatus.CONFLICT));
    }

    private SystemTenant requireTenant(Long systemId, Long tenantId) {
        SystemTenant tenant = tenantService.selectById(tenantId);
        if (tenant == null || !systemId.equals(tenant.getSystemId()) || !"ACTIVE".equals(tenant.getStatus())) {
            throw new DomainException("TENANT_NOT_FOUND", "目标租户不存在或已停用", HttpStatus.NOT_FOUND);
        }
        return tenant;
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入目标系统", HttpStatus.CONFLICT);
        }
    }

    private List<Long> normalizedRoleIds(List<Long> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize access request context", exception);
        }
    }

    private List<Long> readList(String value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read approved roles", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read approved data scope", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private DomainException conflict() {
        return new DomainException("CONCURRENT_MODIFICATION", "访问申请已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
    }

    private record IdentityReference(String provider, String externalUserId) {}
    private record Membership(Long systemMemberId, Long tenantMemberId) {}
}
