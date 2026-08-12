package com.unique.examine.plat.vnext.manage.registration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.Member;
import com.unique.examine.plat.vnext.base.entity.MemberTenant;
import com.unique.examine.plat.vnext.base.entity.Tenant;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberTenantService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatSystemService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatTenantService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
final class RegistrationSystemMembershipService {
    private final IVNextPlatSystemService systemService;
    private final IVNextPlatTenantService tenantService;
    private final IVNextPlatMemberService memberService;
    private final IVNextPlatMemberTenantService memberTenantService;
    private final IdService idService;

    RegistrationSystemMembershipService(
            IVNextPlatSystemService systemService,
            IVNextPlatTenantService tenantService,
            IVNextPlatMemberService memberService,
            IVNextPlatMemberTenantService memberTenantService,
            IdService idService
    ) {
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.memberService = memberService;
        this.memberTenantService = memberTenantService;
        this.idService = idService;
    }

    com.unique.examine.plat.vnext.base.entity.System findSystem(String code) {
        return systemService.getOne(
                Wrappers.<com.unique.examine.plat.vnext.base.entity.System>lambdaQuery()
                        .eq(com.unique.examine.plat.vnext.base.entity.System::getSystemCode, code), false
        );
    }

    com.unique.examine.plat.vnext.base.entity.System createSystem(
            RegistrationCommand command, long accountId, LocalDateTime now
    ) {
        var system = new com.unique.examine.plat.vnext.base.entity.System();
        system.setId(idService.nextId());
        system.setSystemCode(command.systemCode());
        system.setName(command.systemName());
        system.setStatus("INITIALIZING");
        system.setTenantMode("SINGLE");
        system.setOwnerAccountId(accountId);
        system.setPermissionVersion(1L);
        system.setCreatedAt(now);
        system.setCreatedBy(accountId);
        system.setUpdatedAt(now);
        system.setUpdatedBy(accountId);
        system.setVersion(0L);
        require(systemService.save(system), "System persistence");
        return system;
    }

    Tenant createTenant(long systemId, long accountId, LocalDateTime now) {
        var tenant = new Tenant();
        tenant.setId(idService.nextId());
        tenant.setSystemId(systemId);
        tenant.setTenantCode("default");
        tenant.setName("默认租户");
        tenant.setIsDefault(true);
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(now);
        tenant.setCreatedBy(accountId);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(accountId);
        tenant.setVersion(0L);
        require(tenantService.save(tenant), "Tenant persistence");
        return tenant;
    }

    Member createMembership(
            RegistrationCommand command, Account account,
            com.unique.examine.plat.vnext.base.entity.System system, Tenant tenant,
            LocalDateTime now
    ) {
        var member = new Member();
        member.setId(idService.nextId());
        member.setSystemId(system.getId());
        member.setAccountId(account.getId());
        member.setMemberCode("OWNER_" + member.getId());
        member.setDisplayName(command.displayName());
        member.setDefaultTenantId(tenant.getId());
        member.setStatus("ACTIVE");
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setCreatedBy(account.getId());
        member.setUpdatedAt(now);
        member.setUpdatedBy(account.getId());
        member.setVersion(0L);
        require(memberService.save(member), "Member persistence");

        var link = new MemberTenant();
        link.setId(idService.nextId());
        link.setSystemId(system.getId());
        link.setMemberId(member.getId());
        link.setTenantId(tenant.getId());
        link.setStatus("ACTIVE");
        link.setGrantedAt(now);
        link.setGrantedBy(account.getId());
        link.setExpiresAt(null);
        link.setCreatedAt(now);
        link.setCreatedBy(account.getId());
        link.setUpdatedAt(now);
        link.setUpdatedBy(account.getId());
        link.setVersion(0L);
        require(memberTenantService.save(link), "Member tenant persistence");
        return member;
    }

    void activate(com.unique.examine.plat.vnext.base.entity.System system, long accountId, LocalDateTime now) {
        system.setStatus("ACTIVE");
        system.setInitializedAt(now);
        system.setUpdatedAt(now);
        system.setUpdatedBy(accountId);
        require(systemService.updateById(system), "System activation");
    }

    SystemGraph loadActive(RegistrationReceipt receipt) {
        var system = systemService.getById(receipt.systemId());
        var tenant = tenantService.getById(receipt.tenantId());
        var member = memberService.getById(receipt.memberId());
        var link = memberTenantService.getOne(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, receipt.systemId())
                .eq(MemberTenant::getMemberId, receipt.memberId())
                .eq(MemberTenant::getTenantId, receipt.tenantId()), false);
        var valid = system != null && tenant != null && member != null && link != null
                && "ACTIVE".equals(system.getStatus())
                && "SINGLE".equals(system.getTenantMode())
                && Objects.equals(system.getOwnerAccountId(), receipt.accountId())
                && Objects.equals(system.getPermissionVersion(), 1L)
                && Objects.equals(tenant.getSystemId(), receipt.systemId())
                && Boolean.TRUE.equals(tenant.getIsDefault())
                && "default".equals(tenant.getTenantCode())
                && "ACTIVE".equals(tenant.getStatus())
                && Objects.equals(member.getSystemId(), receipt.systemId())
                && Objects.equals(member.getAccountId(), receipt.accountId())
                && Objects.equals(member.getDefaultTenantId(), receipt.tenantId())
                && "ACTIVE".equals(member.getStatus())
                && "ACTIVE".equals(link.getStatus());
        if (!valid) {
            throw RegistrationErrors.replayMismatch();
        }
        return new SystemGraph(system, tenant, member);
    }

    private static void require(boolean result, String operation) {
        if (!result) {
            throw new IllegalStateException(operation + " was rejected");
        }
    }

    record SystemGraph(
            com.unique.examine.plat.vnext.base.entity.System system,
            Tenant tenant,
            Member member
    ) {
    }
}
