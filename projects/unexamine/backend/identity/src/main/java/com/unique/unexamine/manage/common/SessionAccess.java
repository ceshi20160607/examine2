package com.unique.unexamine.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.UnPlatAccount;
import com.unique.unexamine.base.entity.UnPlatMember;
import com.unique.unexamine.base.entity.UnPlatMemberRole;
import com.unique.unexamine.base.entity.UnPlatRole;
import com.unique.unexamine.base.entity.UnSysMember;
import com.unique.unexamine.base.entity.UnSysMemberRole;
import com.unique.unexamine.base.entity.UnSysRole;
import com.unique.unexamine.base.entity.UnSysSystem;
import com.unique.unexamine.base.entity.UnSysTenant;
import com.unique.unexamine.base.entity.UnSysTenantMember;
import com.unique.unexamine.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.base.mapper.UnPlatMemberRoleMapper;
import com.unique.unexamine.base.mapper.UnPlatRoleMapper;
import com.unique.unexamine.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.base.mapper.UnSysMemberRoleMapper;
import com.unique.unexamine.base.mapper.UnSysRoleMapper;
import com.unique.unexamine.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMemberMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 从服务端会话核对当前账号、系统和租户身份，供业务入口共用。 */
@Component
public class SessionAccess {
    private final UnPlatAccountMapper platformAccounts;
    private final UnPlatMemberMapper platformMembers;
    private final UnPlatMemberRoleMapper platformMemberRoles;
    private final UnPlatRoleMapper platformRoles;
    private final UnSysMemberMapper systemMembers;
    private final UnSysSystemMapper systems;
    private final UnSysTenantMapper tenants;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnSysMemberRoleMapper tenantMemberRoles;
    private final UnSysRoleMapper tenantRoles;

    public SessionAccess(UnPlatAccountMapper platformAccounts, UnPlatMemberMapper platformMembers,
                         UnPlatMemberRoleMapper platformMemberRoles, UnPlatRoleMapper platformRoles,
                         UnSysMemberMapper systemMembers, UnSysSystemMapper systems,
                         UnSysTenantMapper tenants, UnSysTenantMemberMapper tenantMembers,
                         UnSysMemberRoleMapper tenantMemberRoles, UnSysRoleMapper tenantRoles) {
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformMemberRoles = platformMemberRoles;
        this.platformRoles = platformRoles;
        this.systemMembers = systemMembers;
        this.systems = systems;
        this.tenants = tenants;
        this.tenantMembers = tenantMembers;
        this.tenantMemberRoles = tenantMemberRoles;
        this.tenantRoles = tenantRoles;
    }

    /** 平台接口每次重新检查账号状态，停用后旧会话立即失效。 */
    public UnPlatAccount platformAccount(HttpServletRequest request) {
        Long id = sessionId(request, "platformAccountId");
        UnPlatAccount account = platformAccounts.selectById(id);
        if (account == null || !enabled(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "平台账号已失效");
        }
        return account;
    }

    /** 管理平台配置需要显式的平台管理员角色。 */
    public UnPlatAccount platformAdmin(HttpServletRequest request) {
        UnPlatAccount account = platformAccount(request);
        UnPlatMember member = platformMembers.selectOne(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getAccountId, account.getId()).eq(UnPlatMember::getStatus, 1));
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有平台管理员权限");
        }
        for (UnPlatMemberRole relation : platformMemberRoles.selectList(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getMemberId, member.getId()))) {
            UnPlatRole role = platformRoles.selectById(relation.getRoleId());
            if (role != null && role.getRoleType() != null && role.getRoleType() == 0 && enabled(role.getStatus())) {
                return account;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有平台管理员权限");
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
