package com.unique.examine.manage.service.impl;

import com.unique.examine.base.entity.BusinessSystem;
import com.unique.examine.base.entity.PlatformAccount;
import com.unique.examine.base.entity.Tenant;
import com.unique.examine.base.service.IBusinessSystemService;
import com.unique.examine.base.service.IPlatformAccountService;
import com.unique.examine.base.service.ISystemMemberService;
import com.unique.examine.base.service.ITenantService;
import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.PermissionService;
import com.unique.examine.manage.service.PlatformManageService;
import com.unique.examine.manage.vo.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformManageServiceImpl implements PlatformManageService {
    private final ITenantService tenantService;
    private final IBusinessSystemService systemService;
    private final IPlatformAccountService accountService;
    private final ISystemMemberService systemMemberService;
    private final AuthManageServiceImpl authManageService;
    private final PermissionService permissionService;
    private final EntityMapConverter converter;

    @Override
    public PageResult<SimpleVO> listTenants(long pageNo, long pageSize, String keyword) {
        permissionService.requirePlatformAction("tenant:view");
        IPage<Tenant> page = tenantService.page(Page.of(pageNo, pageSize), Wrappers.<Tenant>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Tenant::getTenantName, keyword)
                .or(keyword != null && !keyword.isBlank(), q -> q.like(Tenant::getTenantCode, keyword))
                .orderByDesc(Tenant::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO createTenant(TenantSaveBO bo) {
        permissionService.requirePlatformAction("tenant:create");
        Tenant tenant = new Tenant();
        tenant.setTenantName(bo.getTenantName());
        tenant.setTenantCode(bo.getTenantCode());
        tenant.setOwnerAccountId(bo.getOwnerAccountId());
        tenant.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        tenantService.save(tenant);
        return converter.toSimple(tenant);
    }

    @Override
    public PageResult<SimpleVO> listSystems(long pageNo, long pageSize, Long tenantId, String keyword) {
        permissionService.requirePlatformAction("system:view");
        IPage<BusinessSystem> page = systemService.page(Page.of(pageNo, pageSize), Wrappers.<BusinessSystem>lambdaQuery()
                .eq(tenantId != null, BusinessSystem::getTenantId, tenantId)
                .and(keyword != null && !keyword.isBlank(), q -> q.like(BusinessSystem::getSystemName, keyword).or().like(BusinessSystem::getSystemCode, keyword))
                .orderByDesc(BusinessSystem::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO createSystem(SystemSaveBO bo) {
        permissionService.requirePlatformAction("system:create");
        Long ownerAccountId = bo.getOwnerAccountId() == null ? SecurityContext.currentUser().getAccountId() : bo.getOwnerAccountId();
        if (accountService.getById(ownerAccountId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统拥有者账号不存在");
        }
        BusinessSystem system = new BusinessSystem();
        system.setTenantId(bo.getTenantId());
        system.setSystemName(bo.getSystemName());
        system.setSystemCode(bo.getSystemCode());
        system.setOwnerAccountId(ownerAccountId);
        system.setDescription(bo.getDescription());
        system.setStatus(bo.getStatus() == null ? StatusEnums.DRAFT : bo.getStatus());
        systemService.save(system);
        return converter.toSimple(system);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO updateSystemStatus(Long systemId, StatusUpdateBO bo) {
        permissionService.requirePlatformAction("system:status");
        BusinessSystem system = systemService.getById(systemId);
        if (system == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统不存在");
        }
        system.setStatus(bo.getStatus());
        systemService.updateById(system);
        return converter.toSimple(system);
    }

    @Override
    public AuthTokenVO enterSystem(ContextEnterBO bo) {
        BusinessSystem system = systemService.getById(bo.getSystemId());
        if (system == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统不存在");
        }
        if (!StatusEnums.ENABLED.equals(system.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "系统未启用");
        }
        if (bo.getTenantId() != null && !bo.getTenantId().equals(system.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求租户与系统归属不一致");
        }
        PlatformAccount account = accountService.getById(SecurityContext.currentUser().getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (!isAllowedEnter(system, account.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号无权进入该系统");
        }
        return authManageService.issueForSystem(account, system);
    }

    private boolean isAllowedEnter(BusinessSystem system, Long accountId) {
        if (accountId != null && accountId.equals(system.getOwnerAccountId())) {
            return true;
        }
        return systemMemberService.count(Wrappers.<com.unique.examine.base.entity.SystemMember>lambdaQuery()
                .eq(com.unique.examine.base.entity.SystemMember::getSystemId, system.getId())
                .eq(com.unique.examine.base.entity.SystemMember::getTenantId, system.getTenantId())
                .eq(com.unique.examine.base.entity.SystemMember::getAccountId, accountId)
                .eq(com.unique.examine.base.entity.SystemMember::getStatus, StatusEnums.ENABLED)) > 0;
    }
}
