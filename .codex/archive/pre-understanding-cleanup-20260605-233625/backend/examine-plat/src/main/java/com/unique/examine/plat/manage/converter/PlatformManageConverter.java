package com.unique.examine.plat.manage.converter;

import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.manage.vo.PlatformManageVO;

/**
 * 平台管理实体转换器。
 */
public final class PlatformManageConverter {

    private PlatformManageConverter() {
    }

    /**
     * 转换系统信息。
     *
     * @param entity 系统实体
     * @return 平台管理出参
     */
    public static PlatformManageVO fromSystem(com.unique.examine.plat.base.entity.System entity) {
        PlatformManageVO vo = new PlatformManageVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getSystemCode());
        vo.setName(entity.getSystemName());
        vo.setStatus(entity.getStatus());
        vo.setResourcePath(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换租户信息。
     *
     * @param entity 租户实体
     * @return 平台管理出参
     */
    public static PlatformManageVO fromTenant(Tenant entity) {
        PlatformManageVO vo = new PlatformManageVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getTenantCode());
        vo.setName(entity.getTenantName());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换账号信息。
     *
     * @param entity 账号实体
     * @return 平台管理出参
     */
    public static PlatformManageVO fromAccount(Account entity) {
        PlatformManageVO vo = new PlatformManageVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getUsername());
        vo.setName(entity.getDisplayName());
        vo.setStatus(entity.getStatus());
        vo.setMobile(entity.getMobile());
        vo.setEmail(entity.getEmail());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换角色信息。
     *
     * @param entity 角色实体
     * @return 平台管理出参
     */
    public static PlatformManageVO fromRole(Role entity) {
        PlatformManageVO vo = new PlatformManageVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setSystemId(entity.getSystemId());
        vo.setAppId(entity.getAppId());
        vo.setCode(entity.getRoleCode());
        vo.setName(entity.getRoleName());
        vo.setType(entity.getRoleType());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换权限信息。
     *
     * @param entity 权限实体
     * @return 平台管理出参
     */
    public static PlatformManageVO fromPermission(Permission entity) {
        PlatformManageVO vo = new PlatformManageVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setSystemId(entity.getSystemId());
        vo.setAppId(entity.getAppId());
        vo.setModuleId(entity.getModuleId());
        vo.setCode(entity.getPermissionCode());
        vo.setName(entity.getPermissionName());
        vo.setType(entity.getPermissionType());
        vo.setResourcePath(entity.getResourcePath());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
