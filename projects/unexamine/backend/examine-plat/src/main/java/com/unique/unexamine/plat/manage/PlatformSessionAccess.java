package com.unique.unexamine.plat.manage;

import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberRoleMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 校验当前平台账号的服务端会话和状态。 */
@Component
public class PlatformSessionAccess {
    private final UnPlatAccountMapper platformAccounts;
    private final UnPlatMemberMapper platformMembers;
    private final UnPlatMemberRoleMapper platformMemberRoles;
    private final UnPlatRoleMapper platformRoles;

    public PlatformSessionAccess(UnPlatAccountMapper platformAccounts, UnPlatMemberMapper platformMembers,
                                 UnPlatMemberRoleMapper platformMemberRoles, UnPlatRoleMapper platformRoles) {
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformMemberRoles = platformMemberRoles;
        this.platformRoles = platformRoles;
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
