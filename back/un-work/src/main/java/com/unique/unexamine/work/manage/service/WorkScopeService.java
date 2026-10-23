package com.unique.unexamine.work.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 项目与任务复用的平台、系统租户作用域及目标成员校验。 */
@Service
public class WorkScopeService {
    private final AuthManageService auth;
    private final SystemManageService systems;
    private final IUnPlatAccountService accounts;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;

    /** 当前操作者及其明确选择的工作作用域。 */
    public record Scope(Long actorId,String type,Long systemId,Long tenantId) {}
    /** 目标账号及其在当前系统中展示的名称。 */
    public record Assignee(UnPlatAccount account,String displayName) {}

    /** 注入统一身份和系统租户的已生成基础服务。 */
    public WorkScopeService(AuthManageService auth,SystemManageService systems,
            IUnPlatAccountService accounts,IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers) {
        this.auth=auth;this.systems=systems;this.accounts=accounts;
        this.members=members;this.tenantMembers=tenantMembers;
    }

    /** 工作项目和任务始终显式区分平台与当前系统租户。 */
    public Scope scope(String token,Long systemId,Long tenantId) {
        if (systemId==null) {
            if (tenantId!=null) throw new IllegalArgumentException("平台工作不能指定租户");
            return new Scope(auth.requireAccount(token).getId(),"PLATFORM",null,null);
        }
        var context=systems.context(token,systemId,tenantId);
        return new Scope(context.accountId(),"SYSTEM",context.systemId(),context.tenantId());
    }

    /** 指派目标必须是有效账号，系统工作还要求有效系统及租户成员关系。 */
    public Assignee assignee(Scope scope,Long accountId) {
        if (accountId==null || accountId<1) throw new IllegalArgumentException("目标账号无效");
        UnPlatAccount account=accounts.getById(accountId);
        if (account==null || !"ACTIVE".equals(account.getStatus()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"目标账号不存在");
        if (scope.systemId()==null) return new Assignee(account,account.getDisplayName());
        UnSysMember member=members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId,scope.systemId())
                .eq(UnSysMember::getAccountId,accountId)
                .eq(UnSysMember::getStatus,"ACTIVE"),false);
        if (member==null || !tenantMembers.exists(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId,scope.systemId())
                .eq(UnSysTenantMember::getTenantId,scope.tenantId())
                .eq(UnSysTenantMember::getMemberId,member.getId())
                .eq(UnSysTenantMember::getStatus,"ACTIVE")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"目标账号不属于当前租户");
        String name=member.getDisplayName();
        return new Assignee(account,name==null || name.isBlank()?account.getDisplayName():name);
    }

    /** 一次查询平台名称，再按系统成员展示名覆盖，供任务和项目成员列表使用。 */
    public Map<Long,String> displayNames(Scope scope,List<Long> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        Map<Long,String> names=new HashMap<>();
        accounts.listByIds(accountIds).forEach(account->names.put(account.getId(),account.getDisplayName()));
        if (scope.systemId()!=null) members.list(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId,scope.systemId())
                .in(UnSysMember::getAccountId,accountIds))
                .forEach(member->{if (member.getDisplayName()!=null && !member.getDisplayName().isBlank())
                    names.put(member.getAccountId(),member.getDisplayName());});
        return names;
    }
}
