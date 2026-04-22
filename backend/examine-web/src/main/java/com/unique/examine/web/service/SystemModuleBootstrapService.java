package com.unique.examine.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.module.ModuleAuthCacheCoordinator;
import com.unique.examine.module.entity.po.ModuleApp;
import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.entity.po.ModuleMenu;
import com.unique.examine.module.entity.po.ModuleRole;
import com.unique.examine.module.entity.po.ModuleRoleMenuPerm;
import com.unique.examine.module.mapper.ModuleRoleMenuPermMapper;
import com.unique.examine.module.service.IModuleAppService;
import com.unique.examine.module.service.IModuleMemberService;
import com.unique.examine.module.service.IModuleMenuService;
import com.unique.examine.module.service.IModuleRoleService;
import com.unique.examine.plat.entity.po.PlatSystem;
import com.unique.examine.web.module.ModuleMenuApiPermissionCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自建系统 module 域种子：默认应用、菜单（perm_key/api_pattern）、管理员角色与菜单权限、所有者成员。
 */
@Service
public class SystemModuleBootstrapService {

    private static final String DEFAULT_APP_CODE = "default";
    private static final String ADMIN_ROLE_CODE = "admin";

    @Autowired
    private IModuleAppService moduleAppService;
    @Autowired
    private IModuleMemberService moduleMemberService;
    @Autowired
    private IModuleMenuService moduleMenuService;
    @Autowired
    private IModuleRoleService moduleRoleService;
    @Autowired
    private ModuleRoleMenuPermMapper moduleRoleMenuPermMapper;
    @Autowired
    private ModuleRuntimeApiPermissionService moduleRuntimeApiPermissionService;
    @Autowired(required = false)
    private ModuleAuthCacheCoordinator moduleAuthCacheCoordinator;

    @Transactional(rollbackFor = Exception.class)
    public void afterSystemCreated(PlatSystem system, Long ownerPlatId) {
        if (system == null || system.getId() == null || ownerPlatId == null) {
            return;
        }
        if (system.getMultiTenantEnabled() != null && system.getMultiTenantEnabled() == 1) {
            return;
        }
        ensureTenantModuleSeed(system, 0L, ownerPlatId);
    }

    /**
     * 保证 (systemId, tenantId) 下存在 default 应用；写入默认「接口门」菜单、admin 角色及菜单权限；
     * 若当前用户为系统所有者则保证其在默认应用下的成员行并绑定 admin 角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureTenantModuleSeed(PlatSystem system, Long tenantId, Long currentPlatId) {
        if (system == null || system.getId() == null || tenantId == null || currentPlatId == null) {
            return;
        }
        ModuleApp app = findOrCreateDefaultApp(system, tenantId, currentPlatId);
        boolean menuInserted = seedMenuApiGatesIfMissing(app, currentPlatId);
        if (menuInserted) {
            moduleRuntimeApiPermissionService.evictAppCache(app.getId());
        }
        Long adminRoleId = ensureAdminRole(app, currentPlatId);
        boolean rolePermAdded = syncAdminRoleMenuPermsIfMissing(adminRoleId, app, currentPlatId);
        if (rolePermAdded && moduleAuthCacheCoordinator != null) {
            moduleAuthCacheCoordinator.invalidateForRole(system.getId(), tenantId, adminRoleId);
        }
        if (currentPlatId.equals(system.getOwnerPlatAccountId())) {
            ensureMemberIfAbsent(app.getId(), system.getId(), tenantId, currentPlatId, adminRoleId);
            moduleMemberService.lambdaUpdate()
                    .eq(ModuleMember::getAppId, app.getId())
                    .eq(ModuleMember::getPlatId, currentPlatId)
                    .eq(ModuleMember::getStatus, 1)
                    .and(w -> w.isNull(ModuleMember::getRoleId))
                    .set(ModuleMember::getRoleId, adminRoleId)
                    .update();
            if (moduleAuthCacheCoordinator != null) {
                moduleAuthCacheCoordinator.invalidateForMember(system.getId(), tenantId, currentPlatId);
            }
        }
    }

    private ModuleApp findOrCreateDefaultApp(PlatSystem system, Long tenantId, Long operatorPlatId) {
        ModuleApp existing = moduleAppService.getOne(new LambdaQueryWrapper<ModuleApp>()
                .eq(ModuleApp::getSystemId, system.getId())
                .eq(ModuleApp::getTenantId, tenantId)
                .eq(ModuleApp::getAppCode, DEFAULT_APP_CODE)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        ModuleApp app = new ModuleApp();
        app.setSystemId(system.getId());
        app.setTenantId(tenantId);
        app.setAppCode(DEFAULT_APP_CODE);
        app.setAppName(StringUtils.hasText(system.getName()) ? system.getName().trim() : "默认应用");
        app.setStatus(1);
        app.setPublishedFlag(0);
        app.setCreateUserId(operatorPlatId);
        app.setUpdateUserId(operatorPlatId);
        moduleAppService.save(app);
        return app;
    }

    /** @return 是否新插入了菜单行 */
    private boolean seedMenuApiGatesIfMissing(ModuleApp app, Long operatorPlatId) {
        boolean anyInserted = false;
        int sortBase = 9000;
        int i = 0;
        for (ModuleMenuApiPermissionCatalog.Entry entry : ModuleMenuApiPermissionCatalog.ENTRIES) {
            long cnt = moduleMenuService.count(new LambdaQueryWrapper<ModuleMenu>()
                    .eq(ModuleMenu::getAppId, app.getId())
                    .eq(ModuleMenu::getApiPattern, entry.apiPattern()));
            if (cnt > 0) {
                continue;
            }
            ModuleMenu menu = new ModuleMenu();
            menu.setSystemId(app.getSystemId());
            menu.setTenantId(app.getTenantId());
            menu.setAppId(app.getId());
            menu.setParentId(0L);
            menu.setMenuName("权限·" + entry.menuTitle());
            menu.setPageId(null);
            menu.setSortNo(sortBase + (i++));
            menu.setVisibleFlag(0);
            menu.setPermKey(entry.permKey());
            menu.setApiPattern(entry.apiPattern());
            menu.setModuleFieldsJson(null);
            menu.setCreateUserId(operatorPlatId);
            menu.setUpdateUserId(operatorPlatId);
            moduleMenuService.save(menu);
            anyInserted = true;
        }
        return anyInserted;
    }

