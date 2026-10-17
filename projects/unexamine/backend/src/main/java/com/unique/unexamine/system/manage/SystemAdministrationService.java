package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.authentication.base.entity.AuthenticationSession;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.foundation.base.entity.CoreCacheEpoch;
import com.unique.unexamine.foundation.base.service.CoreCacheEpochBaseService;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.authentication.base.service.AuthenticationSessionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.TenantMemberQuotaService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 系统基础设置、租户入口与当前所属人的管理操作。 */
@Service
public class SystemAdministrationService {
    private final SystemDefinitionBaseService systemService;
    private final SystemDepartmentBaseService departmentService;
    private final SystemMemberBaseService memberService;
    private final PlatformAccountBaseService accountService;
    private final AuthenticationSessionBaseService sessionService;
    private final SystemTenantBaseService tenantService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final TenantMemberQuotaService tenantMemberQuotaService;
    private final SystemRoleBaseService roleService;
    private final SystemRolePermissionBaseService permissionService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final CoreCacheEpochBaseService cacheEpochService;
    private final AuditRecorder auditRecorder;
    /** 复用主租户的默认组织初始化，新增租户无需手工补部门才能接纳成员。 */
    private final SystemBootstrapService bootstrapService;

    public SystemAdministrationService(
            SystemDefinitionBaseService systemService,
            SystemDepartmentBaseService departmentService,
            SystemMemberBaseService memberService,
            PlatformAccountBaseService accountService,
            AuthenticationSessionBaseService sessionService,
            SystemTenantBaseService tenantService,
            SystemTenantMemberBaseService tenantMemberService,
            TenantMemberQuotaService tenantMemberQuotaService,
            SystemRoleBaseService roleService,
            SystemRolePermissionBaseService permissionService,
            SystemMemberRoleBaseService memberRoleService,
            CoreCacheEpochBaseService cacheEpochService,
            AuditRecorder auditRecorder,
            SystemBootstrapService bootstrapService) {
        this.systemService = systemService;
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.accountService = accountService;
        this.sessionService = sessionService;
        this.tenantService = tenantService;
        this.tenantMemberService = tenantMemberService;
        this.tenantMemberQuotaService = tenantMemberQuotaService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.memberRoleService = memberRoleService;
        this.cacheEpochService = cacheEpochService;
        this.auditRecorder = auditRecorder;
        this.bootstrapService = bootstrapService;
    }

    @Transactional(readOnly = true)
    public SystemSettingsView settings(AuthenticatedContext context) {
        SystemDefinition system = requireSystem(context);
        return view(system);
    }

