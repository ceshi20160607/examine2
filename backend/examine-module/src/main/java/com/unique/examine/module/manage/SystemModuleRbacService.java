package com.unique.examine.module.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.module.ModuleAuthCacheCoordinator;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.entity.po.ModuleMenu;
import com.unique.examine.module.entity.po.ModuleRole;
import com.unique.examine.module.entity.po.ModuleRoleMenuPerm;
import com.unique.examine.module.service.IModuleMemberService;
import com.unique.examine.module.service.IModuleMenuService;
import com.unique.examine.module.service.IModuleRoleMenuPermService;
import com.unique.examine.module.service.IModuleRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SystemModuleRbacService {

    public record UpsertRoleCmd(Long id, Long appId, String roleCode, String roleName, Integer status) {}
    public record UpsertMemberCmd(Long id, Long appId, Long platId, Long roleId, Integer status) {}
    public record UpsertMenuCmd(
            Long id,
            Long appId,
            Long parentId,
            String menuName,
            Long pageId,
            Integer sortNo,
            Integer visibleFlag,
            String permKey,
            String apiPattern
    ) {}
    public record UpsertRoleMenuPermCmd(Long id, Long roleId, Long menuId, Integer status) {}
    public record SetRoleMenuPermCmd(Long roleId, List<Long> menuIds, Integer permLevel) {}
    public record AssignMemberRoleCmd(Long appId, Long memberPlatId, Long roleId) {}

    @Autowired
    private IModuleRoleService moduleRoleService;
    @Autowired
    private IModuleMenuService moduleMenuService;
    @Autowired
    private IModuleRoleMenuPermService moduleRoleMenuPermService;
    @Autowired
    private IModuleMemberService moduleMemberService;
    @Autowired
    private ModuleAuthCacheCoordinator moduleAuthCacheCoordinator;
    @Autowired
    private ModuleRuntimeApiPermissionService moduleRuntimeApiPermissionService;

    public List<ModuleRole> listRoles(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return moduleRoleService.lambdaQuery()
                .eq(ModuleRole::getSystemId, systemId)
                .eq(ModuleRole::getTenantId, tenantId)
                .eq(ModuleRole::getAppId, appId)
                .orderByAsc(ModuleRole::getRoleCode)
                .list();
    }

    public List<ModuleMenu> listMenus(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return moduleMenuService.lambdaQuery()
                .eq(ModuleMenu::getSystemId, systemId)
                .eq(ModuleMenu::getTenantId, tenantId)
                .eq(ModuleMenu::getAppId, appId)
                .orderByAsc(ModuleMenu::getParentId)
                .orderByAsc(ModuleMenu::getSortNo)
                .orderByAsc(ModuleMenu::getId)
                .list();
    }

    public List<ModuleMember> listMembers(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return moduleMemberService.lambdaQuery()
                .eq(ModuleMember::getSystemId, systemId)
                .eq(ModuleMember::getTenantId, tenantId)
                .eq(ModuleMember::getAppId, appId)
                .orderByAsc(ModuleMember::getPlatId)
                .orderByAsc(ModuleMember::getId)
                .list();
    }

    public List<ModuleRoleMenuPerm> listRoleMenuPerms(Long roleId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (roleId == null) {
            throw new BusinessException(400, "roleId 不能为空");
        }
        ModuleRole role = moduleRoleService.getById(roleId);
        if (role == null) {
            throw new BusinessException(404, "role 不存在");
        }
        if (!Objects.equals(role.getSystemId(), systemId) || !Objects.equals(role.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该角色");
        }
        return moduleRoleMenuPermService.lambdaQuery()
                .eq(ModuleRoleMenuPerm::getSystemId, systemId)
                .eq(ModuleRoleMenuPerm::getTenantId, tenantId)
                .eq(ModuleRoleMenuPerm::getRoleId, roleId)
                .orderByAsc(ModuleRoleMenuPerm::getMenuId)
                .orderByAsc(ModuleRoleMenuPerm::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleRole upsertRole(Long appId, Long operatorPlatId, UpsertRoleCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.roleCode() == null || body.roleCode().isBlank()) {
            throw new BusinessException(400, "roleCode 不能为空");
        }
        if (body.roleName() == null || body.roleName().isBlank()) {
            throw new BusinessException(400, "roleName 不能为空");
        }
        int status = (body.status() == null) ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        String roleCode = body.roleCode().trim();
        String roleName = body.roleName().trim();

        ModuleRole role;
        if (body.id() != null) {
            role = moduleRoleService.getById(body.id());
            if (role == null) {
                throw new BusinessException(404, "role 不存在");
            }
            if (!Objects.equals(role.getSystemId(), systemId) || !Objects.equals(role.getTenantId(), tenantId) || !Objects.equals(role.getAppId(), appId)) {
                throw new BusinessException(403, "无权操作该角色");
            }
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setStatus(status);
            role.setUpdateUserId(operatorPlatId);
            moduleRoleService.updateById(role);
        } else {
            long existed = moduleRoleService.lambdaQuery()
                    .eq(ModuleRole::getSystemId, systemId)
                    .eq(ModuleRole::getTenantId, tenantId)
                    .eq(ModuleRole::getAppId, appId)
                    .eq(ModuleRole::getRoleCode, roleCode)
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "roleCode 已存在");
            }
            role = new ModuleRole();
            role.setSystemId(systemId);
            role.setTenantId(tenantId);
            role.setAppId(appId);
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setStatus(status);
            role.setCreateUserId(operatorPlatId);
            role.setUpdateUserId(operatorPlatId);
            moduleRoleService.save(role);
        }
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleMenu upsertMenu(Long appId, Long operatorPlatId, UpsertMenuCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.menuName() == null || body.menuName().isBlank()) {
            throw new BusinessException(400, "menuName 不能为空");
        }
        Long parentId = body.parentId() == null ? 0L : body.parentId();
        if (parentId < 0) {
            throw new BusinessException(400, "parentId 不合法");
        }
        Integer visible = body.visibleFlag() == null ? 1 : body.visibleFlag();
        if (visible != 0 && visible != 1) {
            throw new BusinessException(400, "visibleFlag 须为 0/1");
        }

        ModuleMenu menu;
        if (body.id() != null) {
            menu = moduleMenuService.getById(body.id());
            if (menu == null) {
                throw new BusinessException(404, "menu 不存在");
            }
            if (!Objects.equals(menu.getSystemId(), systemId) || !Objects.equals(menu.getTenantId(), tenantId) || !Objects.equals(menu.getAppId(), appId)) {
                throw new BusinessException(403, "无权操作该菜单");
            }
        } else {
            menu = new ModuleMenu();
            menu.setSystemId(systemId);
            menu.setTenantId(tenantId);
            menu.setAppId(appId);
            menu.setCreateUserId(operatorPlatId);
        }

        menu.setParentId(parentId);
        menu.setMenuName(body.menuName().trim());
        menu.setPageId(body.pageId());
        menu.setSortNo(body.sortNo());
        menu.setVisibleFlag(visible);
        menu.setPermKey(trimToNull(body.permKey()));
        menu.setApiPattern(trimToNull(body.apiPattern()));
        menu.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleMenuService.updateById(menu);
        } else {
            moduleMenuService.save(menu);
        }

        if (moduleRuntimeApiPermissionService != null) {
            moduleRuntimeApiPermissionService.evictAppCache(appId);
        }
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public void setRoleMenuPerms(Long operatorPlatId, SetRoleMenuPermCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null || body.roleId() == null) {
            throw new BusinessException(400, "roleId 不能为空");
        }
        Integer permLevel = body.permLevel() == null ? 1 : body.permLevel();
        if (permLevel != 0 && permLevel != 1) {
            throw new BusinessException(400, "permLevel 须为 1=允许 或 0=禁止");
        }
        ModuleRole role = moduleRoleService.getById(body.roleId());
        if (role == null) {
            throw new BusinessException(404, "role 不存在");
        }
        if (!Objects.equals(role.getSystemId(), systemId) || !Objects.equals(role.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该角色");
        }

        moduleRoleMenuPermService.lambdaUpdate()
                .eq(ModuleRoleMenuPerm::getRoleId, role.getId())
                .remove();

        List<Long> menuIds = body.menuIds() == null ? List.of() : body.menuIds();
        List<ModuleRoleMenuPerm> batch = new ArrayList<>();
        for (Long mid : menuIds) {
            if (mid == null || mid <= 0) {
                continue;
            }
            ModuleMenu m = moduleMenuService.getById(mid);
            if (m == null) {
                continue;
            }
            if (!Objects.equals(m.getSystemId(), systemId) || !Objects.equals(m.getTenantId(), tenantId) || !Objects.equals(m.getAppId(), role.getAppId())) {
                continue;
            }
            ModuleRoleMenuPerm rp = new ModuleRoleMenuPerm();
            rp.setSystemId(systemId);
            rp.setTenantId(tenantId);
            rp.setAppId(role.getAppId());
            rp.setRoleId(role.getId());
            rp.setMenuId(mid);
            rp.setPermLevel(permLevel);
            rp.setCreateUserId(operatorPlatId);
            rp.setUpdateUserId(operatorPlatId);
            batch.add(rp);
        }
        if (!batch.isEmpty()) {
            moduleRoleMenuPermService.saveBatch(batch);
        }
        if (moduleAuthCacheCoordinator != null) {
            moduleAuthCacheCoordinator.invalidateForRole(systemId, tenantId, role.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleMember assignMemberRole(Long operatorPlatId, AssignMemberRoleCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null || body.memberPlatId() == null) {
            throw new BusinessException(400, "appId/memberPlatId 不能为空");
        }
        if (body.roleId() == null) {
            throw new BusinessException(400, "roleId 不能为空");
        }
        ModuleRole role = moduleRoleService.getById(body.roleId());
        if (role == null) {
            throw new BusinessException(404, "role 不存在");
        }
        if (!Objects.equals(role.getSystemId(), systemId) || !Objects.equals(role.getTenantId(), tenantId) || !Objects.equals(role.getAppId(), body.appId())) {
            throw new BusinessException(403, "无权使用该角色");
        }
        ModuleMember member = moduleMemberService.lambdaQuery()
                .eq(ModuleMember::getSystemId, systemId)
                .eq(ModuleMember::getTenantId, tenantId)
                .eq(ModuleMember::getAppId, body.appId())
                .eq(ModuleMember::getPlatId, body.memberPlatId())
                .last("limit 1")
                .one();
        if (member == null) {
            member = new ModuleMember();
            member.setSystemId(systemId);
            member.setTenantId(tenantId);
            member.setAppId(body.appId());
            member.setPlatId(body.memberPlatId());
            member.setStatus(1);
            member.setRoleId(role.getId());
            member.setCreateUserId(operatorPlatId);
            member.setUpdateUserId(operatorPlatId);
            moduleMemberService.save(member);
        } else {
            member.setRoleId(role.getId());
            member.setStatus(1);
            member.setUpdateUserId(operatorPlatId);
            moduleMemberService.updateById(member);
        }
        if (moduleAuthCacheCoordinator != null) {
            moduleAuthCacheCoordinator.invalidateForMember(systemId, tenantId, body.memberPlatId());
        }
        return member;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void requireOperator(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private static long requireSystem() {
        long sid = AuthContextHolder.getSystemIdOrDefault();
        if (sid == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return sid;
    }
}