    private Long ensureAdminRole(ModuleApp app, Long operatorPlatId) {
        ModuleRole existing = moduleRoleService.getOne(new LambdaQueryWrapper<ModuleRole>()
                .eq(ModuleRole::getAppId, app.getId())
                .eq(ModuleRole::getRoleCode, ADMIN_ROLE_CODE)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        ModuleRole r = new ModuleRole();
        r.setSystemId(app.getSystemId());
        r.setTenantId(app.getTenantId());
        r.setAppId(app.getId());
        r.setRoleCode(ADMIN_ROLE_CODE);
        r.setRoleName("系统管理员");
        r.setStatus(1);
        r.setCreateUserId(operatorPlatId);
        r.setUpdateUserId(operatorPlatId);
        moduleRoleService.save(r);
        return r.getId();
    }

    private boolean syncAdminRoleMenuPermsIfMissing(Long adminRoleId, ModuleApp app, Long operatorPlatId) {
        List<ModuleMenu> menus = moduleMenuService.list(new LambdaQueryWrapper<ModuleMenu>()
                .eq(ModuleMenu::getAppId, app.getId())
                .isNotNull(ModuleMenu::getPermKey));
        Set<Long> menuIds = new HashSet<>();
        for (ModuleMenu m : menus) {
            if (m.getId() != null && m.getPermKey() != null && !m.getPermKey().isBlank()) {
                menuIds.add(m.getId());
            }
        }
        boolean added = false;
        for (Long menuId : menuIds) {
            long cnt = moduleRoleMenuPermMapper.selectCount(new LambdaQueryWrapper<ModuleRoleMenuPerm>()
                    .eq(ModuleRoleMenuPerm::getRoleId, adminRoleId)
                    .eq(ModuleRoleMenuPerm::getMenuId, menuId)
                    .eq(ModuleRoleMenuPerm::getPermLevel, 1));
            if (cnt > 0) {
                continue;
            }
            ModuleRoleMenuPerm rp = new ModuleRoleMenuPerm();
            rp.setSystemId(app.getSystemId());
            rp.setTenantId(app.getTenantId());
            rp.setAppId(app.getId());
            rp.setRoleId(adminRoleId);
            rp.setMenuId(menuId);
            rp.setPermLevel(1);
            rp.setCreateUserId(operatorPlatId);
            rp.setUpdateUserId(operatorPlatId);
            moduleRoleMenuPermMapper.insert(rp);
            added = true;
        }
        return added;
    }

    private void ensureMemberIfAbsent(Long appId, Long systemId, Long tenantId, Long platId, Long adminRoleId) {
        long cnt = moduleMemberService.count(new LambdaQueryWrapper<ModuleMember>()
                .eq(ModuleMember::getAppId, appId)
                .eq(ModuleMember::getPlatId, platId)
                .eq(ModuleMember::getStatus, 1));
        if (cnt > 0) {
            return;
        }
        ModuleMember m = new ModuleMember();
        m.setSystemId(systemId);
        m.setTenantId(tenantId);
        m.setAppId(appId);
        m.setPlatId(platId);
        m.setRoleId(adminRoleId);
        m.setStatus(1);
        m.setCreateUserId(platId);
        m.setUpdateUserId(platId);
        moduleMemberService.save(m);
    }
}
