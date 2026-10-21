package com.unique.unexamine.module.manage;

import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysRoleData;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysMemberRoleService;
import com.unique.unexamine.sys.base.service.IUnSysRoleDataService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleTeamer;
import com.unique.unexamine.module.base.service.IUnModuleTeamerService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** 按租户角色数据范围、记录负责人和团队关系判定模块记录的可读写范围。 */
@Component
public class ModuleRecordAccess {
    private final ModuleSupport support;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysRoleService roles;
    private final IUnSysRoleDataService roleData;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysDepartmentService departments;
    private final IUnModuleTeamerService teamers;

    public ModuleRecordAccess(ModuleSupport support, IUnSysMemberRoleService memberRoles, IUnSysRoleService roles,
                              IUnSysRoleDataService roleData, IUnSysTenantMemberService tenantMembers,
                              IUnSysDepartmentService departments, IUnModuleTeamerService teamers) {
        this.support = support;
        this.memberRoles = memberRoles;
        this.roles = roles;
        this.roleData = roleData;
        this.tenantMembers = tenantMembers;
        this.departments = departments;
        this.teamers = teamers;
    }

    /** 角色的数据范围求并集；无角色时仍能访问本人创建、负责和参与的记录。 */
    public Scope scope(HttpServletRequest request, long tenantId) {
        UnSysTenantMember actor = support.member(request, tenantId);
        Set<Long> memberIds = new LinkedHashSet<>();
        memberIds.add(actor.getMemberId());
        Set<Long> departmentIds = new LinkedHashSet<>();
        boolean all = false;
        List<UnSysTenantMember> allMembers = null;
        List<UnSysDepartment> allDepartments = null;
        for (UnSysMemberRole grant : memberRoles.list(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getTenantMemberId, actor.getId()))) {
            UnSysRole role = roles.getById(grant.getRoleId());
            if (role == null || !role.getTenantId().equals(tenantId) || !ModuleSupport.enabled(role.getStatus())) continue;
            if (role.getRoleType() != null && role.getRoleType() == 0) {
                all = true;
                break;
            }
            for (UnSysRoleData rule : roleData.list(new LambdaQueryWrapper<UnSysRoleData>()
                    .eq(UnSysRoleData::getRoleId, role.getId()))) {
                switch (rule.getScopeType()) {
                    case 1 -> { }
                    case 2 -> {
                        if (allMembers == null) allMembers = tenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                                .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getStatus, 1));
                        addSubordinates(actor.getId(), allMembers, memberIds);
                    }
                    case 3 -> { if (actor.getDepartmentId() != null) departmentIds.add(actor.getDepartmentId()); }
                    case 4 -> {
                        if (actor.getDepartmentId() != null) {
                            if (allDepartments == null) allDepartments = departments.list(new LambdaQueryWrapper<UnSysDepartment>()
                                    .eq(UnSysDepartment::getTenantId, tenantId).eq(UnSysDepartment::getStatus, 1));
                            addDepartmentTree(actor.getDepartmentId(), allDepartments, departmentIds);
                        }
                    }
                    case 5 -> all = true;
                    case 6 -> {
                        if (rule.getDepartmentId() != null && rule.getDepartmentId() > 0) {
                            UnSysDepartment selected = departments.getById(rule.getDepartmentId());
                            if (selected != null && selected.getTenantId().equals(tenantId) && ModuleSupport.enabled(selected.getStatus()))
                                departmentIds.add(selected.getId());
                        }
                    }
                    default -> { }
                }
            }
        }
        return new Scope(tenantId, actor.getId(), actor.getMemberId(), all, memberIds, departmentIds);
    }

    /** 读权限包括数据范围与团队参与；所有记录先通过租户和模块归属检查。 */
    public void requireRead(Scope scope, UnModuleRecord record) {
        if (scope.all() || scope.memberIds().contains(record.getOwnerMemberId())
                || scope.memberIds().contains(record.getCreateMemberId())
                || scope.departmentIds().contains(record.getOwnerDepartmentId())
                || teamers.count(new LambdaQueryWrapper<UnModuleTeamer>()
                .eq(UnModuleTeamer::getTenantId, scope.tenantId()).eq(UnModuleTeamer::getRecordId, record.getId())
                .eq(UnModuleTeamer::getMemberId, scope.memberId())) > 0) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此记录的读取权限");
    }

    /** 写入限定管理员、本人创建或负责的记录，以及明确授予编辑角色的团队成员。 */
    public void requireWrite(Scope scope, UnModuleRecord record) {
        if (scope.all() && isAdmin(scope)) return;
        if (Objects.equals(record.getOwnerMemberId(), scope.memberId())
                || Objects.equals(record.getCreateMemberId(), scope.memberId())) return;
        if (teamers.count(new LambdaQueryWrapper<UnModuleTeamer>()
                .eq(UnModuleTeamer::getTenantId, scope.tenantId()).eq(UnModuleTeamer::getRecordId, record.getId())
                .eq(UnModuleTeamer::getMemberId, scope.memberId()).in(UnModuleTeamer::getRoleCode, List.of("editor", "manager"))) > 0)
            return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此记录的修改权限");
    }

    /** 新建时不能把负责人设置成别人，除非当前会话有租户管理员角色。 */
    public void requireOwnerAssignment(Scope scope, Long ownerMemberId) {
        if (ownerMemberId != null && !ownerMemberId.equals(scope.memberId()) && !isAdmin(scope))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能替其他成员指定记录负责人");
    }

    /** 已有记录的负责人变更只交给租户管理员，避免团队编辑者把记录转给自己。 */
    public void requireOwnerTransfer(Scope scope) {
        if (!isAdmin(scope)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能变更记录负责人");
    }

    /** 负责部门影响角色数据范围，普通成员只能使用本人部门或保留原部门。 */
    public void requireDepartmentAssignment(Scope scope, Long departmentId, UnModuleRecord existing,
                                            Long actorDepartmentId) {
        if (departmentId == null) return;
        UnSysDepartment department = departments.getById(departmentId);
        if (department == null || !department.getTenantId().equals(scope.tenantId())
                || !ModuleSupport.enabled(department.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责部门不属于当前租户或已停用");
        Long currentDepartmentId = existing == null ? null : existing.getOwnerDepartmentId();
        if (!departmentId.equals(currentDepartmentId) && !isAdmin(scope)
                && (!departmentId.equals(actorDepartmentId) || existing != null
                && !Objects.equals(existing.getOwnerMemberId(), scope.memberId())
                && !Objects.equals(existing.getCreateMemberId(), scope.memberId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能指定其他部门为负责部门");
    }

    /** 同一 SQL 范围用于 COUNT 和分页，避免总数与列表权限不一致。 */
    public void appendReadPredicate(StringBuilder where, List<Object> params, Scope scope) {
        if (scope.all()) return;
        where.append(" AND (r.owner_member_id IN (");
        appendIds(where, params, scope.memberIds());
        where.append(") OR r.create_member_id IN (");
        appendIds(where, params, scope.memberIds());
        where.append(')');
        if (!scope.departmentIds().isEmpty()) {
            where.append(" OR r.owner_department_id IN (");
            appendIds(where, params, scope.departmentIds());
            where.append(')');
        }
        where.append(" OR EXISTS (SELECT 1 FROM un_module_teamer team WHERE team.tenant_id=r.tenant_id")
                .append(" AND team.record_id=r.id AND team.member_id=?))");
        params.add(scope.memberId());
    }

    private boolean isAdmin(Scope scope) {
        for (UnSysMemberRole grant : memberRoles.list(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getTenantMemberId, scope.tenantMemberId()))) {
            UnSysRole role = roles.getById(grant.getRoleId());
            if (role != null && role.getTenantId().equals(scope.tenantId()) && ModuleSupport.enabled(role.getStatus())
                    && role.getRoleType() != null && role.getRoleType() == 0) return true;
        }
        return false;
    }

    private void addSubordinates(long leaderId, List<UnSysTenantMember> members, Set<Long> ids) {
        Set<Long> seen = new HashSet<>();
        seen.add(leaderId);
        boolean changed;
        do {
            changed = false;
            for (UnSysTenantMember member : members) {
                if (member.getLeaderTenantMemberId() != null && seen.contains(member.getLeaderTenantMemberId())
                        && seen.add(member.getId())) {
                    ids.add(member.getMemberId());
                    changed = true;
                }
            }
        } while (changed);
    }

    private void addDepartmentTree(long rootId, List<UnSysDepartment> all, Set<Long> ids) {
        ids.add(rootId);
        boolean changed;
        do {
            changed = false;
            for (UnSysDepartment department : all)
                if (department.getParentId() != null && ids.contains(department.getParentId()))
                    changed |= ids.add(department.getId());
        } while (changed);
    }

    private void appendIds(StringBuilder sql, List<Object> params, Set<Long> ids) {
        boolean first = true;
        for (Long id : ids) {
            if (!first) sql.append(',');
            sql.append('?');
            params.add(id);
            first = false;
        }
    }

    public record Scope(long tenantId, long tenantMemberId, long memberId, boolean all,
                        Set<Long> memberIds, Set<Long> departmentIds) { }
}
