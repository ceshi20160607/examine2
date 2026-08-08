package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.DataScope;
import com.unique.examine.plat.base.entity.DataScopeTarget;
import com.unique.examine.plat.base.entity.Department;
import com.unique.examine.plat.base.entity.DepartmentClosure;
import com.unique.examine.plat.base.entity.DepartmentLeaderAssignment;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.MemberDepartment;
import com.unique.examine.plat.base.entity.MemberManagerAssignment;
import com.unique.examine.plat.base.entity.MemberRole;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeTargetMapper;
import com.unique.examine.plat.base.mapper.PlatDepartmentClosureMapper;
import com.unique.examine.plat.base.mapper.PlatDepartmentLeaderAssignmentMapper;
import com.unique.examine.plat.base.mapper.PlatDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberManagerAssignmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatMemberRoleMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.dto.SystemAdminRequests;
import com.unique.examine.plat.manage.vo.SystemAdminViews;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemAdminOrganizationService {
    private final PlatDepartmentMapper departmentMapper;
    private final PlatDepartmentClosureMapper closureMapper;
    private final PlatDepartmentLeaderAssignmentMapper departmentLeaderMapper;
    private final PlatMemberDepartmentMapper memberDepartmentMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final PlatMemberManagerAssignmentMapper memberManagerMapper;
    private final PlatMemberRoleMapper memberRoleMapper;
    private final PlatRoleMapper roleMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatPermissionMapper permissionMapper;
    private final PlatDataScopeMapper dataScopeMapper;
    private final PlatDataScopeTargetMapper dataScopeTargetMapper;
    private final PlatAccountMapper accountMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatSystemMapper systemMapper;
    private final IdService idService;
    private final AuthzEpochService epochService;
    private final SystemAdminScopeSupport scopeSupport;
    private final SystemAdminMutationSupport mutationSupport;

    public SystemAdminOrganizationService(
            PlatDepartmentMapper departmentMapper,
            PlatDepartmentClosureMapper closureMapper,
            PlatDepartmentLeaderAssignmentMapper departmentLeaderMapper,
            PlatMemberDepartmentMapper memberDepartmentMapper,
            PlatMemberMapper memberMapper,
            PlatMemberTenantMapper memberTenantMapper,
            PlatMemberManagerAssignmentMapper memberManagerMapper,
            PlatMemberRoleMapper memberRoleMapper,
            PlatRoleMapper roleMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatPermissionMapper permissionMapper,
            PlatDataScopeMapper dataScopeMapper,
            PlatDataScopeTargetMapper dataScopeTargetMapper,
            PlatAccountMapper accountMapper,
            PlatTenantMapper tenantMapper,
            PlatSystemMapper systemMapper,
            IdService idService,
            AuthzEpochService epochService,
            SystemAdminScopeSupport scopeSupport,
            SystemAdminMutationSupport mutationSupport
    ) {
        this.departmentMapper = departmentMapper;
        this.closureMapper = closureMapper;
        this.departmentLeaderMapper = departmentLeaderMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
        this.memberMapper = memberMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.memberManagerMapper = memberManagerMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeTargetMapper = dataScopeTargetMapper;
        this.accountMapper = accountMapper;
        this.tenantMapper = tenantMapper;
        this.systemMapper = systemMapper;
        this.idService = idService;
        this.epochService = epochService;
        this.scopeSupport = scopeSupport;
        this.mutationSupport = mutationSupport;
    }

    public SystemAdminViews.Page<SystemAdminViews.Department> departments(
            AuthenticatedSession session,
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        var tenantId = scopeSupport.tenantId(session);
        var query = Wrappers.<Department>lambdaQuery()
                .eq(Department::getScopeType, "SYSTEM")
                .eq(Department::getScopeKey, systemId)
                .eq(Department::getSystemId, systemId)
                .eq(Department::getTenantId, tenantId)
                .eq(status != null && !status.isBlank(), Department::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(Department::getName, keyword.trim()).or()
                        .like(Department::getDepartmentCode, keyword.trim()))
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getCreatedAt);
        var result = departmentMapper.selectPage(new Page<>(page(page), size(size)), query);
        var items = result.getRecords().stream().map(this::departmentView).toList();
        return new SystemAdminViews.Page<>(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Transactional
    public SystemAdminViews.Department createDepartment(
            AuthenticatedSession session,
            long systemId,
            SystemAdminRequests.CreateDepartment request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var tenantId = scopeSupport.tenantId(session);
        return mutationSupport.idempotent(
                systemId + ":department:create:" + tenantId, idempotencyKey, request,
                SystemAdminViews.Department.class,
                () -> createDepartmentNow(session, systemId, tenantId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.Department createDepartmentNow(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            SystemAdminRequests.CreateDepartment request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var parentId = nullableId(request.parentId(), "parentId");
        if (parentId != null) {
            requireDepartment(systemId, tenantId, parentId);
        }
        var now = LocalDateTime.now();
        var department = new Department();
        department.setId(idService.nextId());
        department.setScopeType("SYSTEM");
        department.setScopeKey(systemId);
        department.setSystemId(systemId);
        department.setTenantId(tenantId);
        department.setParentId(parentId);
        department.setDepartmentCode(request.code().trim());
        department.setName(request.name().trim());
        department.setSortOrder(0);
        department.setStatus("ACTIVE");
        department.setCreatedAt(now);
        department.setCreatedBy(session.accountId());
        department.setUpdatedAt(now);
        department.setUpdatedBy(session.accountId());
        department.setVersion(0L);
        try {
            departmentMapper.insert(department);
            insertClosure(systemId, tenantId, department.getId(), department.getId(), 0, session.accountId(), now);
            if (parentId != null) {
                var ancestors = closureMapper.selectList(closureQuery(systemId, tenantId)
                        .eq(DepartmentClosure::getDescendantId, parentId));
                for (var ancestor : ancestors) {
                    insertClosure(
                            systemId, tenantId, ancestor.getAncestorId(), department.getId(),
                            ancestor.getDepth() + 1, session.accountId(), now
                    );
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw SystemAdminMutationSupport.conflict("RESOURCE_CONFLICT", "部门编码或层级已冲突");
        }
        epochService.bumpSystem(systemId, session.accountId());
        var after = departmentView(department);
        mutationSupport.success(
                session, systemId, "DEPARTMENT", after.id(), "SYSTEM_DEPARTMENT_CREATE",
                null, after, client
        );
        mutationSupport.outbox(
                session, systemId, "DEPARTMENT", after.id(), "SYSTEM_DEPARTMENT_CREATED",
                after, idempotencyKey, client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Department updateDepartment(
            AuthenticatedSession session,
            long systemId,
            long departmentId,
            SystemAdminRequests.UpdateDepartment request,
            ClientRequest client
    ) {
        var tenantId = scopeSupport.tenantId(session);
        var department = requireDepartment(systemId, tenantId, departmentId);
        requireVersion(department.getVersion(), request.version());
        var parentId = nullableId(request.parentId(), "parentId");
        if (parentId != null) {
            requireDepartment(systemId, tenantId, parentId);
            if (parentId == departmentId || closureMapper.selectCount(closureQuery(systemId, tenantId)
                    .eq(DepartmentClosure::getAncestorId, departmentId)
                    .eq(DepartmentClosure::getDescendantId, parentId)) > 0) {
                throw SystemAdminMutationSupport.invalidState("部门不能移动到自身子树下");
            }
        }
        var before = departmentView(department);
        var parentChanged = !Objects.equals(department.getParentId(), parentId);
        department.setParentId(parentId);
        department.setName(request.name().trim());
        department.setStatus(request.status());
        department.setUpdatedAt(LocalDateTime.now());
        department.setUpdatedBy(session.accountId());
        if (departmentMapper.updateById(department) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        if (parentChanged) {
            rebuildClosure(session, systemId, tenantId, departmentId, parentId);
        }
        epochService.bumpSystem(systemId, session.accountId());
        var after = departmentView(department);
        mutationSupport.success(
                session, systemId, "DEPARTMENT", after.id(), "SYSTEM_DEPARTMENT_UPDATE",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "DEPARTMENT", after.id(), "SYSTEM_DEPARTMENT_UPDATED",
                after, client.requestId(), client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Department updateDepartmentLeader(
            AuthenticatedSession session,
            long systemId,
            long departmentId,
            SystemAdminRequests.UpdateDepartmentLeader request,
            ClientRequest client
    ) {
        var tenantId = scopeSupport.tenantId(session);
        var department = requireDepartment(systemId, tenantId, departmentId);
        requireVersion(department.getVersion(), request.version());
        var leaderMemberId = nullableId(request.leaderMemberId(), "leaderMemberId");
        if (leaderMemberId != null) {
            var activeTenantMember = activeTenantMember(systemId, tenantId, leaderMemberId);
            var departmentMember = memberDepartmentMapper.selectCount(Wrappers.<MemberDepartment>lambdaQuery()
                    .eq(MemberDepartment::getScopeType, "SYSTEM")
                    .eq(MemberDepartment::getScopeKey, systemId)
                    .eq(MemberDepartment::getSystemId, systemId)
                    .eq(MemberDepartment::getTenantId, tenantId)
                    .eq(MemberDepartment::getDepartmentId, departmentId)
                    .eq(MemberDepartment::getMemberId, leaderMemberId)) > 0;
            OrganizationReportingPolicy.requireDepartmentLeaderEligible(
                    "ACTIVE".equals(department.getStatus()),
                    activeTenantMember != null,
                    departmentMember
            );
        }

        var before = departmentView(department);
        var current = activeDepartmentLeader(systemId, tenantId, departmentId);
        touchDepartment(session, department);
        replaceDepartmentLeader(session, systemId, tenantId, departmentId, leaderMemberId, current);
        epochService.bumpSystem(systemId, session.accountId());
        var after = departmentView(department);
        mutationSupport.success(
                session, systemId, "DEPARTMENT", after.id(), "SYSTEM_DEPARTMENT_LEADER_UPDATE",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "DEPARTMENT", after.id(), "SYSTEM_DEPARTMENT_LEADER_UPDATED",
                after, client.requestId(), client
        );
        return after;
    }

    public SystemAdminViews.Page<SystemAdminViews.Member> members(
            AuthenticatedSession session,
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        var tenantId = scopeSupport.tenantId(session);
        var tenantEdges = memberTenantMapper.selectList(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getTenantId, tenantId));
        var candidateIds = tenantEdges.stream().map(MemberTenant::getMemberId).collect(Collectors.toSet());
        candidateIds.retainAll(dataScopedMemberIds(session, systemId, tenantId, candidateIds));
        if (candidateIds.isEmpty()) {
            return new SystemAdminViews.Page<>(List.of(), page(page), size(size), 0);
        }
        var query = Wrappers.<Member>lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .in(Member::getId, candidateIds)
                .eq(status != null && !status.isBlank(), Member::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), Member::getDisplayName, keyword == null ? null : keyword.trim())
                .orderByDesc(Member::getCreatedAt);
        var result = memberMapper.selectPage(new Page<>(page(page), size(size)), query);
        var identityVisible = session.permissions().contains("system.member.identity.view");
        var items = result.getRecords().stream()
                .map(member -> memberView(member, tenantId, identityVisible))
                .toList();
        return new SystemAdminViews.Page<>(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Transactional
    public SystemAdminViews.Member updateMember(
            AuthenticatedSession session,
            long systemId,
            long memberId,
            SystemAdminRequests.UpdateMember request,
            ClientRequest client
    ) {
        var tenantId = scopeSupport.tenantId(session);
        var systemWide = scopeSupport.systemWide(session, systemId, "system.member.manage");
        var member = requireMember(systemId, memberId);
        if (!systemWide && memberTenantMapper.selectCount(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getTenantId, tenantId)
                .eq(MemberTenant::getMemberId, memberId)) == 0) {
            throw SystemAdminMutationSupport.notFound();
        }
        requireVersion(member.getVersion(), request.version());
        var system = systemMapper.selectOne(Wrappers.<com.unique.examine.plat.base.entity.System>lambdaQuery()
                .eq(com.unique.examine.plat.base.entity.System::getId, systemId));
        if (system == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        var owner = member.getAccountId().equals(system.getOwnerAccountId());
        if (owner && request.status() != null && !"ACTIVE".equals(request.status())) {
            throw SystemAdminMutationSupport.invalidState("系统所有者成员不能停用");
        }
        if ((request.status() != null || request.tenantIds() != null || request.roleIds() != null) && !systemWide) {
            throw new BusinessException("PERMISSION_DENIED", "该成员变更需要系统级管理员权限", HttpStatus.FORBIDDEN);
        }
        var before = memberView(member, tenantId, true);
        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw new BusinessException("VALIDATION_ERROR", "displayName 不能为空", HttpStatus.BAD_REQUEST);
            }
            member.setDisplayName(request.displayName().trim());
        }
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        member.setUpdatedAt(LocalDateTime.now());
        member.setUpdatedBy(session.accountId());
        if (memberMapper.updateById(member) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        if (request.departmentIds() != null) {
            replaceDepartments(session, systemId, tenantId, memberId, request);
        }
        if (request.tenantIds() != null) {
            if (owner) {
                throw SystemAdminMutationSupport.invalidState("系统所有者的租户授权不能从成员编辑入口修改");
            }
            replaceTenants(session, systemId, member, request.tenantIds());
        }
        if (request.roleIds() != null) {
            if (owner && !containsOwnerRole(systemId, request.roleIds())) {
                throw SystemAdminMutationSupport.invalidState("系统所有者必须保留所有者角色");
            }
            replaceRoles(session, systemId, tenantId, memberId, request.roleIds());
        }
        epochService.bumpSystem(systemId, session.accountId());
        var after = memberView(member, tenantId, true);
        mutationSupport.success(
                session, systemId, "MEMBER", after.id(), "SYSTEM_MEMBER_UPDATE",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "MEMBER", after.id(), "SYSTEM_MEMBER_UPDATED",
                after, client.requestId(), client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Member updateMemberManager(
            AuthenticatedSession session,
            long systemId,
            long memberId,
            SystemAdminRequests.UpdateMemberManager request,
            ClientRequest client
    ) {
        var tenantId = scopeSupport.tenantId(session);
        var member = requireMember(systemId, memberId);
        if (memberTenantMapper.selectCount(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getTenantId, tenantId)
                .eq(MemberTenant::getMemberId, memberId)) == 0) {
            throw SystemAdminMutationSupport.notFound();
        }
        requireVersion(member.getVersion(), request.version());
        if (memberManagerMapper.lockTenant(systemId, tenantId) == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        var managerMemberId = nullableId(request.managerMemberId(), "managerMemberId");
        if (managerMemberId != null) {
            OrganizationReportingPolicy.requireManagerEligible(
                    activeTenantMember(systemId, tenantId, memberId) != null,
                    activeTenantMember(systemId, tenantId, managerMemberId) != null
            );
            requireAcyclicManager(systemId, tenantId, memberId, managerMemberId);
        }

        var before = memberView(member, tenantId, true);
        var current = activeMemberManager(systemId, tenantId, memberId);
        touchMember(session, member);
        replaceMemberManager(session, systemId, tenantId, memberId, managerMemberId, current);
        epochService.bumpSystem(systemId, session.accountId());
        var after = memberView(member, tenantId, true);
        mutationSupport.success(
                session, systemId, "MEMBER", after.id(), "SYSTEM_MEMBER_MANAGER_UPDATE",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "MEMBER", after.id(), "SYSTEM_MEMBER_MANAGER_UPDATED",
                after, client.requestId(), client
        );
        return after;
    }

    private Set<Long> dataScopedMemberIds(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            Set<Long> candidates
    ) {
        var now = LocalDateTime.now();
        var bindings = memberRoleMapper.selectList(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, session.memberId())
                .le(MemberRole::getValidFrom, now)
                .and(query -> query.isNull(MemberRole::getValidUntil).or().gt(MemberRole::getValidUntil, now))
                .and(query -> query.isNull(MemberRole::getTenantId).or().eq(MemberRole::getTenantId, tenantId)));
        if (bindings.isEmpty()) {
            return Set.of();
        }
        var roleIds = bindings.stream().map(MemberRole::getRoleId).distinct().toList();
        var roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .eq(Role::getStatus, "ACTIVE")
                .in(Role::getId, roleIds)
                .and(query -> query.isNull(Role::getTenantId).or().eq(Role::getTenantId, tenantId)));
        var permission = permissionMapper.selectOne(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "SYSTEM")
                .eq(Permission::getScopeKey, systemId)
                .eq(Permission::getSystemId, systemId)
                .eq(Permission::getPermissionCode, "system.member.list")
                .eq(Permission::getStatus, "ACTIVE"));
        if (permission == null || roles.isEmpty()) {
            return Set.of();
        }
        var activeRoleIds = roles.stream().map(Role::getId).toList();
        var links = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "SYSTEM")
                .eq(RolePermission::getScopeKey, systemId)
                .eq(RolePermission::getPermissionId, permission.getId())
                .in(RolePermission::getRoleId, activeRoleIds));
        if (links.stream().anyMatch(link -> "DENY".equals(link.getEffect()))) {
            return Set.of();
        }
        var allowRoleIds = links.stream().filter(link -> "ALLOW".equals(link.getEffect()))
                .map(RolePermission::getRoleId).collect(Collectors.toSet());
        var scopeIds = roles.stream().filter(role -> allowRoleIds.contains(role.getId()))
                .map(Role::getDataScopeId).filter(Objects::nonNull).distinct().toList();
        if (scopeIds.isEmpty()) {
            return Set.of();
        }
        var scopes = dataScopeMapper.selectList(Wrappers.<DataScope>lambdaQuery()
                .eq(DataScope::getScopeType, "SYSTEM")
                .eq(DataScope::getScopeKey, systemId)
                .eq(DataScope::getSystemId, systemId)
                .eq(DataScope::getStatus, "ACTIVE")
                .in(DataScope::getId, scopeIds)
                .and(query -> query.isNull(DataScope::getTenantId).or().eq(DataScope::getTenantId, tenantId)));
        var allowed = new HashSet<Long>();
        for (var scope : scopes) {
            if ("ALL".equals(scope.getScopeKind())) {
                allowed.addAll(candidates);
            } else if ("SELF".equals(scope.getScopeKind())) {
                allowed.add(session.memberId());
            } else if ("SELECTED_MEMBERS".equals(scope.getScopeKind())) {
                allowed.addAll(scopeTargets(scope, "MEMBER").stream()
                        .map(DataScopeTarget::getMemberId).filter(Objects::nonNull).toList());
            } else if ("SELECTED_DEPARTMENTS".equals(scope.getScopeKind())) {
                var departments = scopeTargets(scope, "DEPARTMENT").stream()
                        .map(DataScopeTarget::getDepartmentId).filter(Objects::nonNull).toList();
                allowed.addAll(membersInDepartments(systemId, tenantId, departments));
            } else if ("PRIMARY_DEPARTMENT".equals(scope.getScopeKind())
                    || "DEPARTMENT_TREE".equals(scope.getScopeKind())) {
                var primary = memberDepartmentMapper.selectOne(Wrappers.<MemberDepartment>lambdaQuery()
                        .eq(MemberDepartment::getScopeType, "SYSTEM")
                        .eq(MemberDepartment::getScopeKey, systemId)
                        .eq(MemberDepartment::getSystemId, systemId)
                        .eq(MemberDepartment::getTenantId, tenantId)
                        .eq(MemberDepartment::getMemberId, session.memberId())
                        .eq(MemberDepartment::getIsPrimary, true));
                if (primary != null) {
                    var departments = new ArrayList<Long>();
                    departments.add(primary.getDepartmentId());
                    if ("DEPARTMENT_TREE".equals(scope.getScopeKind())) {
                        closureMapper.selectList(closureQuery(systemId, tenantId)
                                        .eq(DepartmentClosure::getAncestorId, primary.getDepartmentId()))
                                .forEach(path -> departments.add(path.getDescendantId()));
                    }
                    allowed.addAll(membersInDepartments(systemId, tenantId, departments));
                }
            }
        }
        allowed.retainAll(candidates);
        return allowed;
    }

    private List<DataScopeTarget> scopeTargets(DataScope scope, String type) {
        var query = Wrappers.<DataScopeTarget>lambdaQuery()
                .eq(DataScopeTarget::getScopeType, "SYSTEM")
                .eq(DataScopeTarget::getScopeKey, scope.getSystemId())
                .eq(DataScopeTarget::getSystemId, scope.getSystemId())
                .eq(DataScopeTarget::getDataScopeId, scope.getId())
                .eq(DataScopeTarget::getTargetType, type);
        if (scope.getTenantId() == null) {
            query.isNull(DataScopeTarget::getTenantId);
        } else {
            query.eq(DataScopeTarget::getTenantId, scope.getTenantId());
        }
        return dataScopeTargetMapper.selectList(query);
    }

    private Set<Long> membersInDepartments(long systemId, long tenantId, List<Long> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Set.of();
        }
        return memberDepartmentMapper.selectList(Wrappers.<MemberDepartment>lambdaQuery()
                        .eq(MemberDepartment::getScopeType, "SYSTEM")
                        .eq(MemberDepartment::getScopeKey, systemId)
                        .eq(MemberDepartment::getSystemId, systemId)
                        .eq(MemberDepartment::getTenantId, tenantId)
                        .in(MemberDepartment::getDepartmentId, departmentIds))
                .stream().map(MemberDepartment::getMemberId).collect(Collectors.toSet());
    }

    private void replaceDepartments(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            long memberId,
            SystemAdminRequests.UpdateMember request
    ) {
        var departmentIds = parseIds(request.departmentIds(), "departmentIds");
        if (!departmentIds.isEmpty()) {
            var count = departmentMapper.selectCount(Wrappers.<Department>lambdaQuery()
                    .eq(Department::getScopeType, "SYSTEM")
                    .eq(Department::getScopeKey, systemId)
                    .eq(Department::getSystemId, systemId)
                    .eq(Department::getTenantId, tenantId)
                    .in(Department::getId, departmentIds));
            if (count != departmentIds.size()) {
                throw SystemAdminMutationSupport.notFound();
            }
        }
        var primaryId = nullableId(request.primaryDepartmentId(), "primaryDepartmentId");
        if (primaryId != null && !departmentIds.contains(primaryId)) {
            throw new BusinessException(
                    "VALIDATION_ERROR", "primaryDepartmentId 必须包含在 departmentIds 中", HttpStatus.BAD_REQUEST
            );
        }
        memberDepartmentMapper.delete(Wrappers.<MemberDepartment>lambdaQuery()
                .eq(MemberDepartment::getScopeType, "SYSTEM")
                .eq(MemberDepartment::getScopeKey, systemId)
                .eq(MemberDepartment::getSystemId, systemId)
                .eq(MemberDepartment::getTenantId, tenantId)
                .eq(MemberDepartment::getMemberId, memberId));
        var now = LocalDateTime.now();
        for (var departmentId : departmentIds) {
            var edge = new MemberDepartment();
            edge.setId(idService.nextId());
            edge.setScopeType("SYSTEM");
            edge.setScopeKey(systemId);
            edge.setSystemId(systemId);
            edge.setTenantId(tenantId);
            edge.setMemberId(memberId);
            edge.setDepartmentId(departmentId);
            edge.setIsPrimary(departmentId.equals(primaryId));
            edge.setCreatedAt(now);
            edge.setCreatedBy(session.accountId());
            edge.setUpdatedAt(now);
            edge.setUpdatedBy(session.accountId());
            edge.setVersion(0L);
            memberDepartmentMapper.insert(edge);
        }
    }

    private void replaceTenants(
            AuthenticatedSession session,
            long systemId,
            Member member,
            List<String> values
    ) {
        var tenantIds = parseIds(values, "tenantIds");
        if ("ACTIVE".equals(member.getStatus()) && tenantIds.isEmpty()) {
            throw SystemAdminMutationSupport.invalidState("活动成员至少需要一个活动租户");
        }
        if (!tenantIds.isEmpty() && tenantMapper.selectCount(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getSystemId, systemId)
                .eq(Tenant::getStatus, "ACTIVE")
                .in(Tenant::getId, tenantIds)) != tenantIds.size()) {
            throw SystemAdminMutationSupport.notFound();
        }
        var now = LocalDateTime.now();
        var existing = memberTenantMapper.selectList(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getMemberId, member.getId()));
        var byTenant = existing.stream().collect(Collectors.toMap(MemberTenant::getTenantId, Function.identity()));
        for (var edge : existing) {
            edge.setStatus(tenantIds.contains(edge.getTenantId()) ? "ACTIVE" : "DISABLED");
            edge.setExpiresAt(null);
            edge.setUpdatedAt(now);
            edge.setUpdatedBy(session.accountId());
            if (memberTenantMapper.updateById(edge) != 1) {
                throw SystemAdminMutationSupport.versionConflict();
            }
        }
        for (var tenantId : tenantIds) {
            if (byTenant.containsKey(tenantId)) {
                continue;
            }
            var edge = new MemberTenant();
            edge.setId(idService.nextId());
            edge.setSystemId(systemId);
            edge.setMemberId(member.getId());
            edge.setTenantId(tenantId);
            edge.setStatus("ACTIVE");
            edge.setGrantedAt(now);
            edge.setGrantedBy(session.accountId());
            edge.setCreatedAt(now);
            edge.setCreatedBy(session.accountId());
            edge.setUpdatedAt(now);
            edge.setUpdatedBy(session.accountId());
            edge.setVersion(0L);
            memberTenantMapper.insert(edge);
        }
        if (!tenantIds.isEmpty() && !tenantIds.contains(member.getDefaultTenantId())) {
            member.setDefaultTenantId(tenantIds.iterator().next());
            member.setUpdatedAt(now);
            member.setUpdatedBy(session.accountId());
            if (memberMapper.updateById(member) != 1) {
                throw SystemAdminMutationSupport.versionConflict();
            }
        }
    }

    private void replaceRoles(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            long memberId,
            List<String> values
    ) {
        var roleIds = parseIds(values, "roleIds");
        var roles = roleIds.isEmpty() ? List.<Role>of() : roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .eq(Role::getStatus, "ACTIVE")
                .in(Role::getId, roleIds)
                .and(query -> query.isNull(Role::getTenantId).or().eq(Role::getTenantId, tenantId)));
        if (roles.size() != roleIds.size()) {
            throw SystemAdminMutationSupport.notFound();
        }
        var now = LocalDateTime.now();
        var existing = memberRoleMapper.selectList(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, memberId)
                .and(query -> query.isNull(MemberRole::getTenantId).or().eq(MemberRole::getTenantId, tenantId)));
        var byRole = existing.stream().collect(Collectors.toMap(MemberRole::getRoleId, Function.identity()));
        for (var edge : existing) {
            if (!roleIds.contains(edge.getRoleId()) && edge.getValidUntil() == null) {
                edge.setValidUntil(now);
                memberRoleMapper.updateById(edge);
            }
        }
        for (var role : roles) {
            var edge = byRole.get(role.getId());
            if (edge == null) {
                edge = new MemberRole();
                edge.setId(idService.nextId());
                edge.setSystemId(systemId);
                edge.setMemberId(memberId);
                edge.setRoleId(role.getId());
                edge.setTenantId(role.getTenantId());
                edge.setValidFrom(now);
                edge.setCreatedAt(now);
                edge.setCreatedBy(session.accountId());
                memberRoleMapper.insert(edge);
            } else if (edge.getValidUntil() != null) {
                edge.setValidFrom(now);
                edge.setValidUntil(null);
                memberRoleMapper.updateById(edge);
            }
        }
    }

    private boolean containsOwnerRole(long systemId, List<String> values) {
        var roleIds = parseIds(values, "roleIds");
        return roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .eq(Role::getRoleCode, "system_owner")
                .in(!roleIds.isEmpty(), Role::getId, roleIds)) > 0;
    }

    private void rebuildClosure(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            long departmentId,
            Long parentId
    ) {
        var descendants = closureMapper.selectList(closureQuery(systemId, tenantId)
                .eq(DepartmentClosure::getAncestorId, departmentId));
        var oldAncestors = closureMapper.selectList(closureQuery(systemId, tenantId)
                .eq(DepartmentClosure::getDescendantId, departmentId)
                .ne(DepartmentClosure::getAncestorId, departmentId));
        if (!oldAncestors.isEmpty() && !descendants.isEmpty()) {
            closureMapper.delete(closureQuery(systemId, tenantId)
                    .in(DepartmentClosure::getAncestorId, oldAncestors.stream().map(DepartmentClosure::getAncestorId).toList())
                    .in(DepartmentClosure::getDescendantId, descendants.stream().map(DepartmentClosure::getDescendantId).toList()));
        }
        if (parentId == null) {
            return;
        }
        var newAncestors = closureMapper.selectList(closureQuery(systemId, tenantId)
                .eq(DepartmentClosure::getDescendantId, parentId));
        var now = LocalDateTime.now();
        for (var ancestor : newAncestors) {
            for (var descendant : descendants) {
                insertClosure(
                        systemId, tenantId, ancestor.getAncestorId(), descendant.getDescendantId(),
                        ancestor.getDepth() + 1 + descendant.getDepth(), session.accountId(), now
                );
            }
        }
    }

    private void insertClosure(
            long systemId,
            long tenantId,
            long ancestorId,
            long descendantId,
            int depth,
            long actorAccountId,
            LocalDateTime now
    ) {
        var closure = new DepartmentClosure();
        closure.setId(idService.nextId());
        closure.setScopeType("SYSTEM");
        closure.setScopeKey(systemId);
        closure.setTenantId(tenantId);
        closure.setAncestorId(ancestorId);
        closure.setDescendantId(descendantId);
        closure.setDepth(depth);
        closure.setCreatedAt(now);
        closure.setCreatedBy(actorAccountId);
        closureMapper.insert(closure);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DepartmentClosure> closureQuery(
            long systemId,
            long tenantId
    ) {
        return Wrappers.<DepartmentClosure>lambdaQuery()
                .eq(DepartmentClosure::getScopeType, "SYSTEM")
                .eq(DepartmentClosure::getScopeKey, systemId)
                .eq(DepartmentClosure::getTenantId, tenantId);
    }

    private Department requireDepartment(long systemId, long tenantId, long departmentId) {
        var department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                .eq(Department::getId, departmentId)
                .eq(Department::getScopeType, "SYSTEM")
                .eq(Department::getScopeKey, systemId)
                .eq(Department::getSystemId, systemId)
                .eq(Department::getTenantId, tenantId));
        if (department == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return department;
    }

    private Member requireMember(long systemId, long memberId) {
        var member = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getId, memberId)
                .eq(Member::getSystemId, systemId));
        if (member == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return member;
    }

    private Member activeTenantMember(
            long systemId,
            long tenantId,
            long memberId
    ) {
        var member = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getId, memberId)
                .eq(Member::getSystemId, systemId)
                .eq(Member::getStatus, "ACTIVE"));
        var now = LocalDateTime.now();
        var tenantMember = member == null ? null : memberTenantMapper.selectOne(
                Wrappers.<MemberTenant>lambdaQuery()
                        .eq(MemberTenant::getSystemId, systemId)
                        .eq(MemberTenant::getTenantId, tenantId)
                        .eq(MemberTenant::getMemberId, memberId)
                        .eq(MemberTenant::getStatus, "ACTIVE")
                        .and(query -> query.isNull(MemberTenant::getExpiresAt)
                                .or().gt(MemberTenant::getExpiresAt, now))
        );
        return tenantMember == null ? null : member;
    }

    private void requireAcyclicManager(
            long systemId,
            long tenantId,
            long memberId,
            long managerMemberId
    ) {
        OrganizationReportingPolicy.requireAcyclic(
                memberId,
                managerMemberId,
                cursor -> {
                    var assignment = activeMemberManager(systemId, tenantId, cursor);
                    return assignment == null ? null : assignment.getManagerMemberId();
                }
        );
    }

    private void touchDepartment(AuthenticatedSession session, Department department) {
        department.setUpdatedAt(LocalDateTime.now());
        department.setUpdatedBy(session.accountId());
        if (departmentMapper.updateById(department) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private void touchMember(AuthenticatedSession session, Member member) {
        member.setUpdatedAt(LocalDateTime.now());
        member.setUpdatedBy(session.accountId());
        if (memberMapper.updateById(member) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private DepartmentLeaderAssignment activeDepartmentLeader(
            long systemId,
            long tenantId,
            long departmentId
    ) {
        return departmentLeaderMapper.selectOne(
                Wrappers.<DepartmentLeaderAssignment>lambdaQuery()
                        .eq(DepartmentLeaderAssignment::getSystemId, systemId)
                        .eq(DepartmentLeaderAssignment::getTenantId, tenantId)
                        .eq(DepartmentLeaderAssignment::getDepartmentId, departmentId)
                        .eq(DepartmentLeaderAssignment::getStatus, "ACTIVE")
        );
    }

    private MemberManagerAssignment activeMemberManager(
            long systemId,
            long tenantId,
            long memberId
    ) {
        return memberManagerMapper.selectOne(
                Wrappers.<MemberManagerAssignment>lambdaQuery()
                        .eq(MemberManagerAssignment::getSystemId, systemId)
                        .eq(MemberManagerAssignment::getTenantId, tenantId)
                        .eq(MemberManagerAssignment::getMemberId, memberId)
                        .eq(MemberManagerAssignment::getStatus, "ACTIVE")
        );
    }

    private void replaceDepartmentLeader(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            long departmentId,
            Long leaderMemberId,
            DepartmentLeaderAssignment current
    ) {
        if (current != null && Objects.equals(current.getLeaderMemberId(), leaderMemberId)) {
            return;
        }
        var now = LocalDateTime.now();
        if (current != null && departmentLeaderMapper.clearActive(
                systemId,
                tenantId,
                current.getAssignmentId(),
                current.getVersion(),
                session.accountId(),
                now
        ) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        if (leaderMemberId == null) {
            return;
        }
        var assignment = new DepartmentLeaderAssignment();
        assignment.setSystemId(systemId);
        assignment.setTenantId(tenantId);
        assignment.setAssignmentId(idService.nextId());
        assignment.setDepartmentId(departmentId);
        assignment.setLeaderMemberId(leaderMemberId);
        assignment.setStatus("ACTIVE");
        assignment.setAssignedBy(session.accountId());
        assignment.setAssignedAt(now);
        assignment.setVersion(0L);
        try {
            departmentLeaderMapper.insert(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private void replaceMemberManager(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            long memberId,
            Long managerMemberId,
            MemberManagerAssignment current
    ) {
        if (current != null && Objects.equals(current.getManagerMemberId(), managerMemberId)) {
            return;
        }
        var now = LocalDateTime.now();
        if (current != null && memberManagerMapper.clearActive(
                systemId,
                tenantId,
                current.getAssignmentId(),
                current.getVersion(),
                session.accountId(),
                now
        ) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        if (managerMemberId == null) {
            return;
        }
        var assignment = new MemberManagerAssignment();
        assignment.setSystemId(systemId);
        assignment.setTenantId(tenantId);
        assignment.setAssignmentId(idService.nextId());
        assignment.setMemberId(memberId);
        assignment.setManagerMemberId(managerMemberId);
        assignment.setStatus("ACTIVE");
        assignment.setAssignedBy(session.accountId());
        assignment.setAssignedAt(now);
        assignment.setVersion(0L);
        try {
            memberManagerMapper.insert(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private SystemAdminViews.Department departmentView(Department department) {
        var memberCount = memberDepartmentMapper.selectCount(Wrappers.<MemberDepartment>lambdaQuery()
                .eq(MemberDepartment::getScopeType, "SYSTEM")
                .eq(MemberDepartment::getScopeKey, department.getSystemId())
                .eq(MemberDepartment::getSystemId, department.getSystemId())
                .eq(MemberDepartment::getTenantId, department.getTenantId())
                .eq(MemberDepartment::getDepartmentId, department.getId()));
        var leader = activeDepartmentLeader(
                department.getSystemId(),
                department.getTenantId(),
                department.getId()
        );
        return new SystemAdminViews.Department(
                Long.toString(department.getId()), string(department.getParentId()), department.getName(),
                department.getDepartmentCode(), department.getStatus(), memberCount,
                Long.toString(department.getVersion()),
                leader == null ? null : Long.toString(leader.getLeaderMemberId())
        );
    }

    private SystemAdminViews.Member memberView(Member member, long tenantId, boolean identityVisible) {
        Account account = identityVisible ? accountMapper.selectById(member.getAccountId()) : null;
        var departments = memberDepartmentMapper.selectList(Wrappers.<MemberDepartment>lambdaQuery()
                .eq(MemberDepartment::getScopeType, "SYSTEM")
                .eq(MemberDepartment::getScopeKey, member.getSystemId())
                .eq(MemberDepartment::getSystemId, member.getSystemId())
                .eq(MemberDepartment::getTenantId, tenantId)
                .eq(MemberDepartment::getMemberId, member.getId()));
        var tenantIds = memberTenantMapper.selectList(Wrappers.<MemberTenant>lambdaQuery()
                        .eq(MemberTenant::getSystemId, member.getSystemId())
                        .eq(MemberTenant::getMemberId, member.getId())
                        .eq(MemberTenant::getStatus, "ACTIVE"))
                .stream().map(edge -> Long.toString(edge.getTenantId())).toList();
        var now = LocalDateTime.now();
        var roleIds = memberRoleMapper.selectList(Wrappers.<MemberRole>lambdaQuery()
                        .eq(MemberRole::getSystemId, member.getSystemId())
                        .eq(MemberRole::getMemberId, member.getId())
                        .le(MemberRole::getValidFrom, now)
                        .and(query -> query.isNull(MemberRole::getValidUntil).or().gt(MemberRole::getValidUntil, now))
                        .and(query -> query.isNull(MemberRole::getTenantId).or().eq(MemberRole::getTenantId, tenantId)))
                .stream().map(edge -> Long.toString(edge.getRoleId())).distinct().toList();
        var primary = departments.stream().filter(edge -> Boolean.TRUE.equals(edge.getIsPrimary())).findFirst().orElse(null);
        var manager = activeMemberManager(member.getSystemId(), tenantId, member.getId());
        return new SystemAdminViews.Member(
                Long.toString(member.getId()), Long.toString(member.getAccountId()),
                account == null ? null : account.getUsername(), member.getDisplayName(),
                account == null ? null : account.getEmail(), member.getStatus(),
                primary == null ? null : Long.toString(primary.getDepartmentId()),
                departments.stream().map(edge -> Long.toString(edge.getDepartmentId())).toList(),
                tenantIds, roleIds, Long.toString(member.getVersion()),
                manager == null ? null : Long.toString(manager.getManagerMemberId())
        );
    }

    private static Set<Long> parseIds(List<String> values, String field) {
        if (values == null) {
            return Set.of();
        }
        var result = new LinkedHashSet<Long>();
        for (var value : values) {
            result.add(SystemAdminMutationSupport.id(value, field));
        }
        return result;
    }

    private static Long nullableId(String value, String field) {
        return value == null || value.isBlank() ? null : SystemAdminMutationSupport.id(value, field);
    }

    private static void requireVersion(long actual, String expected) {
        if (actual != SystemAdminMutationSupport.version(expected)) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static int page(int value) {
        return Math.max(value, 1);
    }

    private static int size(int value) {
        return Math.min(Math.max(value, 1), 100);
    }
}
