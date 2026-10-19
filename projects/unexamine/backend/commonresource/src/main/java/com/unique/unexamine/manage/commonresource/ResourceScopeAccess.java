package com.unique.unexamine.manage.commonresource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.UnPlatMember;
import com.unique.unexamine.base.entity.UnSysMember;
import com.unique.unexamine.base.entity.UnSysSystem;
import com.unique.unexamine.base.entity.UnSysTenant;
import com.unique.unexamine.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 公共资源在平台、系统和租户之间复用时的范围校验。 */
@Component
public class ResourceScopeAccess {
    private final SessionAccess access;
    private final UnPlatMemberMapper platformMembers;
    private final UnSysSystemMapper systems;
    private final UnSysTenantMapper tenants;

    public ResourceScopeAccess(SessionAccess access, UnPlatMemberMapper platformMembers,
                               UnSysSystemMapper systems, UnSysTenantMapper tenants) {
        this.access = access;
        this.platformMembers = platformMembers;
        this.systems = systems;
        this.tenants = tenants;
    }

    /** 读取平台资源需平台登录；系统与租户资源需所属成员或平台管理员。 */
    public void read(String type, long id, HttpServletRequest request) {
        validate(type, id);
        if ("platform".equals(type)) {
            access.platformAccount(request);
        } else if (hasPlatformSession(request)) {
            access.platformAdmin(request);
        } else if ("system".equals(type)) {
            UnSysMember member = access.systemMember(request);
            if (member.getSystemId() != id) throw forbidden();
        } else {
            access.tenantMember(request, id);
        }
    }

    /** 公告与 AI 配置只允许平台管理员或对应租户管理员写入。 */
    public Long admin(String type, long id, HttpServletRequest request) {
        validate(type, id);
        if ("platform".equals(type) || "system".equals(type) || hasPlatformSession(request)) {
            Long accountId = access.platformAdmin(request).getId();
            UnPlatMember member = platformMembers.selectOne(new LambdaQueryWrapper<UnPlatMember>()
                    .eq(UnPlatMember::getAccountId, accountId).eq(UnPlatMember::getStatus, 1));
            if (member == null) throw forbidden();
            return member.getId();
        }
        return access.tenantAdmin(request, id).getId();
    }

    /** 文件上传允许当前范围的成员，跨范围平台会话不能冒充租户上传者。 */
    public Long fileUploader(String type, long id, HttpServletRequest request) {
        validate(type, id);
        if ("platform".equals(type)) {
            Long accountId = access.platformAccount(request).getId();
            UnPlatMember member = platformMembers.selectOne(new LambdaQueryWrapper<UnPlatMember>()
                    .eq(UnPlatMember::getAccountId, accountId).eq(UnPlatMember::getStatus, 1));
            if (member == null) throw forbidden();
            return member.getId();
        }
        if ("system".equals(type)) {
            UnSysMember member = access.systemMember(request);
            if (member.getSystemId() != id) throw forbidden();
            return member.getId();
        }
        return access.tenantMember(request, id).getId();
    }

    private void validate(String type, long id) {
        if ("platform".equals(type)) {
            if (id != 0) throw bad();
        } else if ("system".equals(type)) {
            UnSysSystem system = systems.selectById(id);
            if (system == null || system.getStatus() == null || system.getStatus() != 1) throw bad();
        } else if ("tenant".equals(type)) {
            UnSysTenant tenant = tenants.selectById(id);
            if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) throw bad();
        } else {
            throw bad();
        }
    }

    private boolean hasPlatformSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("platformAccountId") instanceof Long;
    }

    private ResponseStatusException bad() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源范围无效或已停用");
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "不可访问该资源范围");
    }
}
