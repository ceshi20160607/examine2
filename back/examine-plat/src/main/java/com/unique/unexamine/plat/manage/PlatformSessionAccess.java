package com.unique.unexamine.plat.manage;

import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 校验当前平台账号的服务端会话和状态。 */
@Component
public class PlatformSessionAccess {
    private final IUnPlatAccountService platformAccounts;
    private final IUnPlatMemberService platformMembers;
    private final IUnPlatMemberRoleService platformMemberRoles;
    private final IUnPlatRoleService platformRoles;

    public PlatformSessionAccess(IUnPlatAccountService platformAccounts, IUnPlatMemberService platformMembers,
                                 IUnPlatMemberRoleService platformMemberRoles, IUnPlatRoleService platformRoles) {
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformMemberRoles = platformMemberRoles;
        this.platformRoles = platformRoles;
    }

    /** 平台接口每次重新检查账号状态，停用后旧会话立即失效。 */
    public UnPlatAccount platformAccount(HttpServletRequest request) {
        Long id = sessionId(request, "platformAccountId");
        UnPlatAccount account = platformAccounts.getById(id);
        if (account == null || !enabled(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "平台账号已失效");
        }
        return account;
    }

    /** 管理平台配置需要显式的平台管理员角色。 */
    public UnPlatAccount platformAdmin(HttpServletRequest request) {
        UnPlatAccount account = platformAccount(request);
        UnPlatMember member = platformMembers.getOne(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getAccountId, account.getId()).eq(UnPlatMember::getStatus, 1));
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有平台管理员权限");
        }
        for (UnPlatMemberRole relation : platformMemberRoles.list(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getMemberId, member.getId()))) {
            UnPlatRole role = platformRoles.getById(relation.getRoleId());
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
