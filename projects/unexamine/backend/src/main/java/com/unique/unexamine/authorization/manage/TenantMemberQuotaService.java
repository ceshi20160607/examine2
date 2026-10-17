package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.foundation.base.entity.CoreQuotaPolicy;
import com.unique.unexamine.foundation.base.service.CoreQuotaPolicyBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 同一租户的成员新增入口共用配额判断，实际成员数以业务表为准。 */
@Service
public class TenantMemberQuotaService {
    private final CoreQuotaPolicyBaseService quotaPolicyService;
    private final SystemTenantMemberBaseService tenantMemberService;

    public TenantMemberQuotaService(CoreQuotaPolicyBaseService quotaPolicyService,
                                    SystemTenantMemberBaseService tenantMemberService) {
        this.quotaPolicyService = quotaPolicyService;
        this.tenantMemberService = tenantMemberService;
    }

    /** 必须在成员写入事务内调用；锁策略行串行化同一租户的并发新增或恢复。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkBeforeActivate(Long systemId, Long tenantId) {
        CoreQuotaPolicy policy = quotaPolicyService.selectOne(Wrappers.<CoreQuotaPolicy>lambdaQuery()
                .eq(CoreQuotaPolicy::getContextType, "SYSTEM")
                .eq(CoreQuotaPolicy::getSystemId, systemId)
                .eq(CoreQuotaPolicy::getTenantId, tenantId)
                .eq(CoreQuotaPolicy::getQuotaCode, "MEMBER_COUNT")
                .eq(CoreQuotaPolicy::getPeriodType, "TOTAL")
                .eq(CoreQuotaPolicy::getStatus, "ACTIVE")
                .last("FOR UPDATE"));
        if (policy == null) return;

        // 成员入口可能在取得策略锁前做过普通读取；锁定读取才能看到前一事务刚提交的成员。
        long activeMembers = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .select(SystemTenantMember::getId)
                .eq(SystemTenantMember::getSystemId, systemId)
                .eq(SystemTenantMember::getTenantId, tenantId)
                .eq(SystemTenantMember::getStatus, "ACTIVE")
                .last("FOR UPDATE")).size();
        if (activeMembers >= policy.getHardLimit()) {
            throw new DomainException("TENANT_MEMBER_QUOTA_EXCEEDED",
                    "当前工作空间有效成员已达到上限：" + policy.getHardLimit(), HttpStatus.CONFLICT);
        }
    }
}
