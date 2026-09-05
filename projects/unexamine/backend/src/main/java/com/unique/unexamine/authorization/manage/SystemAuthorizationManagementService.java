package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.base.entity.AuthorizationFieldPolicy;
import com.unique.unexamine.authentication.base.entity.AuthorizationPermissionVersion;
import com.unique.unexamine.authentication.base.service.AuthorizationFieldPolicyBaseService;
import com.unique.unexamine.authentication.base.service.AuthorizationPermissionVersionBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreCacheEpoch;
import com.unique.unexamine.foundation.base.service.CoreCacheEpochBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemAuthorizationManagementService {
    private static final Map<String, List<String>> FIXED_RESOURCES = Map.of(
            "CONFIG:SYSTEM", List.of("MANAGE", "MIGRATE_TENANT_MODE"),
            "CONFIG:MODULE", List.of("MANAGE"),
            "AUDIT:EVENT", List.of("VIEW", "VIEW_SENSITIVE", "PURGE"),
            "TODO:SYSTEM", List.of("VIEW", "HANDLE"),
            "MESSAGE:SYSTEM", List.of("VIEW", "SEND", "MANAGE"),
            "AI:SYSTEM", List.of("VIEW", "MANAGE", "PUBLISH"));
    private static final Set<String> DATA_SCOPES = Set.of(
            "ALL", "DEPARTMENT_AND_DESCENDANTS", "DEPARTMENT", "SELF_AND_SUBORDINATES", "SELF", "CUSTOM");
    private static final Set<String> CHANNELS = Set.of("PAGE", "APPLICATION", "FILE", "FLOW");
    private static final Set<String> MODULE_ACTIONS = Set.of(
            "LIST", "DETAIL", "CREATE", "UPDATE", "DELETE", "IMPORT", "EXPORT", "CONVERT", "PRINT");
    private static final Map<String, String> ACTION_NAMES = Map.ofEntries(
            Map.entry("MANAGE", "管理"), Map.entry("MIGRATE_TENANT_MODE", "切换组织模式"),
            Map.entry("VIEW", "查看"), Map.entry("VIEW_SENSITIVE", "查看敏感信息"), Map.entry("PURGE", "清理"),
            Map.entry("LIST", "查看列表"), Map.entry("DETAIL", "查看详情"), Map.entry("CREATE", "新建"),
            Map.entry("UPDATE", "编辑"), Map.entry("DELETE", "删除"), Map.entry("IMPORT", "导入"),
            Map.entry("EXPORT", "导出"), Map.entry("CONVERT", "转化"), Map.entry("PRINT", "打印"),
            Map.entry("ARCHIVE", "归档"), Map.entry("RESTORE", "恢复"), Map.entry("TRANSFER", "转交负责人"),
            Map.entry("SHARE", "共享"));

    private final SystemDepartmentBaseService departmentService;
    private final PlatformAccountBaseService accountService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemRoleBaseService roleService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final SystemRolePermissionBaseService permissionService;
    private final AuthorizationFieldPolicyBaseService fieldPolicyService;
    private final AuthorizationPermissionVersionBaseService versionService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleActionBaseService actionService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final CoreCacheEpochBaseService cacheEpochService;
    private final PermissionChecker permissionChecker;
    private final PermissionResolver permissionResolver;
    private final OrganizationRelationRepository relationRepository;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public SystemAuthorizationManagementService(
            SystemDepartmentBaseService departmentService,
            PlatformAccountBaseService accountService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemRoleBaseService roleService,
            SystemMemberRoleBaseService memberRoleService,
            SystemRolePermissionBaseService permissionService,
            AuthorizationFieldPolicyBaseService fieldPolicyService,
            AuthorizationPermissionVersionBaseService versionService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModuleFieldBaseService fieldService,
            CoreCacheEpochBaseService cacheEpochService,
            PermissionChecker permissionChecker,
            PermissionResolver permissionResolver,
            OrganizationRelationRepository relationRepository,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.departmentService = departmentService;
        this.accountService = accountService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.roleService = roleService;
        this.memberRoleService = memberRoleService;
        this.permissionService = permissionService;
        this.fieldPolicyService = fieldPolicyService;
        this.versionService = versionService;
        this.moduleService = moduleService;
        this.actionService = actionService;
        this.fieldService = fieldService;
        this.cacheEpochService = cacheEpochService;
        this.permissionChecker = permissionChecker;
        this.permissionResolver = permissionResolver;
        this.relationRepository = relationRepository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SystemAuthorizationModels.Overview overview(AuthenticatedContext context) {
        requireContext(context);
        List<SystemDepartment> departments = departments(context);
        List<SystemTenantMember> tenantMembers = tenantMemberService.selectList(
                Wrappers.<SystemTenantMember>lambdaQuery().eq(SystemTenantMember::getTenantId, context.tenantId()));
        Map<Long, SystemMember> members = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())).stream()
                .collect(Collectors.toMap(SystemMember::getId, Function.identity()));
        Map<Long, List<Long>> roleIds = memberRoleService.selectList(Wrappers.<SystemMemberRole>lambdaQuery()
                        .eq(SystemMemberRole::getTenantId, context.tenantId())).stream()
                .collect(Collectors.groupingBy(SystemMemberRole::getTenantMemberId,
                        Collectors.mapping(SystemMemberRole::getRoleId, Collectors.toList())));
        List<SystemAuthorizationModels.MemberView> memberViews = tenantMembers.stream()
                .sorted(Comparator.comparing(SystemTenantMember::getId)).map(item -> memberView(item,
                        members.get(item.getSystemMemberId()), roleIds.getOrDefault(item.getId(), List.of()))).toList();
        return new SystemAuthorizationModels.Overview(
                departments.stream().map(this::departmentView).toList(), memberViews,
                roles(context), resources(context), latestVersion(context.systemId(), context.tenantId()));
    }

    @Transactional
    public SystemAuthorizationModels.DepartmentView saveDepartment(
            AuthenticatedContext context, SystemAuthorizationModels.DepartmentRequest input, String traceId) {
        requireContext(context);
        SystemDepartment department;
        String oldPath = null;
        if (input.id() == null) {
            department = new SystemDepartment();
            department.setSystemId(context.systemId());
            department.setTenantId(context.tenantId());
            department.setStatus("ACTIVE");
        } else {
            department = requireDepartment(context, input.id());
            requireVersion(input.expectedVersion(), department.getVersion());
            oldPath = department.getPathCode();
        }
        SystemDepartment parent = input.parentId() == null ? null : requireDepartment(context, input.parentId());
        if (department.getId() != null && parent != null
                && (parent.getId().equals(department.getId()) || parent.getPathCode().startsWith(department.getPathCode() + "/"))) {
            throw new DomainException("ORGANIZATION_CYCLE", "部门不能移动到自身或下级部门", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        department.setParentId(parent == null ? null : parent.getId());
        department.setCode(input.code() == null || input.code().isBlank()
                ? department.getCode() == null ? stableCode("department") : department.getCode()
                : input.code().strip());
        department.setName(input.name().strip());
        department.setSortOrder(input.sortOrder() == null ? 0 : input.sortOrder());
        department.setPathCode((parent == null ? "" : parent.getPathCode() + "/") + department.getCode());
        try {
            if (department.getId() == null) departmentService.insert(department);
            else if (departmentService.updateById(department) != 1) throw conflict();
        } catch (RuntimeException exception) {
            if (exception instanceof DomainException domainException) throw domainException;
            throw new DomainException("DEPARTMENT_CODE_EXISTS", "当前租户已存在相同部门编码", HttpStatus.CONFLICT);
        }
        if (oldPath != null && !oldPath.equals(department.getPathCode())) {
            for (SystemDepartment child : departments(context)) {
                if (!child.getId().equals(department.getId()) && child.getPathCode().startsWith(oldPath + "/")) {
                    child.setPathCode(department.getPathCode() + child.getPathCode().substring(oldPath.length()));
                    departmentService.updateById(child);
                }
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DEPARTMENT_SAVED", "SYSTEM_DEPARTMENT", department.getId().toString(), "SUCCESS",
                Map.of("code", department.getCode(), "pathCode", department.getPathCode()));
        return departmentView(departmentService.selectById(department.getId()));
    }

    @Transactional
    public SystemAuthorizationModels.MemberView assignMember(
            AuthenticatedContext context, Long tenantMemberId,
            SystemAuthorizationModels.MemberAssignmentRequest input, String traceId) {
        requireContext(context);
        SystemTenantMember tenantMember = requireTenantMember(context, tenantMemberId);
        requireVersion(input.expectedVersion(), tenantMember.getVersion());
        if (input.departmentId() != null) requireDepartment(context, input.departmentId());
        validateReportingLine(context, tenantMemberId, input.managerTenantMemberId());
        List<Long> distinctRoleIds = input.roleIds().stream().distinct().sorted().toList();
        for (Long roleId : distinctRoleIds) requireRole(context, roleId);
        tenantMember.setDepartmentId(input.departmentId());
        if (tenantMemberService.updateById(tenantMember) != 1) throw conflict();
        memberRoleService.selectList(Wrappers.<SystemMemberRole>lambdaQuery()
                        .eq(SystemMemberRole::getTenantMemberId, tenantMemberId))
                .forEach(row -> memberRoleService.deleteById(row.getId()));
        for (Long roleId : distinctRoleIds) {
            SystemMemberRole row = new SystemMemberRole();
            row.setTenantId(context.tenantId());
            row.setTenantMemberId(tenantMemberId);
            row.setRoleId(roleId);
            memberRoleService.insert(row);
        }
        relationRepository.upsert(context.systemId(), context.tenantId(), tenantMemberId,
                input.managerTenantMemberId(), input.positionTitle());
        long permissionVersion = latestVersion(context.systemId(), context.tenantId()) + 1;
        AuthorizationPermissionVersion permissionVersionRow = new AuthorizationPermissionVersion();
        permissionVersionRow.setContextType("SYSTEM");
        permissionVersionRow.setPlatformId(context.platformId());
        permissionVersionRow.setSystemId(context.systemId());
        permissionVersionRow.setTenantId(context.tenantId());
        permissionVersionRow.setVersionNumber(permissionVersion);
        permissionVersionRow.setReason("member-assignment:" + tenantMemberId);
        permissionVersionRow.setChangedByAccountId(context.accountId());
        versionService.insert(permissionVersionRow);
        bumpCache(context.systemId(), context.tenantId(), permissionVersion);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_MEMBER_AUTHORIZATION_ASSIGNED", "SYSTEM_TENANT_MEMBER", tenantMemberId.toString(), "SUCCESS",
                Map.of("departmentId", input.departmentId() == null ? "" : input.departmentId(),
                        "manager", input.managerTenantMemberId() == null ? "" : input.managerTenantMemberId(),
                        "roleIds", distinctRoleIds, "permissionVersion", permissionVersion));
        SystemMember member = memberService.selectById(tenantMember.getSystemMemberId());
        return memberView(tenantMemberService.selectById(tenantMemberId), member, distinctRoleIds);
    }

    @Transactional
    public SystemAuthorizationModels.MemberView addMember(
            AuthenticatedContext context, SystemAuthorizationModels.AddMemberRequest input, String traceId) {
        requireContext(context);
        String accountText = input.account().strip();
        PlatformAccount account = accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                        .and(wrapper -> wrapper.eq(PlatformAccount::getUsername, accountText)
                                .or().eq(PlatformAccount::getEmail, accountText)
                                .or().eq(PlatformAccount::getMobile, accountText)))
                .stream().findFirst().orElseThrow(() -> new DomainException("ACCOUNT_NOT_FOUND",
                        "没有找到该账号，请确认用户名、邮箱或手机号；新用户需先完成注册", HttpStatus.NOT_FOUND));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new DomainException("ACCOUNT_NOT_ACTIVE", "该账号当前不可加入系统", HttpStatus.CONFLICT);
        }
        SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())
                        .eq(SystemMember::getAccountId, account.getId()))
                .stream().findFirst().orElse(null);
        if (member == null) {
            member = new SystemMember();
            member.setSystemId(context.systemId());
            member.setAccountId(account.getId());
            member.setEmployeeNumber(emptyToNull(input.employeeNumber()));
            member.setDisplayName(account.getDisplayName());
            member.setStatus("ACTIVE");
            member.setJoinedAt(LocalDateTime.now());
            memberService.insert(member);
        } else {
            if (input.employeeNumber() != null && !input.employeeNumber().isBlank()) {
                member.setEmployeeNumber(input.employeeNumber().strip());
            }
            member.setDisplayName(account.getDisplayName());
            member.setStatus("ACTIVE");
            memberService.updateById(member);
        }
        SystemTenantMember membership = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, member.getId()))
                .stream().findFirst().orElse(null);
        if (membership != null && "ACTIVE".equals(membership.getStatus())) {
            throw new DomainException("SYSTEM_MEMBER_EXISTS", "该成员已经在当前工作空间中", HttpStatus.CONFLICT);
        }
        if (membership == null) {
            membership = new SystemTenantMember();
            membership.setSystemId(context.systemId());
            membership.setTenantId(context.tenantId());
            membership.setSystemMemberId(member.getId());
            membership.setDepartmentId(input.departmentId() == null ? rootDepartment(context).getId() : input.departmentId());
            membership.setTenantAdmin(false);
            membership.setStatus("ACTIVE");
            tenantMemberService.insert(membership);
        } else {
            membership.setDepartmentId(input.departmentId() == null ? rootDepartment(context).getId() : input.departmentId());
            membership.setStatus("ACTIVE");
            tenantMemberService.updateById(membership);
        }
        membership = tenantMemberService.selectById(membership.getId());
        SystemAuthorizationModels.MemberView saved = assignMember(context, membership.getId(),
                new SystemAuthorizationModels.MemberAssignmentRequest(membership.getDepartmentId(),
                        input.managerTenantMemberId(), input.positionTitle(), input.roleIds(), membership.getVersion()), traceId);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_MEMBER_ADDED", "SYSTEM_TENANT_MEMBER", membership.getId().toString(), "SUCCESS",
                Map.of("account", account.getUsername(), "displayName", account.getDisplayName()));
        return saved;
    }

    @Transactional
    public SystemAuthorizationModels.RoleView saveRole(
            AuthenticatedContext context, SystemAuthorizationModels.SaveRoleRequest input, String traceId) {
        requireContext(context);
        input.permissions().forEach(permission -> validatePermission(context, permission));
        input.fieldPolicies().forEach(policy -> validateFieldPolicy(context, policy));
        SystemRole role;
        if (input.id() == null) {
            role = new SystemRole();
            role.setSystemId(context.systemId());
            role.setTenantId(context.tenantId());
            role.setBuiltIn(false);
        } else {
            role = requireRole(context, input.id());
            requireVersion(input.expectedVersion(), role.getVersion());
            if (Boolean.TRUE.equals(role.getBuiltIn())) {
                throw new DomainException("BUILT_IN_ROLE_IMMUTABLE", "内置角色不能通过普通授权流程修改", HttpStatus.CONFLICT);
            }
        }
        role.setCode(input.code() == null || input.code().isBlank()
                ? role.getCode() == null ? stableCode("role") : role.getCode()
                : input.code().strip());
        role.setName(input.name().strip());
        role.setDescription(input.description());
        role.setStatus("DRAFT");
        try {
            if (role.getId() == null) roleService.insert(role);
            else if (roleService.updateById(role) != 1) throw conflict();
        } catch (RuntimeException exception) {
            if (exception instanceof DomainException domainException) throw domainException;
            throw new DomainException("ROLE_CODE_EXISTS", "当前租户已存在相同角色编码", HttpStatus.CONFLICT);
        }
        replaceRoleConfiguration(context, role.getId(), input.permissions(), input.fieldPolicies());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_AUTHORIZATION_ROLE_SAVED", "SYSTEM_ROLE", role.getId().toString(), "DRAFT",
                Map.of("permissions", input.permissions().size(), "fieldPolicies", input.fieldPolicies().size()));
        return roleView(roleService.selectById(role.getId()));
    }

    @Transactional
    public SystemAuthorizationModels.RoleView publishRole(
            AuthenticatedContext context, Long roleId,
            SystemAuthorizationModels.PublishRoleRequest input, String traceId) {
        requireContext(context);
        SystemRole role = requireRole(context, roleId);
        requireVersion(input.expectedVersion(), role.getVersion());
        List<SystemAuthorizationModels.PermissionInput> permissions = permissionInputs(roleId);
        List<SystemAuthorizationModels.FieldPolicyInput> policies = fieldPolicyInputs(roleId);
        if (permissions.isEmpty()) {
            throw new DomainException("ROLE_PERMISSION_REQUIRED", "角色至少需要一项有效权限", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        permissions.forEach(permission -> validatePermission(context, permission));
        policies.forEach(policy -> validateFieldPolicy(context, policy));
        role.setStatus("ACTIVE");
        if (roleService.updateById(role) != 1) throw conflict();
        long version = latestVersion(context.systemId(), context.tenantId()) + 1;
        AuthorizationPermissionVersion row = new AuthorizationPermissionVersion();
        row.setContextType("SYSTEM");
        row.setPlatformId(context.platformId());
        row.setSystemId(context.systemId());
        row.setTenantId(context.tenantId());
        row.setVersionNumber(version);
        row.setReason(input.reason().strip());
        row.setChangedByAccountId(context.accountId());
        versionService.insert(row);
        bumpCache(context.systemId(), context.tenantId(), version);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_AUTHORIZATION_PUBLISHED", "SYSTEM_ROLE", roleId.toString(), "SUCCESS",
                Map.of("permissionVersion", version, "reason", input.reason()));
        return roleView(roleService.selectById(roleId));
    }

    @Transactional
    public SystemAuthorizationModels.PermissionPreview preview(
            AuthenticatedContext context, SystemAuthorizationModels.PreviewRequest input, String traceId) {
        requireContext(context);
        long currentVersion = latestVersion(context.systemId(), context.tenantId());
        if (input.expectedPermissionVersion() != currentVersion) {
            throw new DomainException("AUTHORIZATION_VERSION_EXPIRED",
                    "授权版本已变化，请刷新后重新预览", HttpStatus.CONFLICT);
        }
        SystemTenantMember target = requireTenantMember(context, input.tenantMemberId());
        SystemMember systemMember = memberService.selectById(target.getSystemMemberId());
        ResolvedPermissions resolved = permissionResolver.resolve(context.systemId(), context.tenantId(), target.getId());
        List<SystemRole> activeRoles = resolved.roleIds().isEmpty() ? List.of() : roleService.selectList(
                Wrappers.<SystemRole>lambdaQuery().eq(SystemRole::getTenantId, context.tenantId())
                        .eq(SystemRole::getStatus, "ACTIVE").in(SystemRole::getId, resolved.roleIds()));
        List<SystemAuthorizationModels.RoleContribution> contributions = activeRoles.stream()
                .sorted(Comparator.comparing(SystemRole::getId))
                .map(role -> new SystemAuthorizationModels.RoleContribution(role.getId(), role.getCode(), role.getName(),
                        permissionInputs(role.getId()).stream().filter(permission -> previewFilter(input,
                                permission.resourceCode(), permission.actionCode())).toList()))
                .toList();

        List<SystemAuthorizationModels.ResourceView> catalog = resources(context).stream()
                .filter(resource -> input.resourceCode() == null || input.resourceCode().isBlank()
                        || input.resourceCode().equals(resource.resourceCode()))
                .toList();
        List<SystemAuthorizationModels.ActionDecision> actionDecisions = new ArrayList<>();
        for (SystemAuthorizationModels.ResourceView resource : catalog) {
            for (String action : resource.actions()) {
                if (input.actionCode() != null && !input.actionCode().isBlank() && !input.actionCode().equals(action)) {
                    continue;
                }
                List<PermissionGrant> matchingGrants = resolved.permissions().stream().filter(candidate ->
                                matches(candidate.resourceType(), resource.resourceType())
                                        && matches(candidate.resourceCode(), resource.resourceCode())
                                        && matches(candidate.actionCode(), action))
                        .toList();
                DataScopeExpression scope = findScope(resolved.dataScopes(), resource.resourceType(),
                        resource.resourceCode(), action);
                boolean allowed = !matchingGrants.isEmpty();
                actionDecisions.add(new SystemAuthorizationModels.ActionDecision(resource.resourceType(),
                        resource.resourceCode(), resource.name(), action, allowed,
                        matchingGrants.stream().flatMap(grant -> grant.roleIds().stream()).distinct().sorted().toList(), scope,
                        allowed ? "至少一个有效角色授予该动作；菜单动作和数据范围按角色并集合并"
                                : "没有有效角色授予该动作，按默认拒绝处理"));
            }
        }

        List<AuthorizationFieldPolicy> allPolicies = resolved.roleIds().isEmpty() ? List.of() : fieldPolicyService.selectList(
                Wrappers.<AuthorizationFieldPolicy>lambdaQuery().eq(AuthorizationFieldPolicy::getContextType, "SYSTEM")
                        .eq(AuthorizationFieldPolicy::getSystemId, context.systemId())
                        .eq(AuthorizationFieldPolicy::getTenantId, context.tenantId())
                        .in(AuthorizationFieldPolicy::getRoleId, resolved.roleIds()));
        List<SystemAuthorizationModels.FieldDecision> fieldDecisions = new ArrayList<>();
        for (SystemAuthorizationModels.ResourceView resource : catalog) {
            if (!"MODULE".equals(resource.resourceType())) continue;
            Set<Long> resourceRoleIds = resolved.permissions().stream()
                    .filter(grant -> matches(grant.resourceType(), "MODULE")
                            && matches(grant.resourceCode(), resource.resourceCode()))
                    .flatMap(grant -> grant.roleIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
            for (String field : resource.fields()) {
                List<AuthorizationFieldPolicy> policies = allPolicies.stream()
                        .filter(policy -> resource.resourceCode().equals(policy.getResourceCode())
                                && field.equals(policy.getFieldCode()) && "PAGE".equals(policy.getChannel())
                                && resourceRoleIds.contains(policy.getRoleId())).toList();
                boolean complete = !resourceRoleIds.isEmpty()
                        && policies.stream().map(AuthorizationFieldPolicy::getRoleId).distinct().count() == resourceRoleIds.size();
                boolean readable = complete && policies.stream().allMatch(policy -> Boolean.TRUE.equals(policy.getReadable()));
                boolean writable = complete && policies.stream().allMatch(policy -> Boolean.TRUE.equals(policy.getWritable()));
                List<String> masks = policies.stream().map(AuthorizationFieldPolicy::getMaskStrategy)
                        .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
                fieldDecisions.add(new SystemAuthorizationModels.FieldDecision(resource.resourceCode(), field, "PAGE",
                        readable, writable, masks, resourceRoleIds.stream().sorted().toList(),
                        complete ? "字段策略在相关角色之间取硬限制交集"
                                : "至少一个相关角色缺少字段策略，按默认拒绝处理"));
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "AUTHORIZATION_PERMISSION_PREVIEW", "SYSTEM_TENANT_MEMBER", target.getId().toString(), "SUCCESS",
                Map.of("permissionVersion", currentVersion,
                        "resourceCode", input.resourceCode() == null ? "" : input.resourceCode(),
                        "actionCode", input.actionCode() == null ? "" : input.actionCode(),
                        "targetRoleIds", resolved.roleIds()));
        return new SystemAuthorizationModels.PermissionPreview(currentVersion,
                memberView(target, systemMember, resolved.roleIds()), contributions, actionDecisions, fieldDecisions,
                Map.of("menuAndAction", "UNION", "dataScope", "UNION",
                        "fieldAndChannelRestrictions", "INTERSECTION", "missingContext", "DEFAULT_DENY"));
    }

    private void replaceRoleConfiguration(AuthenticatedContext context, Long roleId,
                                          List<SystemAuthorizationModels.PermissionInput> permissions,
                                          List<SystemAuthorizationModels.FieldPolicyInput> policies) {
        permissionService.selectList(Wrappers.<SystemRolePermission>lambdaQuery().eq(SystemRolePermission::getRoleId, roleId))
                .forEach(row -> permissionService.deleteById(row.getId()));
        fieldPolicyService.selectList(Wrappers.<AuthorizationFieldPolicy>lambdaQuery()
                        .eq(AuthorizationFieldPolicy::getContextType, "SYSTEM")
                        .eq(AuthorizationFieldPolicy::getRoleId, roleId))
                .forEach(row -> fieldPolicyService.deleteById(row.getId()));
        for (SystemAuthorizationModels.PermissionInput input : permissions) {
            SystemRolePermission row = new SystemRolePermission();
            row.setSystemId(context.systemId());
            row.setTenantId(context.tenantId());
            row.setRoleId(roleId);
            row.setResourceType(input.resourceType());
            row.setResourceCode(input.resourceCode());
            row.setActionCode(input.actionCode());
            row.setDataScopeType(input.dataScopeType());
            row.setDataScopeJson(normalizeJson(input.dataScopeJson()));
            row.setEffect("ALLOW");
            permissionService.insert(row);
        }
        for (SystemAuthorizationModels.FieldPolicyInput input : policies) {
            AuthorizationFieldPolicy row = new AuthorizationFieldPolicy();
            row.setContextType("SYSTEM");
            row.setPlatformId(context.platformId());
            row.setSystemId(context.systemId());
            row.setTenantId(context.tenantId());
            row.setRoleId(roleId);
            row.setResourceCode(input.resourceCode());
            row.setFieldCode(input.fieldCode());
            row.setChannel(input.channel());
            row.setReadable(input.readable());
            row.setWritable(input.writable());
            row.setMaskStrategy(input.maskStrategy());
            fieldPolicyService.insert(row);
        }
    }

    private void validatePermission(AuthenticatedContext context, SystemAuthorizationModels.PermissionInput input) {
        if (!DATA_SCOPES.contains(input.dataScopeType())) {
            throw new DomainException("DATA_SCOPE_INVALID", "数据范围无效：" + input.dataScopeType(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        normalizeJson(input.dataScopeJson());
        String fixedKey = input.resourceType() + ":" + input.resourceCode();
        boolean valid = FIXED_RESOURCES.getOrDefault(fixedKey, List.of()).contains(input.actionCode());
        if ("MODULE".equals(input.resourceType())) {
            ConfiguredModule module = requireModule(context, input.resourceCode());
            Set<String> actions = new LinkedHashSet<>(MODULE_ACTIONS);
            actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                            .eq(ConfiguredModuleAction::getModuleId, module.getId())
                            .eq(ConfiguredModuleAction::getStatus, "ACTIVE"))
                    .forEach(action -> actions.add(action.getCode()));
            valid = actions.contains(input.actionCode());
        }
        if (!valid) {
            throw new DomainException("AUTHORIZATION_REFERENCE_INVALID", "权限引用了不存在的资源或动作",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!permissionChecker.allows(context, input.resourceType(), input.resourceCode(), input.actionCode())) {
            throw new DomainException("AUTHORIZATION_OVERGRANT", "不能授予当前管理员自身不具备的权限",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void validateFieldPolicy(AuthenticatedContext context, SystemAuthorizationModels.FieldPolicyInput input) {
        if (!CHANNELS.contains(input.channel())) {
            throw new DomainException("FIELD_POLICY_CHANNEL_INVALID", "字段渠道无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        ConfiguredModule module = requireModule(context, input.resourceCode());
        boolean fieldExists = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .eq(ConfiguredModuleField::getModuleId, module.getId())
                        .eq(ConfiguredModuleField::getCode, input.fieldCode())
                        .eq(ConfiguredModuleField::getStatus, "ACTIVE"))
                .stream().findAny().isPresent();
        if (!fieldExists) {
            throw new DomainException("AUTHORIZATION_FIELD_REFERENCE_INVALID", "字段权限引用了不存在的模块字段",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (input.writable() && !input.readable()) {
            throw new DomainException("FIELD_POLICY_INVALID", "可写字段必须同时可读", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private boolean previewFilter(SystemAuthorizationModels.PreviewRequest input, String resourceCode, String actionCode) {
        return (input.resourceCode() == null || input.resourceCode().isBlank()
                || matches(resourceCode, input.resourceCode()))
                && (input.actionCode() == null || input.actionCode().isBlank()
                || matches(actionCode, input.actionCode()));
    }

    private DataScopeExpression findScope(Map<String, DataScopeExpression> scopes,
                                          String resourceType, String resourceCode, String actionCode) {
        return scopes.entrySet().stream().filter(entry -> {
            String[] values = entry.getKey().split(":", 3);
            return values.length == 3 && matches(values[0], resourceType)
                    && matches(values[1], resourceCode) && matches(values[2], actionCode);
        }).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private boolean matches(String granted, String required) {
        return "*".equals(granted) || granted.equals(required);
    }

    private ConfiguredModule requireModule(AuthenticatedContext context, String code) {
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                        .eq(ConfiguredModule::getCode, code))
                .stream().findFirst().orElseThrow(() -> new DomainException("AUTHORIZATION_REFERENCE_INVALID",
                        "权限引用了不存在的业务模块", HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private List<SystemAuthorizationModels.RoleView> roles(AuthenticatedContext context) {
        return roleService.selectList(Wrappers.<SystemRole>lambdaQuery().eq(SystemRole::getTenantId, context.tenantId()))
                .stream().sorted(Comparator.comparing(SystemRole::getId)).map(this::roleView).toList();
    }

    private List<SystemAuthorizationModels.ResourceView> resources(AuthenticatedContext context) {
        List<SystemAuthorizationModels.ResourceView> result = new ArrayList<>();
        result.add(new SystemAuthorizationModels.ResourceView("CONFIG", "SYSTEM", "系统管理",
                FIXED_RESOURCES.get("CONFIG:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("CONFIG:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("CONFIG", "MODULE", "业务模块配置",
                FIXED_RESOURCES.get("CONFIG:MODULE"), List.of(), actionNames(FIXED_RESOURCES.get("CONFIG:MODULE")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("AUDIT", "EVENT", "审计日志",
                FIXED_RESOURCES.get("AUDIT:EVENT"), List.of(), actionNames(FIXED_RESOURCES.get("AUDIT:EVENT")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("TODO", "SYSTEM", "我的待办",
                FIXED_RESOURCES.get("TODO:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("TODO:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("MESSAGE", "SYSTEM", "消息中心",
                FIXED_RESOURCES.get("MESSAGE:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("MESSAGE:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("AI", "SYSTEM", "智能助手",
                FIXED_RESOURCES.get("AI:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("AI:SYSTEM")), Map.of()));
        for (ConfiguredModule module : moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, context.systemId()).eq(ConfiguredModule::getOwnerTenantId, context.tenantId()))) {
            Set<String> actions = new LinkedHashSet<>(MODULE_ACTIONS);
            actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                            .eq(ConfiguredModuleAction::getModuleId, module.getId()).eq(ConfiguredModuleAction::getStatus, "ACTIVE"))
                    .forEach(action -> actions.add(action.getCode()));
            List<ConfiguredModuleField> configuredFields = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                            .eq(ConfiguredModuleField::getModuleId, module.getId()).eq(ConfiguredModuleField::getStatus, "ACTIVE"))
                    .stream().sorted(Comparator.comparing(ConfiguredModuleField::getSortOrder)
                            .thenComparing(ConfiguredModuleField::getId)).toList();
            List<String> fields = configuredFields.stream().map(ConfiguredModuleField::getCode).toList();
            Map<String, String> fieldNames = configuredFields.stream().collect(Collectors.toMap(
                    ConfiguredModuleField::getCode, ConfiguredModuleField::getName,
                    (first, ignored) -> first, LinkedHashMap::new));
            List<String> sortedActions = actions.stream().sorted().toList();
            result.add(new SystemAuthorizationModels.ResourceView("MODULE", module.getCode(), module.getName(),
                    sortedActions, fields, actionNames(sortedActions), fieldNames));
        }
        return result;
    }

    private SystemAuthorizationModels.RoleView roleView(SystemRole role) {
        return new SystemAuthorizationModels.RoleView(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                Boolean.TRUE.equals(role.getBuiltIn()), role.getStatus(), permissionInputs(role.getId()),
                fieldPolicyInputs(role.getId()), role.getVersion());
    }

    private List<SystemAuthorizationModels.PermissionInput> permissionInputs(Long roleId) {
        return permissionService.selectList(Wrappers.<SystemRolePermission>lambdaQuery()
                        .eq(SystemRolePermission::getRoleId, roleId)).stream()
                .sorted(Comparator.comparing(SystemRolePermission::getId))
                .map(row -> new SystemAuthorizationModels.PermissionInput(row.getResourceType(), row.getResourceCode(),
                        row.getActionCode(), row.getDataScopeType(), row.getDataScopeJson())).toList();
    }

    private List<SystemAuthorizationModels.FieldPolicyInput> fieldPolicyInputs(Long roleId) {
        return fieldPolicyService.selectList(Wrappers.<AuthorizationFieldPolicy>lambdaQuery()
                        .eq(AuthorizationFieldPolicy::getContextType, "SYSTEM")
                        .eq(AuthorizationFieldPolicy::getRoleId, roleId)).stream()
                .sorted(Comparator.comparing(AuthorizationFieldPolicy::getId))
                .map(row -> new SystemAuthorizationModels.FieldPolicyInput(row.getResourceCode(), row.getFieldCode(),
                        row.getChannel(), Boolean.TRUE.equals(row.getReadable()), Boolean.TRUE.equals(row.getWritable()),
                        row.getMaskStrategy())).toList();
    }

    private SystemAuthorizationModels.DepartmentView departmentView(SystemDepartment item) {
        return new SystemAuthorizationModels.DepartmentView(item.getId(), item.getParentId(), item.getCode(), item.getName(),
                item.getPathCode(), item.getSortOrder(), item.getStatus(), item.getVersion());
    }

    private SystemAuthorizationModels.MemberView memberView(SystemTenantMember tenantMember, SystemMember member,
                                                              List<Long> roleIds) {
        SystemDepartment department = tenantMember.getDepartmentId() == null
                ? null : departmentService.selectById(tenantMember.getDepartmentId());
        OrganizationRelationRepository.MemberRelation relation = relationRepository.byTenant(tenantMember.getTenantId())
                .get(tenantMember.getId());
        String managerName = null;
        if (relation != null && relation.managerTenantMemberId() != null) {
            SystemTenantMember managerMembership = tenantMemberService.selectById(relation.managerTenantMemberId());
            SystemMember manager = managerMembership == null ? null
                    : memberService.selectById(managerMembership.getSystemMemberId());
            managerName = manager == null ? null : manager.getDisplayName();
        }
        List<String> roleNames = roleIds.stream().map(roleService::selectById)
                .filter(java.util.Objects::nonNull).map(SystemRole::getName).distinct().sorted().toList();
        return new SystemAuthorizationModels.MemberView(tenantMember.getId(), tenantMember.getSystemMemberId(),
                member == null ? null : member.getAccountId(), member == null ? "未知成员" : member.getDisplayName(),
                member == null ? null : member.getEmployeeNumber(), tenantMember.getDepartmentId(),
                department == null ? null : department.getName(),
                relation == null ? null : relation.managerTenantMemberId(), managerName,
                relation == null ? null : relation.positionTitle(),
                Boolean.TRUE.equals(tenantMember.getTenantAdmin()), tenantMember.getStatus(),
                roleIds.stream().distinct().sorted().toList(), roleNames, tenantMember.getVersion());
    }

    private void validateReportingLine(AuthenticatedContext context, Long tenantMemberId, Long managerTenantMemberId) {
        if (managerTenantMemberId == null) return;
        requireTenantMember(context, managerTenantMemberId);
        if (tenantMemberId.equals(managerTenantMemberId)) {
            throw new DomainException("REPORTING_LINE_CYCLE", "直属上级不能是成员本人", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<Long, OrganizationRelationRepository.MemberRelation> relations = relationRepository.byTenant(context.tenantId());
        Long current = managerTenantMemberId;
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            if (tenantMemberId.equals(current)) {
                throw new DomainException("REPORTING_LINE_CYCLE", "上下级关系不能形成循环", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            OrganizationRelationRepository.MemberRelation relation = relations.get(current);
            current = relation == null ? null : relation.managerTenantMemberId();
        }
    }

    private SystemDepartment rootDepartment(AuthenticatedContext context) {
        return departments(context).stream().filter(item -> item.getParentId() == null).findFirst()
                .orElseThrow(() -> new DomainException("ROOT_DEPARTMENT_MISSING", "系统根部门不存在，请联系平台管理员",
                        HttpStatus.CONFLICT));
    }

    private Map<String, String> actionNames(List<String> actions) {
        return actions.stream().collect(Collectors.toMap(Function.identity(),
                action -> ACTION_NAMES.getOrDefault(action, "自定义操作"), (first, ignored) -> first, LinkedHashMap::new));
    }

    private String stableCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private List<SystemDepartment> departments(AuthenticatedContext context) {
        return departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getTenantId, context.tenantId())).stream()
                .sorted(Comparator.comparing(SystemDepartment::getPathCode)).toList();
    }

    private SystemDepartment requireDepartment(AuthenticatedContext context, Long id) {
        SystemDepartment value = departmentService.selectById(id);
        if (value == null || !context.tenantId().equals(value.getTenantId())) {
            throw new DomainException("DEPARTMENT_NOT_FOUND", "当前租户中不存在该部门", HttpStatus.NOT_FOUND);
        }
        return value;
    }

    private SystemTenantMember requireTenantMember(AuthenticatedContext context, Long id) {
        SystemTenantMember value = tenantMemberService.selectById(id);
        if (value == null || !context.tenantId().equals(value.getTenantId())) {
            throw new DomainException("SYSTEM_MEMBER_NOT_FOUND", "当前租户中不存在该成员", HttpStatus.NOT_FOUND);
        }
        return value;
    }

    private SystemRole requireRole(AuthenticatedContext context, Long id) {
        SystemRole value = roleService.selectById(id);
        if (value == null || !context.tenantId().equals(value.getTenantId())) {
            throw new DomainException("SYSTEM_ROLE_NOT_FOUND", "当前租户中不存在该角色", HttpStatus.NOT_FOUND);
        }
        return value;
    }

    private long latestVersion(Long systemId, Long tenantId) {
        return versionService.selectList(Wrappers.<AuthorizationPermissionVersion>lambdaQuery()
                        .eq(AuthorizationPermissionVersion::getContextType, "SYSTEM")
                        .eq(AuthorizationPermissionVersion::getSystemId, systemId)
                        .eq(AuthorizationPermissionVersion::getTenantId, tenantId)).stream()
                .map(AuthorizationPermissionVersion::getVersionNumber).max(Long::compareTo).orElse(0L);
    }

    private void bumpCache(Long systemId, Long tenantId, long version) {
        String contextKey = "system:" + systemId + ":tenant:" + tenantId;
        CoreCacheEpoch epoch = cacheEpochService.selectList(Wrappers.<CoreCacheEpoch>lambdaQuery()
                        .eq(CoreCacheEpoch::getContextKey, contextKey)
                        .eq(CoreCacheEpoch::getCacheNamespace, "AUTHORIZATION"))
                .stream().findFirst().orElse(null);
        if (epoch == null) {
            epoch = new CoreCacheEpoch();
            epoch.setContextKey(contextKey);
            epoch.setCacheNamespace("AUTHORIZATION");
            epoch.setEpochValue(version);
            epoch.setReason("authorization-published:" + version);
            cacheEpochService.insert(epoch);
        } else {
            epoch.setEpochValue(version);
            epoch.setReason("authorization-published:" + version);
            cacheEpochService.updateById(epoch);
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception exception) {
            throw new DomainException("DATA_SCOPE_JSON_INVALID", "数据范围条件不是有效 JSON", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void requireContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireVersion(Integer expected, Integer actual) {
        if (expected == null || !expected.equals(actual)) throw conflict();
    }

    private DomainException conflict() {
        return new DomainException("CONCURRENT_MODIFICATION", "数据已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
    }
}
