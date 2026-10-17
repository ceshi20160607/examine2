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
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogService;
import com.unique.unexamine.moduleconfig.manage.SharedSourceMetadataService;
import com.fasterxml.jackson.databind.JsonNode;
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
    private static final Map<String, List<String>> FIXED_RESOURCES = Map.ofEntries(
            Map.entry("CONFIG:SYSTEM", List.of("MANAGE")),
            Map.entry("CONFIG:MODULE", List.of("MANAGE", "PREVIEW", "PUBLISH", "ROLLBACK")),
            Map.entry("CONFIG:MODULE_FIELD", List.of("CONFIGURE_SENSITIVE")),
            // 设计与运行授权分别配置，目录必须覆盖真实服务检查，否则普通配置者永远无法被授权。
            Map.entry("FLOW:SYSTEM", List.of("VIEW", "DESIGN", "SIMULATE", "PUBLISH", "BIND_VIEW", "BIND_PUBLISH",
                    "VIEW_RUNTIME", "START", "HANDLE", "TERMINATE", "ADD_MANUAL_NODE")),
            // 业务附件与审批页共用同一文件权限，普通角色必须能在权限页面明确配置。
            Map.entry("FILE:OBJECT", List.of("VIEW", "UPLOAD", "REFERENCE", "DELETE", "DOWNLOAD", "MANAGE")),
            Map.entry("AUDIT:EVENT", List.of("VIEW", "VIEW_SENSITIVE", "PURGE")),
            Map.entry("TODO:SYSTEM", List.of("VIEW", "HANDLE")),
            Map.entry("MESSAGE:SYSTEM", List.of("VIEW", "SEND", "MANAGE")),
            Map.entry("AI:SYSTEM", List.of("VIEW", "MANAGE", "PUBLISH")),
            // 工作台此前只有运行时检查、没有可配置资源，普通成员无法被授予真实任务权限。
            Map.entry("WORK:SYSTEM", List.of("VIEW", "CREATE_PROJECT", "MANAGE_PROJECT", "CREATE_TASK", "UPDATE_TASK",
                    "CREATE_LOG", "UPDATE_LOG", "VIEW_OTHERS", "VIEW_ALL", "VIEW_CONFIG", "CONFIGURE",
                    "PUBLISH_CONFIG", "VIEW_CALENDAR", "VIEW_DETAIL")));
    private static final Set<String> DATA_SCOPES = Set.of(
            "ALL", "DEPARTMENT_AND_DESCENDANTS", "DEPARTMENT", "SELF_AND_SUBORDINATES", "SELF",
            "SPECIFIED_MEMBERS", "SPECIFIED_DEPARTMENTS", "CUSTOM");
    private static final Set<String> CHANNELS = Set.of("PAGE", "APPLICATION", "FILE", "FLOW");
    private static final Set<String> MODULE_ACTIONS = Set.of(
            "LIST", "DETAIL", "CREATE", "UPDATE", "DELETE", "IMPORT", "EXPORT", "CONVERT", "PRINT");
    private static final Map<String, String> ACTION_NAMES = Map.ofEntries(
            Map.entry("MANAGE", "管理"),
            Map.entry("PREVIEW", "预览与检查"), Map.entry("PUBLISH", "发布"), Map.entry("ROLLBACK", "回滚发布"),
            Map.entry("CONFIGURE_SENSITIVE", "配置敏感字段"),
            Map.entry("VIEW_RUNTIME", "查看流程运行"), Map.entry("START", "发起流程"),
            Map.entry("DESIGN", "设计流程"), Map.entry("SIMULATE", "模拟流程"),
            Map.entry("BIND_VIEW", "查看动作绑定"), Map.entry("BIND_PUBLISH", "配置动作绑定"),
            Map.entry("HANDLE", "处理事项"), Map.entry("TERMINATE", "终止流程"), Map.entry("ADD_MANUAL_NODE", "添加审批节点"),
            Map.entry("VIEW", "查看"), Map.entry("READ", "读取"),
            Map.entry("VIEW_SENSITIVE", "查看敏感信息"), Map.entry("PURGE", "清理"),
            Map.entry("LIST", "查看列表"), Map.entry("DETAIL", "查看详情"), Map.entry("CREATE", "新建"),
            Map.entry("UPDATE", "编辑"), Map.entry("DELETE", "删除"), Map.entry("IMPORT", "导入"),
            Map.entry("EXPORT", "导出"), Map.entry("CONVERT", "转化"), Map.entry("PRINT", "打印"),
            Map.entry("UPLOAD", "上传"), Map.entry("REFERENCE", "建立业务引用"), Map.entry("DOWNLOAD", "下载"),
            Map.entry("ARCHIVE", "归档"), Map.entry("RESTORE", "恢复"), Map.entry("TRANSFER", "转交负责人"),
            Map.entry("SHARE", "共享"), Map.entry("CREATE_PROJECT", "新建项目"),
            Map.entry("MANAGE_PROJECT", "管理项目"), Map.entry("CREATE_TASK", "新建任务"),
            Map.entry("UPDATE_TASK", "处理任务"), Map.entry("CREATE_LOG", "新建工作日志"),
            Map.entry("UPDATE_LOG", "修订工作日志"), Map.entry("VIEW_OTHERS", "查看他人工作"),
            Map.entry("VIEW_ALL", "查看全部工作"), Map.entry("VIEW_CONFIG", "查看工作配置"),
            Map.entry("CONFIGURE", "配置工作字段"), Map.entry("PUBLISH_CONFIG", "发布工作配置"),
            Map.entry("VIEW_CALENDAR", "查看工作日历"), Map.entry("VIEW_DETAIL", "查看统计明细"));

    private final SystemDepartmentBaseService departmentService;
    private final PlatformAccountBaseService accountService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final TenantMemberQuotaService tenantMemberQuotaService;
    /** 主租户模板可供当前租户配置角色，但数据访问仍受运行时共享和权限交集限制。 */
    private final SystemTenantBaseService tenantService;
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
    private final SystemPeopleDirectoryService peopleDirectoryService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final ModulePublicationService modulePublicationService;
    /** 权限预览直接采用运行态目录，避免菜单显示与真实成员导航分叉。 */
    private final RuntimeModuleCatalogService runtimeModuleCatalogService;
    /** 来源资源只来自当前有效共享，不枚举其他租户的全部扩展。 */
    private final SharedSourceMetadataService sharedSourceMetadata;

    public SystemAuthorizationManagementService(
            SystemDepartmentBaseService departmentService,
            PlatformAccountBaseService accountService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            TenantMemberQuotaService tenantMemberQuotaService,
            SystemTenantBaseService tenantService,
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
            SystemPeopleDirectoryService peopleDirectoryService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper, ModulePublicationService modulePublicationService,
            RuntimeModuleCatalogService runtimeModuleCatalogService,
            SharedSourceMetadataService sharedSourceMetadata) {
        this.departmentService = departmentService;
        this.accountService = accountService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.tenantMemberQuotaService = tenantMemberQuotaService;
        this.tenantService = tenantService;
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
        this.peopleDirectoryService = peopleDirectoryService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.modulePublicationService = modulePublicationService;
        this.runtimeModuleCatalogService = runtimeModuleCatalogService;
        this.sharedSourceMetadata = sharedSourceMetadata;
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
            if (input.code() != null && !input.code().isBlank()
                    && !input.code().strip().equals(department.getCode())) {
                throw new DomainException("DEPARTMENT_CODE_IMMUTABLE", "部门编码创建后不能修改", HttpStatus.CONFLICT);
            }
            oldPath = department.getPathCode();
        }
        SystemDepartment parent = input.parentId() == null ? null : requireActiveDepartment(context, input.parentId());
        if (department.getId() != null && parent != null
                && (parent.getId().equals(department.getId()) || parent.getPathCode().startsWith(department.getPathCode() + "/"))) {
            throw new DomainException("ORGANIZATION_CYCLE", "部门不能移动到自身或下级部门", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        department.setParentId(parent == null ? null : parent.getId());
        if (department.getId() == null) {
            department.setCode(input.code() == null || input.code().isBlank()
                    ? stableCode("department") : input.code().strip());
        }
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

    /** 拖拽移动先更新部门及其下级路径，再在同一事务中固定目标上级下的顺序。 */
    @Transactional
    public SystemAuthorizationModels.DepartmentView moveDepartment(
            AuthenticatedContext context, Long departmentId,
            SystemAuthorizationModels.DepartmentMoveRequest input, String traceId) {
        requireContext(context);
        SystemDepartment source = requireActiveDepartment(context, departmentId);
        requireVersion(input.expectedVersion(), source.getVersion());
        if (source.getParentId() == null) {
            throw new DomainException("ROOT_DEPARTMENT_IMMOVABLE", "根部门不能移动", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        SystemDepartment target = requireActiveDepartment(context, input.targetDepartmentId());
        if (source.getId().equals(target.getId())
                || target.getPathCode().startsWith(source.getPathCode() + "/")) {
            throw new DomainException("ORGANIZATION_CYCLE", "部门不能移动到自身或下级部门", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String placement = input.placement().strip().toUpperCase();
        if (!Set.of("INSIDE", "BEFORE", "AFTER").contains(placement)) {
            throw new DomainException("DEPARTMENT_MOVE_PLACEMENT_INVALID", "请选择部门内或同级前后位置",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Long parentId = "INSIDE".equals(placement) ? target.getId() : target.getParentId();
        if (parentId == null) {
            throw new DomainException("ROOT_DEPARTMENT_IMMOVABLE", "不能在根部门旁创建第二个根部门",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        saveDepartment(context, new SystemAuthorizationModels.DepartmentRequest(source.getId(), parentId,
                source.getCode(), source.getName(), source.getSortOrder(), source.getVersion()), traceId);

        List<SystemDepartment> siblings = new ArrayList<>(departments(context).stream()
                .filter(item -> parentId.equals(item.getParentId()) && !source.getId().equals(item.getId()))
                .sorted(Comparator.comparing((SystemDepartment item) -> item.getSortOrder() == null ? 0 : item.getSortOrder())
                        .thenComparing(SystemDepartment::getId)).toList());
        SystemDepartment moved = departmentService.selectById(source.getId());
        int index = siblings.size();
        if (!"INSIDE".equals(placement)) {
            index = -1;
            for (int candidate = 0; candidate < siblings.size(); candidate++) {
                if (target.getId().equals(siblings.get(candidate).getId())) {
                    index = candidate + ("AFTER".equals(placement) ? 1 : 0);
                    break;
                }
            }
            if (index < 0) throw conflict();
        }
        siblings.add(index, moved);
        for (int position = 0; position < siblings.size(); position++) {
            SystemDepartment sibling = siblings.get(position);
            int sortOrder = (position + 1) * 10;
            if (sibling.getSortOrder() != null && sibling.getSortOrder() == sortOrder) continue;
            sibling.setSortOrder(sortOrder);
            if (departmentService.updateById(sibling) != 1) throw conflict();
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DEPARTMENT_MOVED", "SYSTEM_DEPARTMENT", source.getId().toString(), "SUCCESS",
                Map.of("targetDepartmentId", target.getId(), "placement", placement, "parentId", parentId));
        return departmentView(departmentService.selectById(source.getId()));
    }

    /** 成员归属为空时回到当前租户有效根部门，避免产生无部门的有效成员。 */
    @Transactional
    public SystemAuthorizationModels.MemberView assignMember(
            AuthenticatedContext context, Long tenantMemberId,
            SystemAuthorizationModels.MemberAssignmentRequest input, String traceId) {
        requireContext(context);
        SystemTenantMember tenantMember = requireTenantMember(context, tenantMemberId);
        requireVersion(input.expectedVersion(), tenantMember.getVersion());
        Long departmentId = input.departmentId() == null ? rootDepartment(context).getId()
                : requireActiveDepartment(context, input.departmentId()).getId();
        validateReportingLine(context, tenantMemberId, input.managerTenantMemberId());
        List<Long> distinctRoleIds = input.roleIds().stream().distinct().sorted().toList();
        for (Long roleId : distinctRoleIds) requireRole(context, roleId);
        tenantMember.setDepartmentId(departmentId);
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
                Map.of("departmentId", departmentId,
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
            member.setJoinTime(LocalDateTime.now());
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
        // 新建和恢复有效租户成员都会增加当前租户用量，角色调整不重复占用。
        tenantMemberQuotaService.checkBeforeActivate(context.systemId(), context.tenantId());
        if (membership == null) {
            membership = new SystemTenantMember();
            membership.setSystemId(context.systemId());
            membership.setTenantId(context.tenantId());
            membership.setSystemMemberId(member.getId());
            membership.setDepartmentId(input.departmentId() == null ? rootDepartment(context).getId()
                    : requireActiveDepartment(context, input.departmentId()).getId());
            membership.setTenantAdmin(false);
            membership.setStatus("ACTIVE");
            tenantMemberService.insert(membership);
        } else {
            membership.setDepartmentId(input.departmentId() == null ? rootDepartment(context).getId()
                    : requireActiveDepartment(context, input.departmentId()).getId());
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
        List<SystemAuthorizationModels.ResourceView> catalog = resources(context);
        input.permissions().forEach(permission -> validatePermission(context, permission, catalog));
        input.fieldPolicies().forEach(policy -> validateFieldPolicy(context, policy, catalog));
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
            if (input.code() != null && !input.code().isBlank()
                    && !input.code().strip().equals(role.getCode())) {
                throw new DomainException("ROLE_CODE_IMMUTABLE", "角色编码创建后不能修改", HttpStatus.CONFLICT);
            }
        }
        // 已发布角色的草稿只写在角色草稿字段；生效授权行必须等发布时才替换。
        if (role.getId() != null && "ACTIVE".equals(role.getStatus())) {
            role.setDraftCode(role.getCode());
            role.setDraftName(input.name().strip());
            role.setDraftDescription(input.description() == null ? "" : input.description());
            role.setDraftPermissionsJson(writeRoleDraft(input.permissions()));
            role.setDraftFieldPoliciesJson(writeRoleDraft(input.fieldPolicies()));
            if (roleService.updateById(role) != 1) throw conflict();
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "SYSTEM_AUTHORIZATION_ROLE_SAVED", "SYSTEM_ROLE", role.getId().toString(), "DRAFT",
                    Map.of("permissions", input.permissions().size(), "fieldPolicies", input.fieldPolicies().size()));
            return roleView(roleService.selectById(role.getId()));
        }
        if (role.getId() == null) {
            role.setCode(input.code() == null || input.code().isBlank()
                    ? stableCode("role") : input.code().strip());
        }
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
        boolean editingPublishedRole = "ACTIVE".equals(role.getStatus());
        if (editingPublishedRole && !hasRoleDraft(role)) {
            throw new DomainException("ROLE_DRAFT_REQUIRED", "请先保存角色草稿再发布", HttpStatus.CONFLICT);
        }
        List<SystemAuthorizationModels.PermissionInput> permissions = editingPublishedRole
                ? draftPermissionInputs(role) : permissionInputs(roleId);
        List<SystemAuthorizationModels.FieldPolicyInput> policies = editingPublishedRole
                ? draftFieldPolicyInputs(role) : fieldPolicyInputs(roleId);
        if (permissions.isEmpty()) {
            throw new DomainException("ROLE_PERMISSION_REQUIRED", "角色至少需要一项有效权限", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<SystemAuthorizationModels.ResourceView> catalog = resources(context);
        permissions.forEach(permission -> validatePermission(context, permission, catalog));
        policies.forEach(policy -> validateFieldPolicy(context, policy, catalog));
        if (editingPublishedRole) {
            if (!role.getCode().equals(role.getDraftCode())) {
                throw new DomainException("ROLE_CODE_IMMUTABLE", "角色编码创建后不能修改", HttpStatus.CONFLICT);
            }
            role.setName(role.getDraftName());
            role.setDescription(role.getDraftDescription());
            try {
                if (roleService.updateById(role) != 1) throw conflict();
            } catch (RuntimeException exception) {
                if (exception instanceof DomainException domainException) throw domainException;
                throw new DomainException("ROLE_CODE_EXISTS", "当前租户已存在相同角色编码", HttpStatus.CONFLICT);
            }
            replaceRoleConfiguration(context, roleId, permissions, policies);
            if (roleService.update(Wrappers.<SystemRole>lambdaUpdate().eq(SystemRole::getId, roleId)
                    .set(SystemRole::getDraftCode, null)
                    .set(SystemRole::getDraftName, null)
                    .set(SystemRole::getDraftDescription, null)
                    .set(SystemRole::getDraftPermissionsJson, null)
                    .set(SystemRole::getDraftFieldPoliciesJson, null)) != 1) throw conflict();
        } else {
            role.setStatus("ACTIVE");
            if (roleService.updateById(role) != 1) throw conflict();
        }
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
        PermissionResolutionResult resolution = permissionResolver.resolveWithDiagnostics(
                context.systemId(), context.tenantId(), target.getId());
        ResolvedPermissions resolved = resolution.permissions();
        // 当前所属人的隐式权限来自系统归属，不能解释成不存在的角色贡献。
        boolean systemOwner = "SYSTEM_OWNER".equals(resolution.source());
        // 用目标成员的已生效权限计算运行态菜单；管理员自己的会话仅用于发起预览和审计。
        AuthenticatedContext targetContext = new AuthenticatedContext(null, systemMember.getAccountId(),
                context.platformId(), context.systemId(), context.tenantId(), systemMember.getId(), target.getId(),
                null, systemMember.getDisplayName(), null, resolved.roleIds(),
                resolved.permissions(), resolved.dataScopes());
        var visibleMenus = runtimeModuleCatalogService.list(targetContext);
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
                        systemOwner ? "系统当前所属人隐式拥有全部管理与业务权限，不依赖角色授权"
                                : allowed ? "至少一个有效角色授予该动作；菜单动作和数据范围按角色并集合并"
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
            for (String action : resource.actions()) {
                if (input.actionCode() != null && !input.actionCode().isBlank()
                        && !input.actionCode().equals(action)) continue;
                if (systemOwner) {
                    for (String field : resource.fields()) {
                        fieldDecisions.add(new SystemAuthorizationModels.FieldDecision(resource.resourceCode(), action,
                                field, "PAGE", true, true, List.of(), List.of(),
                                "系统当前所属人隐式拥有字段读取和写入权限，不依赖角色字段策略"));
                    }
                    continue;
                }
                // 字段硬限制只取授予当前动作的角色，不能让仅授予列表的角色影响详情预览。
                Set<Long> actionRoleIds = resolved.permissions().stream()
                        .filter(grant -> matches(grant.resourceType(), "MODULE")
                                && matches(grant.resourceCode(), resource.resourceCode())
                                && matches(grant.actionCode(), action))
                        .flatMap(grant -> grant.roleIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
                if (actionRoleIds.isEmpty()) continue;
                for (String field : resource.fields()) {
                    List<AuthorizationFieldPolicy> policies = allPolicies.stream()
                            .filter(policy -> resource.resourceCode().equals(policy.getResourceCode())
                                    && field.equals(policy.getFieldCode()) && "PAGE".equals(policy.getChannel())
                                    && actionRoleIds.contains(policy.getRoleId())).toList();
                    boolean complete = policies.stream().map(AuthorizationFieldPolicy::getRoleId).distinct().count()
                            == actionRoleIds.size();
                    boolean readable = complete && policies.stream().allMatch(policy -> Boolean.TRUE.equals(policy.getReadable()));
                    boolean writable = complete && policies.stream().allMatch(policy -> Boolean.TRUE.equals(policy.getWritable()));
                    List<String> masks = policies.stream().map(AuthorizationFieldPolicy::getMaskStrategy)
                            .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
                    fieldDecisions.add(new SystemAuthorizationModels.FieldDecision(resource.resourceCode(), action,
                            field, "PAGE", readable, writable, masks, actionRoleIds.stream().sorted().toList(),
                            complete ? "字段策略在授予当前动作的角色之间取硬限制交集"
                                    : "至少一个授予当前动作的角色缺少字段策略，按默认拒绝处理"));
                }
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "AUTHORIZATION_PERMISSION_PREVIEW", "SYSTEM_TENANT_MEMBER", target.getId().toString(), "SUCCESS",
                Map.of("permissionVersion", currentVersion,
                        "resourceCode", input.resourceCode() == null ? "" : input.resourceCode(),
                        "actionCode", input.actionCode() == null ? "" : input.actionCode(),
                        "targetRoleIds", resolved.roleIds()));
        return new SystemAuthorizationModels.PermissionPreview(currentVersion, systemOwner,
                memberView(target, systemMember, resolved.roleIds()), visibleMenus,
                contributions, actionDecisions, fieldDecisions,
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
            // 字段权限属于当前系统角色，保存时同时填写分域角色标识。
            row.setSystemRoleId(roleId);
            row.setResourceCode(input.resourceCode());
            row.setFieldCode(input.fieldCode());
            row.setChannel(input.channel());
            row.setReadable(input.readable());
            row.setWritable(input.writable());
            row.setMaskStrategy(input.maskStrategy());
            fieldPolicyService.insert(row);
        }
    }

    private void validatePermission(AuthenticatedContext context, SystemAuthorizationModels.PermissionInput input,
                                    List<SystemAuthorizationModels.ResourceView> catalog) {
        if (!DATA_SCOPES.contains(input.dataScopeType())) {
            throw new DomainException("DATA_SCOPE_INVALID", "数据范围无效：" + input.dataScopeType(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        normalizeJson(input.dataScopeJson());
        validateScopeTargets(context, input);
        String fixedKey = input.resourceType() + ":" + input.resourceCode();
        boolean valid = FIXED_RESOURCES.getOrDefault(fixedKey, List.of()).contains(input.actionCode());
        if ("MODULE".equals(input.resourceType())) {
            valid = catalog.stream().anyMatch(resource -> "MODULE".equals(resource.resourceType())
                    && input.resourceCode().equals(resource.resourceCode()) && resource.actions().contains(input.actionCode()));
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

    private void validateFieldPolicy(AuthenticatedContext context, SystemAuthorizationModels.FieldPolicyInput input,
                                     List<SystemAuthorizationModels.ResourceView> catalog) {
        if (!CHANNELS.contains(input.channel())) {
            throw new DomainException("FIELD_POLICY_CHANNEL_INVALID", "字段渠道无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean fieldExists = catalog.stream().anyMatch(resource -> "MODULE".equals(resource.resourceType())
                && input.resourceCode().equals(resource.resourceCode()) && resource.fields().contains(input.fieldCode()));
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

    /** 角色资源与运行目录采用相同模板边界；不纳入其他扩展租户的私有模块。 */
    private List<ConfiguredModule> authorizationModules(AuthenticatedContext context) {
        Long mainTenantId = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId())
                        .eq(SystemTenant::getMain, true).eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().map(SystemTenant::getId).findFirst().orElseThrow(() -> new DomainException(
                        "MAIN_TENANT_MISSING", "当前系统缺少有效主租户", HttpStatus.CONFLICT));
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .in(ConfiguredModule::getOwnerTenantId, List.of(context.tenantId(), mainTenantId)))
                .stream().sorted(Comparator.comparing(module -> !context.tenantId().equals(module.getOwnerTenantId())))
                .toList();
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
        // 与真实配置入口一致，普通管理员可以被明确授权，不依赖平台管理员通配权限。
        result.add(new SystemAuthorizationModels.ResourceView("CONFIG", "MODULE_FIELD", "敏感字段配置",
                FIXED_RESOURCES.get("CONFIG:MODULE_FIELD"), List.of(),
                actionNames(FIXED_RESOURCES.get("CONFIG:MODULE_FIELD")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("FLOW", "SYSTEM", "流程设计、运行与审批",
                FIXED_RESOURCES.get("FLOW:SYSTEM"), List.of(),
                actionNames(FIXED_RESOURCES.get("FLOW:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("FILE", "OBJECT", "业务附件",
                FIXED_RESOURCES.get("FILE:OBJECT"), List.of(),
                actionNames(FIXED_RESOURCES.get("FILE:OBJECT")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("AUDIT", "EVENT", "审计日志",
                FIXED_RESOURCES.get("AUDIT:EVENT"), List.of(), actionNames(FIXED_RESOURCES.get("AUDIT:EVENT")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("TODO", "SYSTEM", "我的待办",
                FIXED_RESOURCES.get("TODO:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("TODO:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("MESSAGE", "SYSTEM", "消息中心",
                FIXED_RESOURCES.get("MESSAGE:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("MESSAGE:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("AI", "SYSTEM", "智能助手",
                FIXED_RESOURCES.get("AI:SYSTEM"), List.of(), actionNames(FIXED_RESOURCES.get("AI:SYSTEM")), Map.of()));
        result.add(new SystemAuthorizationModels.ResourceView("WORK", "SYSTEM", "项目、任务与工作日志",
                FIXED_RESOURCES.get("WORK:SYSTEM"), List.of(),
                actionNames(FIXED_RESOURCES.get("WORK:SYSTEM")), Map.of()));
        List<ConfiguredModule> modules = new ArrayList<>(authorizationModules(context));
        Set<Long> currentModuleIds = modules.stream().map(ConfiguredModule::getId).collect(Collectors.toSet());
        Map<Long, List<SharedSourceMetadataService.SourcePublication>> sources = sharedSourceMetadata.current(context);
        sharedSourceMetadata.modules(context, sources.keySet()).stream()
                .filter(module -> !currentModuleIds.contains(module.getId())).forEach(modules::add);
        for (ConfiguredModule module : modules) {
            boolean current = currentModuleIds.contains(module.getId());
            // 私有来源模块只提供实际共享允许的发布动作，不能顺带授予新建、再共享等模块全局动作。
            Set<String> actions = current ? new LinkedHashSet<>(MODULE_ACTIONS) : new LinkedHashSet<>();
            List<SharedSourceMetadataService.SourcePublication> source = sources.getOrDefault(module.getId(), List.of());
            List<JsonNode> configuredActions = current ? authorizationActions(context, module, source)
                    : appendSharedResources(List.of(), source, "actions");
            configuredActions.forEach(action -> actions.add(action.path("code").asText()));
            List<JsonNode> configuredFields = current ? authorizationFields(context, module, source)
                    : appendSharedResources(List.of(), source, "fields");
            List<String> fields = configuredFields.stream().map(field -> field.path("code").asText()).toList();
            Map<String, String> fieldNames = configuredFields.stream().collect(Collectors.toMap(
                    field -> field.path("code").asText(), field -> field.path("name").asText(),
                    (first, ignored) -> first, LinkedHashMap::new));
            List<String> sortedActions = actions.stream().sorted().toList();
            Map<String, String> names = new LinkedHashMap<>(actionNames(sortedActions));
            configuredActions.forEach(action -> names.put(action.path("code").asText(), action.path("name").asText()));
            result.add(new SystemAuthorizationModels.ResourceView("MODULE", module.getCode(), module.getName(),
                    sortedActions, fields, names, fieldNames));
        }
        return result;
    }

    /** 本租户动作加上真实有效共享允许的来源动作，不枚举来源未发布或未共享动作。 */
    private List<JsonNode> authorizationActions(AuthenticatedContext context, ConfiguredModule module,
                                               List<SharedSourceMetadataService.SourcePublication> sources) {
        if ("ACTIVE".equals(module.getStatus())) {
            List<JsonNode> actions = new ArrayList<>();
            modulePublicationService.published(context, module.getCode()).configuration().path("actions").forEach(actions::add);
            return appendSharedResources(actions, sources, "actions");
        }
        return actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                        .eq(ConfiguredModuleAction::getModuleId, module.getId())
                        .eq(ConfiguredModuleAction::getOwnerTenantId, module.getOwnerTenantId())
                        .eq(ConfiguredModuleAction::getStatus, "ACTIVE")).stream()
                .map(action -> (JsonNode) objectMapper.valueToTree(action)).toList();
    }

    /** 发布字段使用逻辑编码；来源专属字段只在真实有效共享存在时进入当前角色目录。 */
    private List<JsonNode> authorizationFields(AuthenticatedContext context, ConfiguredModule module,
                                              List<SharedSourceMetadataService.SourcePublication> sources) {
        if ("ACTIVE".equals(module.getStatus())) {
            List<JsonNode> fields = new ArrayList<>();
            modulePublicationService.published(context, module.getCode()).configuration().path("fields").forEach(fields::add);
            return appendSharedResources(fields, sources, "fields");
        }
        // 本租户尚未发布的模块可提前配置角色；只包含该模块所有者自己的草稿字段。
        return fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .eq(ConfiguredModuleField::getModuleId, module.getId())
                        .eq(ConfiguredModuleField::getOwnerTenantId, module.getOwnerTenantId())
                        .eq(ConfiguredModuleField::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModuleField::getSortOrder, ConfiguredModuleField::getId)).stream()
                .map(field -> (JsonNode) objectMapper.valueToTree(field)).toList();
    }

    /** 角色硬限制按逻辑编码配置；同编码优先保留本租户名称，记录显示始终采用真实来源描述。 */
    private List<JsonNode> appendSharedResources(List<JsonNode> local,
                                                List<SharedSourceMetadataService.SourcePublication> sources, String part) {
        Map<String, JsonNode> logical = new LinkedHashMap<>();
        local.forEach(node -> logical.put(node.path("code").asText(), node));
        for (SharedSourceMetadataService.SourcePublication source : sources) {
            for (JsonNode node : source.configuration().configuration().path(part)) {
                String code = node.path("code").asText();
                if (code.isBlank() || code.startsWith("__t") || !"ACTIVE".equals(node.path("status").asText())) continue;
                if ("actions".equals(part) && !source.allowedActions().contains(code)) continue;
                logical.putIfAbsent(code, node);
            }
        }
        return new ArrayList<>(logical.values());
    }

    private SystemAuthorizationModels.RoleView roleView(SystemRole role) {
        boolean pending = hasRoleDraft(role);
        return new SystemAuthorizationModels.RoleView(role.getId(), pending ? role.getDraftCode() : role.getCode(),
                pending ? role.getDraftName() : role.getName(),
                pending ? role.getDraftDescription() : role.getDescription(),
                Boolean.TRUE.equals(role.getBuiltIn()), role.getStatus(),
                pending ? draftPermissionInputs(role) : permissionInputs(role.getId()),
                pending ? draftFieldPolicyInputs(role) : fieldPolicyInputs(role.getId()), role.getVersion(),
                pending || "DRAFT".equals(role.getStatus()));
    }

    private boolean hasRoleDraft(SystemRole role) {
        return role.getDraftPermissionsJson() != null && !role.getDraftPermissionsJson().isBlank();
    }

    private String writeRoleDraft(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("角色草稿序列化失败", exception);
        }
    }

    private List<SystemAuthorizationModels.PermissionInput> draftPermissionInputs(SystemRole role) {
        try {
            return objectMapper.readValue(role.getDraftPermissionsJson(), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, SystemAuthorizationModels.PermissionInput.class));
        } catch (Exception exception) {
            throw new IllegalStateException("角色权限草稿内容无效", exception);
        }
    }

    private List<SystemAuthorizationModels.FieldPolicyInput> draftFieldPolicyInputs(SystemRole role) {
        try {
            return objectMapper.readValue(role.getDraftFieldPoliciesJson(), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, SystemAuthorizationModels.FieldPolicyInput.class));
        } catch (Exception exception) {
            throw new IllegalStateException("角色字段策略草稿内容无效", exception);
        }
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

    /** 直属上级必须是同一租户的有效成员，且调整后不能形成汇报环。 */
    private void validateReportingLine(AuthenticatedContext context, Long tenantMemberId, Long managerTenantMemberId) {
        if (managerTenantMemberId == null) return;
        SystemTenantMember manager = requireTenantMember(context, managerTenantMemberId);
        if (!"ACTIVE".equals(manager.getStatus())) {
            throw new DomainException("MANAGER_NOT_ACTIVE", "直属上级不是当前工作空间的有效成员", HttpStatus.UNPROCESSABLE_ENTITY);
        }
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
        return departments(context).stream().filter(item -> item.getParentId() == null && "ACTIVE".equals(item.getStatus())).findFirst()
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

    /** 指定人员使用业务记录的系统成员 ID，候选必须与人员字段和流程目录同属当前有效工作空间。 */
    private void validateScopeTargets(AuthenticatedContext context, SystemAuthorizationModels.PermissionInput input) {
        boolean memberScope = "SPECIFIED_MEMBERS".equals(input.dataScopeType());
        boolean departmentScope = "SPECIFIED_DEPARTMENTS".equals(input.dataScopeType());
        if (!memberScope && !departmentScope) return;
        if (!"MODULE".equals(input.resourceType())) {
            throw new DomainException("DATA_SCOPE_INVALID", "指定成员或部门范围只适用于业务模块",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        JsonNode condition;
        try {
            condition = objectMapper.readTree(input.dataScopeJson());
        } catch (Exception exception) {
            throw new DomainException("DATA_SCOPE_JSON_INVALID", "请选择有效的成员或部门范围",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        JsonNode selected = condition == null ? null : condition.path(memberScope ? "memberIds" : "departmentIds");
        if (selected == null || !selected.isArray() || selected.isEmpty()) {
            throw new DomainException("DATA_SCOPE_TARGET_REQUIRED", "指定范围至少选择一名成员或一个部门",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var directory = peopleDirectoryService.recordOptions(context, memberScope, departmentScope);
        Set<Long> validIds = memberScope
                ? directory.people().stream().map(SystemPeopleDirectoryModels.Person::systemMemberId).collect(Collectors.toSet())
                : directory.departments().stream().map(SystemPeopleDirectoryModels.Department::id).collect(Collectors.toSet());
        for (JsonNode target : selected) {
            if (!target.isIntegralNumber() || !validIds.contains(target.longValue())) {
                throw new DomainException("DATA_SCOPE_TARGET_INVALID", "指定成员或部门不属于当前有效工作空间",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    /** 新成员、归属和父部门只能选有效部门；已停用部门仍可供历史引用展示。 */
    private SystemDepartment requireActiveDepartment(AuthenticatedContext context, Long id) {
        SystemDepartment value = requireDepartment(context, id);
        if (!"ACTIVE".equals(value.getStatus())) {
            throw new DomainException("DEPARTMENT_DISABLED", "部门已停用，请选择有效部门", HttpStatus.UNPROCESSABLE_ENTITY);
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
