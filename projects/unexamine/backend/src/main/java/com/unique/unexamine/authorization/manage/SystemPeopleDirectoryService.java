package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.authentication.base.entity.AuthorizationPermissionVersion;
import com.unique.unexamine.authentication.base.service.AuthorizationPermissionVersionBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemPeopleDirectoryService {
    private final SystemDepartmentBaseService departmentService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final PlatformAccountBaseService accountService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final SystemRoleBaseService roleService;
    private final AuthorizationPermissionVersionBaseService permissionVersionService;
    private final OrganizationRelationRepository relationRepository;

    public SystemPeopleDirectoryService(
            SystemDepartmentBaseService departmentService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            PlatformAccountBaseService accountService,
            SystemMemberRoleBaseService memberRoleService,
            SystemRoleBaseService roleService,
            AuthorizationPermissionVersionBaseService permissionVersionService,
            OrganizationRelationRepository relationRepository) {
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.accountService = accountService;
        this.memberRoleService = memberRoleService;
        this.roleService = roleService;
        this.permissionVersionService = permissionVersionService;
        this.relationRepository = relationRepository;
    }

    @Transactional(readOnly = true)
    public SystemPeopleDirectoryModels.Directory directory(AuthenticatedContext context, String keyword) {
        requireSystemContext(context);
        List<SystemDepartment> departments = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getSystemId, context.systemId())
                        .eq(SystemDepartment::getTenantId, context.tenantId())
                        .eq(SystemDepartment::getStatus, "ACTIVE"))
                .stream().sorted(Comparator.comparing(SystemDepartment::getPathCode)).toList();
        Map<Long, SystemDepartment> departmentById = departments.stream()
                .collect(Collectors.toMap(SystemDepartment::getId, Function.identity()));
        List<SystemTenantMember> memberships = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, context.systemId())
                .eq(SystemTenantMember::getTenantId, context.tenantId()).eq(SystemTenantMember::getStatus, "ACTIVE"));
        Map<Long, SystemMember> members = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId()).eq(SystemMember::getStatus, "ACTIVE"))
                .stream().collect(Collectors.toMap(SystemMember::getId, Function.identity()));
        // 候选人员必须能以有效平台账号进入当前系统，和流程实际解析处理人的条件一致。
        List<Long> accountIds = members.values().stream().map(SystemMember::getAccountId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Set<Long> activeAccountIds = accountIds.isEmpty() ? Set.of() : accountService.selectList(
                        Wrappers.<PlatformAccount>lambdaQuery()
                                .in(PlatformAccount::getId, accountIds)
                                .eq(PlatformAccount::getStatus, "ACTIVE"))
                .stream().map(PlatformAccount::getId).collect(Collectors.toSet());
        memberships = memberships.stream().filter(item -> {
            SystemMember member = members.get(item.getSystemMemberId());
            return member != null && activeAccountIds.contains(member.getAccountId());
        }).toList();
        Map<Long, SystemRole> roles = roleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                        .eq(SystemRole::getTenantId, context.tenantId()).eq(SystemRole::getStatus, "ACTIVE"))
                .stream().collect(Collectors.toMap(SystemRole::getId, Function.identity()));
        Map<Long, List<Long>> roleIds = memberRoleService.selectList(Wrappers.<SystemMemberRole>lambdaQuery()
                        .eq(SystemMemberRole::getTenantId, context.tenantId())).stream()
                .collect(Collectors.groupingBy(SystemMemberRole::getTenantMemberId,
                        Collectors.mapping(SystemMemberRole::getRoleId, Collectors.toList())));
        Map<Long, OrganizationRelationRepository.MemberRelation> relations = relationRepository.byTenant(context.tenantId());
        Map<Long, String> displayNames = memberships.stream().collect(Collectors.toMap(SystemTenantMember::getId,
                item -> {
                    SystemMember member = members.get(item.getSystemMemberId());
                    return member == null ? "未知成员" : member.getDisplayName();
                }));
        Map<Long, Integer> memberCounts = memberships.stream().filter(item -> item.getDepartmentId() != null)
                .collect(Collectors.groupingBy(SystemTenantMember::getDepartmentId, Collectors.summingInt(ignored -> 1)));
        String normalizedKeyword = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        List<SystemPeopleDirectoryModels.Person> people = memberships.stream()
                .sorted(Comparator.comparing(item -> displayNames.getOrDefault(item.getId(), "")))
                .map(item -> person(item, members.get(item.getSystemMemberId()), departmentById, roles, roleIds,
                        relations, displayNames))
                .filter(person -> matches(person, normalizedKeyword))
                .toList();
        List<SystemPeopleDirectoryModels.Department> departmentViews = departments.stream()
                .map(item -> new SystemPeopleDirectoryModels.Department(item.getId(), item.getParentId(), item.getName(),
                        fullDepartmentName(item, departmentById), memberCounts.getOrDefault(item.getId(), 0)))
                .toList();
        return new SystemPeopleDirectoryModels.Directory(departmentViews, people, latestVersion(context));
    }

    /** 仅解析服务端已授权记录引用的名称；调用方须先裁剪不可读及脱敏字段。 */
    @Transactional(readOnly = true)
    public SystemPeopleDirectoryModels.Labels recordLabels(
            AuthenticatedContext dataContext, Set<Long> memberIds, Set<Long> departmentIds) {
        Map<Long, String> members = memberIds.isEmpty() ? Map.of() : memberService.selectList(
                        Wrappers.<SystemMember>lambdaQuery().eq(SystemMember::getSystemId, dataContext.systemId())
                                .in(SystemMember::getId, memberIds)).stream()
                .collect(Collectors.toMap(SystemMember::getId, SystemMember::getDisplayName));
        Map<Long, String> departments = departmentIds.isEmpty() ? Map.of() : departmentService.selectList(
                        Wrappers.<SystemDepartment>lambdaQuery().eq(SystemDepartment::getSystemId, dataContext.systemId())
                                .eq(SystemDepartment::getTenantId, dataContext.tenantId())
                                .in(SystemDepartment::getId, departmentIds)).stream()
                .collect(Collectors.toMap(SystemDepartment::getId, SystemDepartment::getName));
        return new SystemPeopleDirectoryModels.Labels(members, departments);
    }

    /** 记录写动作已授权后提供组织选项；不开放账号、角色、上级关系等通讯录信息。 */
    @Transactional(readOnly = true)
    public SystemPeopleDirectoryModels.Directory recordOptions(
            AuthenticatedContext dataContext, boolean includeMembers, boolean includeDepartments) {
        SystemPeopleDirectoryModels.Directory directory = directory(dataContext, null);
        List<SystemPeopleDirectoryModels.Person> people = includeMembers ? directory.people().stream()
                .filter(person -> person.accountId() != null)
                .map(person -> new SystemPeopleDirectoryModels.Person(person.tenantMemberId(), person.systemMemberId(),
                        null, person.displayName(), null, person.departmentId(), person.departmentName(),
                        null, null, null, List.of(), false)).toList() : List.of();
        List<SystemPeopleDirectoryModels.Department> departments = includeDepartments ? directory.departments().stream()
                .map(item -> new SystemPeopleDirectoryModels.Department(item.id(), item.parentId(), item.name(),
                        item.fullName(), 0)).toList() : List.of();
        return new SystemPeopleDirectoryModels.Directory(departments, people, directory.permissionVersion());
    }

    private SystemPeopleDirectoryModels.Person person(
            SystemTenantMember membership,
            SystemMember member,
            Map<Long, SystemDepartment> departments,
            Map<Long, SystemRole> roles,
            Map<Long, List<Long>> roleIds,
            Map<Long, OrganizationRelationRepository.MemberRelation> relations,
            Map<Long, String> displayNames) {
        OrganizationRelationRepository.MemberRelation relation = relations.get(membership.getId());
        SystemDepartment department = departments.get(membership.getDepartmentId());
        List<String> roleNames = roleIds.getOrDefault(membership.getId(), List.of()).stream()
                .map(roles::get).filter(java.util.Objects::nonNull).map(SystemRole::getName).distinct().sorted().toList();
        return new SystemPeopleDirectoryModels.Person(
                membership.getId(), membership.getSystemMemberId(), member == null ? null : member.getAccountId(),
                member == null ? "未知成员" : member.getDisplayName(), member == null ? null : member.getEmployeeNumber(),
                membership.getDepartmentId(), department == null ? null : department.getName(),
                relation == null ? null : relation.managerTenantMemberId(),
                relation == null ? null : displayNames.get(relation.managerTenantMemberId()),
                relation == null ? null : relation.positionTitle(), roleNames,
                Boolean.TRUE.equals(membership.getTenantAdmin()));
    }

    private boolean matches(SystemPeopleDirectoryModels.Person person, String keyword) {
        if (keyword.isBlank()) return true;
        return java.util.stream.Stream.of(person.displayName(), person.employeeNumber(), person.departmentName(),
                        person.positionTitle(), String.join(" ", person.roleNames()))
                .filter(java.util.Objects::nonNull).anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(keyword));
    }

    private String fullDepartmentName(SystemDepartment department, Map<Long, SystemDepartment> byId) {
        java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
        SystemDepartment current = department;
        while (current != null) {
            names.addFirst(current.getName());
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return String.join(" / ", names);
    }

    private long latestVersion(AuthenticatedContext context) {
        return permissionVersionService.selectList(Wrappers.<AuthorizationPermissionVersion>lambdaQuery()
                        .eq(AuthorizationPermissionVersion::getContextType, "SYSTEM")
                        .eq(AuthorizationPermissionVersion::getSystemId, context.systemId())
                        .eq(AuthorizationPermissionVersion::getTenantId, context.tenantId())).stream()
                .map(AuthorizationPermissionVersion::getVersionNumber).max(Long::compareTo).orElse(0L);
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.BAD_REQUEST);
        }
    }
}