    /** 仅当前所属人可按关键词选择有效平台账号作为受让人。 */
    @Transactional(readOnly = true)
    public List<SystemOwnerCandidateView> ownerCandidates(AuthenticatedContext context, String query) {
        SystemDefinition system = requireSystem(context);
        requireOwner(context, system);
        if (query == null || query.strip().length() < 2) return List.of();
        String keyword = query.strip();
        return accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                .eq(PlatformAccount::getStatus, "ACTIVE")
                .ne(PlatformAccount::getId, context.accountId())
                .and(condition -> condition.like(PlatformAccount::getUsername, keyword)
                        .or().like(PlatformAccount::getDisplayName, keyword))
                .orderByAsc(PlatformAccount::getId).last("LIMIT 20"))
                .stream().map(account -> new SystemOwnerCandidateView(account.getId(), account.getUsername(),
                        account.getDisplayName())).toList();
    }

    /** 当前所属人转让系统；受让人在各租户拥有有效成员映射，才能承担缺省负责人。 */
    @Transactional
    public SystemSettingsView transferOwner(AuthenticatedContext context, TransferSystemOwnerRequest request,
            String traceId) {
        SystemDefinition system = requireSystem(context);
        requireOwner(context, system);
        if (!request.expectedVersion().equals(system.getVersion())) throw concurrentModification();
        if (context.accountId().equals(request.accountId())) {
            throw new DomainException("OWNER_UNCHANGED", "请选择另一位平台用户", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        PlatformAccount recipient = accountService.selectById(request.accountId());
        if (recipient == null || !"ACTIVE".equals(recipient.getStatus())) {
            throw new DomainException("OWNER_ACCOUNT_INVALID", "受让账号不存在或已停用", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        SystemMember recipientMember = ensureSystemMember(system.getId(), recipient);
        for (SystemTenant tenant : tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, system.getId()))) {
            ensureTenantMember(system.getId(), tenant, recipientMember);
        }
        Long previousOwner = system.getCreatorAccountId();
        system.setCreatorAccountId(recipient.getId());
        if (systemService.updateById(system) != 1) throw concurrentModification();
        int revoked = revokeSystemSessions(system.getId(), List.of(previousOwner, recipient.getId()));
        for (SystemTenant tenant : tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, system.getId()))) {
            bumpCache(system.getId(), tenant.getId(), "AUTHORIZATION");
        }
        auditRecorder.record(traceId, context.accountId(), system.getId(), context.tenantId(), context.memberId(),
                "SYSTEM_OWNER_TRANSFERRED", "SYSTEM", system.getId().toString(), "SUCCESS",
                Map.of("previousOwnerAccountId", previousOwner, "newOwnerAccountId", recipient.getId(),
                        "revokedSessions", revoked, "previousPermissionSnapshot", context.permissions(),
                        "affectedTenantIds", tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                                .eq(SystemTenant::getSystemId, system.getId())).stream().map(SystemTenant::getId).toList()));
        return view(systemService.selectById(system.getId()));
    }

    @Transactional
    public SystemSettingsView updateSettings(
            AuthenticatedContext context,
            UpdateSystemSettingsRequest request,
            String traceId) {
        SystemDefinition system = requireSystem(context);
        String previousName = system.getName();
        String previousMode = system.getTenantMode();
        boolean modeChanged = !previousMode.equals(request.tenantMode());
        int revokedSessions = 0;
        if (modeChanged && "SINGLE".equals(request.tenantMode())) {
            SystemTenant main = tenantService.selectOne(Wrappers.<SystemTenant>lambdaQuery()
                    .eq(SystemTenant::getSystemId, context.systemId())
                    .eq(SystemTenant::getMain, true));
            if (main == null || !"ACTIVE".equals(main.getStatus())
                    || !main.getId().equals(context.tenantId())) {
                throw new DomainException("MAIN_TENANT_REQUIRED",
                        "请从已启用的根租户后台关闭多租户", HttpStatus.CONFLICT);
            }
            // 关闭仅撤销非主租户会话；租户、共享及业务数据的原状态保留，再次开启即恢复访问。
            for (SystemTenant tenant : tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                    .eq(SystemTenant::getSystemId, context.systemId())
                    .eq(SystemTenant::getMain, false))) {
                for (AuthenticationSession session : sessionService.selectList(
                        Wrappers.<AuthenticationSession>lambdaQuery()
                                .eq(AuthenticationSession::getSystemId, context.systemId())
                                .eq(AuthenticationSession::getTenantId, tenant.getId())
                                .eq(AuthenticationSession::getRevoked, false))) {
                    session.setRevoked(true);
                    revokedSessions += sessionService.updateById(session);
                }
                bumpCache(context.systemId(), tenant.getId(), "AUTHORIZATION");
            }
        }
        system.setName(request.name().strip());
        system.setTenantMode(request.tenantMode());
        if (systemService.updateById(system) != 1) {
            throw concurrentModification();
        }
        if (modeChanged) {
            bumpCache(context.systemId(), context.tenantId(), "AUTHORIZATION");
            bumpCache(context.systemId(), context.tenantId(), "MODULE_CATALOG");
            bumpCache(context.systemId(), context.tenantId(), "SYSTEM_SHELL");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_SETTINGS_UPDATED", "SYSTEM", context.systemId().toString(), "SUCCESS",
                Map.of("previousName", previousName, "name", system.getName(),
                        "previousTenantMode", previousMode, "tenantMode", system.getTenantMode(),
                        "revokedSessions", revokedSessions));
        return view(systemService.selectById(context.systemId()));
    }

    @Transactional(readOnly = true)
    public List<TenantView> tenants(AuthenticatedContext context) {
        SystemDefinition system = requireSystem(context);
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId()))
                .stream()
                .filter(tenant -> "MULTI".equals(system.getTenantMode()) || Boolean.TRUE.equals(tenant.getMain()))
                .sorted(Comparator.comparing(SystemTenant::getMain).reversed()
                        .thenComparing(SystemTenant::getId))
                .map(tenant -> view(tenant, context.tenantId()))
                .toList();
    }

    @Transactional
    public TenantView createTenant(AuthenticatedContext context, CreateTenantRequest request, String traceId) {
        SystemDefinition system = requireSystem(context);
        if (!"MULTI".equals(system.getTenantMode())) {
            throw new DomainException("MULTI_TENANT_NOT_ENABLED", "请先启用多租户模式", HttpStatus.CONFLICT);
        }
        String code = request.code().strip().toLowerCase(Locale.ROOT);
        SystemTenant tenant = new SystemTenant();
        tenant.setSystemId(context.systemId());
        tenant.setCode(code);
        tenant.setName(request.name().strip());
        tenant.setMain(false);
        tenant.setMainMarker(null);
        tenant.setCreatorAccountId(context.accountId());
        tenant.setStatus("ACTIVE");
        tenantService.insert(tenant);

        var rootDepartment = bootstrapService.initializeRootDepartment(context.systemId(), tenant.getId());

        SystemTenantMember tenantMember = new SystemTenantMember();
        tenantMember.setSystemId(context.systemId());
        tenantMember.setTenantId(tenant.getId());
        tenantMember.setSystemMemberId(context.memberId());
        tenantMember.setDepartmentId(rootDepartment.getId());
        tenantMember.setTenantAdmin(!context.accountId().equals(system.getCreatorAccountId()));
        tenantMember.setStatus("ACTIVE");
        // 新租户的创建人会成为该租户首位有效成员，创建事务中一并校验配额。
        tenantMemberQuotaService.checkBeforeActivate(context.systemId(), tenant.getId());
        tenantMemberService.insert(tenantMember);

        // 新租户必须同时包含系统当前所属人，供未指定负责人的业务记录兜底。
        if ("APPLICATION".equals(context.mfaLevel()) || !context.accountId().equals(system.getCreatorAccountId())) {
            PlatformAccount ownerAccount = accountService.selectById(system.getCreatorAccountId());
            if (ownerAccount == null || !"ACTIVE".equals(ownerAccount.getStatus())) {
                throw new DomainException("SYSTEM_OWNER_INVALID", "系统所属账号已停用，请先转让系统", HttpStatus.CONFLICT);
            }
            ensureTenantMember(system.getId(), tenant, ensureSystemMember(system.getId(), ownerAccount));
        }

        SystemRole role = new SystemRole();
        role.setSystemId(context.systemId());
        role.setTenantId(tenant.getId());
        role.setCode("TENANT_SUPER_ADMIN");
        role.setName("租户超级管理员");
        role.setStatus("ACTIVE");
        roleService.insert(role);

        SystemRolePermission permission = new SystemRolePermission();
        permission.setSystemId(context.systemId());
        permission.setTenantId(tenant.getId());
        permission.setRoleId(role.getId());
        permission.setResourceType("*");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permission.setDataScopeType("ALL");
        permissionService.insert(permission);

        if (!context.accountId().equals(system.getCreatorAccountId())) {
            SystemMemberRole memberRole = new SystemMemberRole();
            memberRole.setTenantId(tenant.getId());
            memberRole.setTenantMemberId(tenantMember.getId());
            memberRole.setRoleId(role.getId());
            memberRoleService.insert(memberRole);
        }

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_CREATED", "TENANT", tenant.getId().toString(), "SUCCESS",
                Map.of("code", tenant.getCode(), "name", tenant.getName(), "creatorGrantedAdmin",
                        !context.accountId().equals(system.getCreatorAccountId()),
                        "rootDepartmentId", rootDepartment.getId()));
        // 返回数据库写入后的版本，后续停用、归档和恢复必须使用该版本防止并发覆盖。
        return view(tenantService.selectById(tenant.getId()), context.tenantId());
    }

    @Transactional
    public TenantView updateTenantStatus(
            AuthenticatedContext context,
            Long tenantId,
            UpdateTenantStatusRequest request,
            String traceId) {
        requireSystem(context);
        SystemTenant tenant = requireTenant(context.systemId(), tenantId);
        if (Boolean.TRUE.equals(tenant.getMain()) && !"ACTIVE".equals(request.status())) {
            throw new DomainException("MAIN_TENANT_PROTECTED", "默认主租户不能停用或归档", HttpStatus.CONFLICT);
        }
        String previousStatus = tenant.getStatus();
        if (request.expectedVersion() != null && !request.expectedVersion().equals(tenant.getVersion())) {
            throw new DomainException("TENANT_VERSION_CONFLICT", "工作空间状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (previousStatus.equals(request.status())) return view(tenant, context.tenantId());
        // 归档只能从已停用空间发起；恢复可直接启用，原成员、配置和业务记录保持原租户归属。
        if (("ARCHIVED".equals(request.status()) && !"DISABLED".equals(previousStatus))
                || ("ARCHIVED".equals(previousStatus) && !"ACTIVE".equals(request.status()))) {
            throw new DomainException("TENANT_STATUS_TRANSITION_INVALID",
                    "请先停用工作空间再归档；归档后只能恢复启用", HttpStatus.CONFLICT);
        }
        tenant.setStatus(request.status());
        if (tenantService.updateById(tenant) != 1) {
            throw new DomainException("CONCURRENT_MODIFICATION", "租户已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        int revokedSessions = 0;
        if (!"ACTIVE".equals(tenant.getStatus())) {
            List<AuthenticationSession> sessions = sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                    .eq(AuthenticationSession::getSystemId, context.systemId())
                    .eq(AuthenticationSession::getTenantId, tenantId)
                    .eq(AuthenticationSession::getRevoked, false));
            for (AuthenticationSession session : sessions) {
                session.setRevoked(true);
                revokedSessions += sessionService.updateById(session);
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_STATUS_UPDATED", "TENANT", tenantId.toString(), "SUCCESS",
                Map.of("previousStatus", previousStatus, "status", tenant.getStatus(),
                        "revokedSessions", revokedSessions));
        return view(tenantService.selectById(tenantId), context.tenantId());
    }

    /** 登录展示只改变当前系统租户的公开文案，不影响成员身份和业务模块目录。 */
    @Transactional
    public TenantView updateTenantLoginPresentation(AuthenticatedContext context, Long tenantId,
            UpdateTenantLoginPresentationRequest request, String traceId) {
        requireSystem(context);
        SystemTenant tenant = requireTenant(context.systemId(), tenantId);
        if (!request.expectedVersion().equals(tenant.getVersion())) {
            throw new DomainException("TENANT_VERSION_CONFLICT", "工作空间设置已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        String loginTitle = blankToNull(request.loginTitle());
        String loginSubtitle = blankToNull(request.loginSubtitle());
        // Base 的非空更新不会清除字段；明确 SET NULL 才能恢复默认登录文案。
        if (tenantService.update(Wrappers.<SystemTenant>lambdaUpdate()
                .eq(SystemTenant::getId, tenantId).eq(SystemTenant::getVersion, request.expectedVersion())
                .set(SystemTenant::getLoginTitle, loginTitle)
                .set(SystemTenant::getLoginSubtitle, loginSubtitle)
                .set(SystemTenant::getVersion, request.expectedVersion() + 1)) != 1) {
            throw new DomainException("CONCURRENT_MODIFICATION", "工作空间设置已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_LOGIN_PRESENTATION_UPDATED", "TENANT", tenantId.toString(), "SUCCESS",
                Map.of("loginTitle", loginTitle == null ? "" : loginTitle,
                        "loginSubtitle", loginSubtitle == null ? "" : loginSubtitle));
        return view(tenantService.selectById(tenantId), context.tenantId());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void requireOwner(AuthenticatedContext context, SystemDefinition system) {
        if (!context.accountId().equals(system.getCreatorAccountId())) {
            throw new DomainException("SYSTEM_OWNER_REQUIRED", "只有当前系统所属人可转让系统", HttpStatus.FORBIDDEN);
        }
    }

    private SystemMember ensureSystemMember(Long systemId, PlatformAccount account) {
        SystemMember member = memberService.selectOne(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, account.getId()));
        if (member == null) {
            member = new SystemMember();
            member.setSystemId(systemId);
            member.setAccountId(account.getId());
            member.setDisplayName(account.getDisplayName());
            member.setStatus("ACTIVE");
            memberService.insert(member);
        } else if (!"ACTIVE".equals(member.getStatus())) {
            member.setStatus("ACTIVE");
            if (memberService.updateById(member) != 1) throw concurrentModification();
        }
        return member;
    }

    private SystemTenantMember ensureTenantMember(Long systemId, SystemTenant tenant, SystemMember member) {
        SystemTenantMember mapping = tenantMemberService.selectOne(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, systemId)
                .eq(SystemTenantMember::getTenantId, tenant.getId())
                .eq(SystemTenantMember::getSystemMemberId, member.getId()));
        if (mapping == null) {
            SystemDepartment root = departmentService.selectOne(Wrappers.<SystemDepartment>lambdaQuery()
                    .eq(SystemDepartment::getSystemId, systemId)
                    .eq(SystemDepartment::getTenantId, tenant.getId())
                    .eq(SystemDepartment::getCode, "root"));
            if (root == null) throw new DomainException("TENANT_ROOT_MISSING", "租户缺少根部门", HttpStatus.CONFLICT);
            // 当前所属人是每个租户的缺省负责人；归属转让不得受普通成员配额阻断。
            mapping = new SystemTenantMember();
            mapping.setSystemId(systemId);
            mapping.setTenantId(tenant.getId());
            mapping.setSystemMemberId(member.getId());
            mapping.setDepartmentId(root.getId());
            mapping.setTenantAdmin(false);
            mapping.setStatus("ACTIVE");
            tenantMemberService.insert(mapping);
        } else if (!"ACTIVE".equals(mapping.getStatus())) {
            mapping.setStatus("ACTIVE");
            if (tenantMemberService.updateById(mapping) != 1) throw concurrentModification();
        }
        return mapping;
    }

    private int revokeSystemSessions(Long systemId, List<Long> accountIds) {
        int revoked = 0;
        for (AuthenticationSession session : sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                .eq(AuthenticationSession::getSystemId, systemId)
                .in(AuthenticationSession::getAccountId, accountIds)
                .eq(AuthenticationSession::getRevoked, false))) {
            session.setRevoked(true);
            revoked += sessionService.updateById(session);
        }
        return revoked;
    }

    /** 模式变化后让权限、模块目录及运行壳下一次请求读取最新租户范围。 */
    private void bumpCache(Long systemId, Long tenantId, String namespace) {
        String key = "system:" + systemId + ":tenant:" + tenantId;
        CoreCacheEpoch epoch = cacheEpochService.selectOne(Wrappers.<CoreCacheEpoch>lambdaQuery()
                .eq(CoreCacheEpoch::getContextKey, key)
                .eq(CoreCacheEpoch::getCacheNamespace, namespace));
        if (epoch == null) {
            epoch = new CoreCacheEpoch();
            epoch.setContextKey(key);
            epoch.setCacheNamespace(namespace);
            epoch.setEpochValue(1L);
            epoch.setReason("tenant-mode-setting");
            cacheEpochService.insert(epoch);
        } else {
            epoch.setEpochValue(epoch.getEpochValue() + 1);
            epoch.setReason("tenant-mode-setting");
            if (cacheEpochService.updateById(epoch) != 1) throw concurrentModification();
        }
    }

    private DomainException concurrentModification() {
        return new DomainException("CONCURRENT_MODIFICATION", "系统设置已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
    }

    private SystemDefinition requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.BAD_REQUEST);
        }
        SystemDefinition system = systemService.selectById(context.systemId());
        if (system == null || Boolean.TRUE.equals(system.getDeleted())) {
            throw new DomainException("SYSTEM_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND);
        }
        return system;
    }

    private SystemTenant requireTenant(Long systemId, Long tenantId) {
        SystemTenant tenant = tenantService.selectById(tenantId);
        if (tenant == null || !systemId.equals(tenant.getSystemId())) {
            throw new DomainException("TENANT_NOT_FOUND", "租户不存在", HttpStatus.NOT_FOUND);
        }
        return tenant;
    }

    private SystemSettingsView view(SystemDefinition system) {
        return new SystemSettingsView(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                system.getCreatorAccountId(), system.getStatus(), system.getVersion());
    }

    private TenantView view(SystemTenant tenant, Long currentTenantId) {
        return new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getLoginTitle(),
                tenant.getLoginSubtitle(), Boolean.TRUE.equals(tenant.getMain()),
                tenant.getStatus(), tenant.getId().equals(currentTenantId), tenant.getVersion());
    }
}
