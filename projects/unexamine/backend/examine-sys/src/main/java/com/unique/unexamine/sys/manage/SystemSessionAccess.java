package com.unique.unexamine.sys.manage;

import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysSystem;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.sys.base.mapper.UnSysMemberRoleMapper;
import com.unique.unexamine.sys.base.mapper.UnSysRoleMapper;
import com.unique.unexamine.sys.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 校验当前系统成员与租户的服务端会话和状态。 */
@Component
public class SystemSessionAccess {
    private final UnSysMemberMapper systemMembers;
    private final UnSysSystemMapper systems;
    private final UnSysTenantMapper tenants;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnSysMemberRoleMapper tenantMemberRoles;
    private final UnSysRoleMapper tenantRoles;

    public SystemSessionAccess(UnSysMemberMapper systemMembers, UnSysSystemMapper systems,
                               UnSysTenantMapper tenants, UnSysTenantMemberMapper tenantMembers,
                               UnSysMemberRoleMapper tenantMemberRoles, UnSysRoleMapper tenantRoles) {
        this.systemMembers = systemMembers;
        this.systems = systems;
        this.tenants = tenants;
        this.tenantMembers = tenantMembers;
        this.tenantMemberRoles = tenantMemberRoles;
        this.tenantRoles = tenantRoles;
    }

    /** 系统成员必须仍属于启用的业务系统。 */
    public UnSysMember systemMember(HttpServletRequest request) {
        Long id = sessionId(request, "systemMemberId");
        UnSysMember member = systemMembers.selectById(id);
        if (member == null || !enabled(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "系统成员会话已失效");
        }
        UnSysSystem system = systems.selectById(member.getSystemId());
        if (system == null || !enabled(system.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "业务系统已停用");
        }
        return member;
    }

    /** 租户身份由服务端成员关系决定，不能依赖请求提交的成员或系统编号。 */
    public UnSysTenantMember tenantMember(HttpServletRequest request, long tenantId) {
        UnSysMember member = systemMember(request);
        UnSysTenant tenant = tenants.selectById(tenantId);
        if (tenant == null || !enabled(tenant.getStatus()) || !tenant.getSystemId().equals(member.getSystemId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不可访问此租户");
        }
        UnSysTenantMember relation = tenantMembers.selectOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, 1));
        if (relation == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不是此租户的有效成员");
        }
        return relation;
    }

    /** 字段、模块及业务配置写入仅允许该租户的启用管理员角色。 */
    public UnSysTenantMember tenantAdmin(HttpServletRequest request, long tenantId) {
        UnSysTenantMember member = tenantMember(request, tenantId);
        for (UnSysMemberRole relation : tenantMemberRoles.selectList(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getTenantMemberId, member.getId()))) {
            UnSysRole role = tenantRoles.selectById(relation.getRoleId());
            if (role != null && role.getTenantId().equals(tenantId)
                    && role.getRoleType() != null && role.getRoleType() == 0 && enabled(role.getStatus())) {
                return member;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此租户的管理权限");
    }

    /** 会话 ID 只从服务器保存的值读取，缺失时返回统一未登录状态。 */
    private Long sessionId(HttpServletRequest request, String name) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(name);
        if (id instanceof Long value) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    private boolean enabled(Byte status) {
        return status != null && status == 1;
    }
}
