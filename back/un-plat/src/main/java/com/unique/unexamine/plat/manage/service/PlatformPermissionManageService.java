package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatMenu;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.entity.UnPlatRoleData;
import com.unique.unexamine.plat.base.entity.UnPlatRoleMenu;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatMenuService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleMenuService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleDataService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.DataGrant;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MenuGrant;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MenuRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MenuView;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberRolesRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleMenusRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleDataRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleView;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台菜单、角色和角色关系配置；修改保存后即时影响后续平台管理请求。 */
@Service
public class PlatformPermissionManageService {
    private final AuthManageService auth;
    private final IUnPlatMenuService menus;
    private final IUnPlatRoleService roles;
    private final IUnPlatMemberService members;
    private final IUnPlatMemberRoleService memberRoles;
    private final IUnPlatRoleMenuService roleMenus;
    private final IUnPlatRoleDataService roleData;
    private final IUnPlatLogOptService logs;
    private final ObjectMapper json;

    /** 注入平台权限基础服务和操作审计。 */
    public PlatformPermissionManageService(AuthManageService auth, IUnPlatMenuService menus,
            IUnPlatRoleService roles, IUnPlatMemberService members,
            IUnPlatMemberRoleService memberRoles, IUnPlatRoleMenuService roleMenus,
            IUnPlatRoleDataService roleData, IUnPlatLogOptService logs, ObjectMapper json) {
        this.auth = auth;
        this.menus = menus;
        this.roles = roles;
        this.members = members;
        this.memberRoles = memberRoles;
        this.roleMenus = roleMenus;
        this.roleData = roleData;
        this.logs = logs;
        this.json = json;
    }

    /** 查询平台有效菜单，菜单是平台后台的统一权限资源。 */
    public List<MenuView> menus(String token) {
        auth.requirePlatformAdmin(token);
        return menus.list(new LambdaQueryWrapper<UnPlatMenu>().eq(UnPlatMenu::getStatus, "ACTIVE")
                        .orderByAsc(UnPlatMenu::getSortNo).orderByAsc(UnPlatMenu::getId))
                .stream().map(this::menuView).toList();
    }

    /** 保存平台菜单；编码创建后保持稳定，父级只能引用有效菜单。 */
    @Transactional(rollbackFor = Exception.class)
    public MenuView saveMenu(String token, MenuRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        if (request == null || request.code() == null || request.name() == null
                || request.name().isBlank() || request.name().length() > 100
                || request.sortNo() != null && request.sortNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "菜单内容不能为空");
        String code = normalizeCode(request.code(), "菜单编码");
        if (request.id() != null && request.id().equals(request.parentId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "菜单不能以自身作为上级");
        if (request.parentId() != null) activeMenu(request.parentId());
        ensureNoCycle(request.id(), request.parentId());
        long duplicate = menus.count(new LambdaQueryWrapper<UnPlatMenu>().eq(UnPlatMenu::getCode, code)
                .ne(request.id() != null, UnPlatMenu::getId, request.id()));
        if (duplicate > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "菜单编码已存在");
        UnPlatMenu row = request.id() == null ? new UnPlatMenu() : menus.queryById(request.id());
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "菜单不存在");
        if (request.id() != null && !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "菜单已停用");
        String status = normalizeStatus(request.status());
        if ("DISABLED".equals(status) && roleMenus.count(new LambdaQueryWrapper<UnPlatRoleMenu>()
                .eq(UnPlatRoleMenu::getMenuId, row.getId())) > 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "请先解除菜单的角色授权");
        if (request.id() == null) row.setCreateTime(LocalDateTime.now());
        row.setParentId(request.parentId());
        row.setCode(code);
        row.setName(request.name().trim());
        row.setRoute(request.route() == null ? null : request.route().trim());
        row.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        row.setStatus(status);
        row.setUpdateTime(LocalDateTime.now());
        try {
            menus.addOrUpdate(row);
        } catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "菜单编码已存在");
        }
        audit(operator.getId(), request.id() == null ? "PLATFORM_MENU_CREATE" : "PLATFORM_MENU_UPDATE",
                row.getId(), Map.of("code", row.getCode(), "status", row.getStatus()));
        return menuView(row);
    }

    /** 停用菜单前保留授权数据但要求先解除有效角色授权。 */
    @Transactional(rollbackFor = Exception.class)
    public MenuView disableMenu(String token, Long menuId) {
        var operator = auth.requirePlatformAdmin(token);
        UnPlatMenu row = activeMenu(menuId);
        if (roleMenus.count(new LambdaQueryWrapper<UnPlatRoleMenu>()
                .eq(UnPlatRoleMenu::getMenuId, menuId)) > 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "请先解除菜单的角色授权");
        row.setStatus("DISABLED");
        row.setUpdateTime(LocalDateTime.now());
        menus.addOrUpdate(row);
        audit(operator.getId(), "PLATFORM_MENU_DISABLE", row.getId(), Map.of());
        return menuView(row);
    }

    /** 保存平台角色；平台超级管理员编码不能被改作其他含义。 */
    @Transactional(rollbackFor = Exception.class)
    public RoleView saveRole(String token, RoleRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        if (request == null || request.code() == null || request.name() == null
                || request.name().isBlank() || request.name().length() > 100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "角色内容不能为空");
        String code = normalizeCode(request.code(), "角色编码");
        long duplicate = roles.count(new LambdaQueryWrapper<UnPlatRole>().eq(UnPlatRole::getCode, code)
                .ne(request.id() != null, UnPlatRole::getId, request.id()));
        if (duplicate > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "角色编码已存在");
        UnPlatRole row = request.id() == null ? new UnPlatRole() : roles.queryById(request.id());
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "角色不存在");
        if (request.id() != null && !code.equals(row.getCode()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "角色编码创建后不能修改");
        String status = normalizeStatus(request.status());
        if ("PLATFORM_SUPER_ADMIN".equals(row.getCode()) && "DISABLED".equals(status))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台超级管理员角色不能停用");
        if (request.id() == null) row.setCreateTime(LocalDateTime.now());
        row.setCode(code);
        row.setName(request.name().trim());
        row.setDescription(request.description());
        row.setStatus(status);
        row.setUpdateTime(LocalDateTime.now());
        try {
            roles.addOrUpdate(row);
        } catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "角色编码已存在");
        }
        audit(operator.getId(), request.id() == null ? "PLATFORM_ROLE_CREATE" : "PLATFORM_ROLE_UPDATE",
                row.getId(), Map.of("code", row.getCode(), "status", row.getStatus()));
        return roleView(row);
    }

    /** 查询成员当前有效角色主键。 */
    public List<Long> memberRoles(String token, Long memberId) {
        auth.requirePlatformAdmin(token);
        activeMember(memberId);
        return memberRoles.list(new LambdaQueryWrapper<UnPlatMemberRole>()
                        .eq(UnPlatMemberRole::getMemberId, memberId))
                .stream().map(UnPlatMemberRole::getRoleId).toList();
    }

    /** 按角色列表替换成员授权，撤销后再次授权会恢复原逻辑记录。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> saveMemberRoles(String token, Long memberId, MemberRolesRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        activeMember(memberId);
        if (request == null || request.roleIds() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "角色列表不能为空");
        Set<Long> desired = positiveDistinct(request.roleIds(), "角色主键");
        for (Long roleId : desired) activeRole(roleId);
        var current = memberRoles.list(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getMemberId, memberId));
        for (UnPlatMemberRole link : current) {
            if (!desired.contains(link.getRoleId())) memberRoles.removeByIds(List.of(link.getId()));
        }
        for (Long roleId : desired) {
            if (memberRoles.count(new LambdaQueryWrapper<UnPlatMemberRole>()
                    .eq(UnPlatMemberRole::getMemberId, memberId)
                    .eq(UnPlatMemberRole::getRoleId, roleId)) > 0) continue;
            if (!memberRoles.restoreByPair(memberId, roleId)) {
                UnPlatMemberRole link = new UnPlatMemberRole();
                link.setMemberId(memberId);
                link.setRoleId(roleId);
                link.setCreateTime(LocalDateTime.now());
                memberRoles.addOrUpdate(link);
            }
        }
        audit(operator.getId(), "PLATFORM_MEMBER_ROLE_SAVE", memberId, Map.of("roleIds", desired));
        return desired.stream().sorted().toList();
    }

    /** 查询角色当前有效的菜单动作授权。 */
    public List<MenuGrant> roleMenus(String token, Long roleId) {
        auth.requirePlatformAdmin(token);
        activeRole(roleId);
        return roleMenus.list(new LambdaQueryWrapper<UnPlatRoleMenu>()
                        .eq(UnPlatRoleMenu::getRoleId, roleId)
                        .orderByAsc(UnPlatRoleMenu::getMenuId)
                        .orderByAsc(UnPlatRoleMenu::getActionCode))
                .stream().map(row -> new MenuGrant(row.getMenuId(), row.getActionCode())).toList();
    }

    /** 按菜单动作列表替换角色授权，角色与菜单必须都处于有效状态。 */
    @Transactional(rollbackFor = Exception.class)
    public List<MenuGrant> saveRoleMenus(String token, Long roleId, RoleMenusRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        activeRole(roleId);
        if (request == null || request.grants() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "菜单授权列表不能为空");
        Set<String> seen = new HashSet<>();
        for (MenuGrant grant : request.grants()) {
            if (grant == null || grant.menuId() == null || grant.menuId() < 1
                    || grant.actionCode() == null || !grant.actionCode().trim().matches("[A-Za-z0-9._:-]{1,80}"))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "菜单动作授权无效");
            activeMenu(grant.menuId());
            if (!seen.add(grant.menuId() + ":" + grant.actionCode().trim().toUpperCase(Locale.ROOT)))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "菜单动作授权重复");
        }
        var current = roleMenus.list(new LambdaQueryWrapper<UnPlatRoleMenu>()
                .eq(UnPlatRoleMenu::getRoleId, roleId));
        Set<String> desired = request.grants().stream()
                .map(g -> g.menuId() + ":" + g.actionCode().trim().toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        for (UnPlatRoleMenu link : current) {
            if (!desired.contains(link.getMenuId() + ":" + link.getActionCode().toUpperCase(Locale.ROOT)))
                roleMenus.removeByIds(List.of(link.getId()));
        }
        for (MenuGrant grant : request.grants()) {
            String action = grant.actionCode().trim().toUpperCase(Locale.ROOT);
            if (roleMenus.count(new LambdaQueryWrapper<UnPlatRoleMenu>()
                    .eq(UnPlatRoleMenu::getRoleId, roleId).eq(UnPlatRoleMenu::getMenuId, grant.menuId())
                    .eq(UnPlatRoleMenu::getActionCode, action)) > 0) continue;
            if (!roleMenus.restoreByGrant(roleId, grant.menuId(), action)) {
                UnPlatRoleMenu link = new UnPlatRoleMenu();
                link.setRoleId(roleId);
                link.setMenuId(grant.menuId());
                link.setActionCode(action);
                link.setCreateTime(LocalDateTime.now());
                roleMenus.addOrUpdate(link);
            }
        }
        audit(operator.getId(), "PLATFORM_ROLE_MENU_SAVE", roleId, Map.of("grants", request.grants()));
        return request.grants().stream().map(g -> new MenuGrant(g.menuId(), g.actionCode().trim().toUpperCase(Locale.ROOT))).toList();
    }

    /** 查询角色当前有效的数据范围配置。 */
    public List<DataGrant> roleData(String token, Long roleId) {
        auth.requirePlatformAdmin(token);
        activeRole(roleId);
        return roleData.list(new LambdaQueryWrapper<UnPlatRoleData>()
                        .eq(UnPlatRoleData::getRoleId, roleId)
                        .orderByAsc(UnPlatRoleData::getResourceCode))
                .stream().map(row -> new DataGrant(row.getResourceCode(), row.getScopeCode(), readJson(row.getScopeConfig())))
                .toList();
    }

    /** 替换角色数据范围；同一资源只保留一个当前有效范围。 */
    @Transactional(rollbackFor = Exception.class)
    public List<DataGrant> saveRoleData(String token, Long roleId, RoleDataRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        activeRole(roleId);
        if (request == null || request.grants() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据范围列表不能为空");
        Set<String> seen = new HashSet<>();
        for (DataGrant grant : request.grants()) {
            if (grant == null || grant.resourceCode() == null
                    || !grant.resourceCode().trim().matches("[A-Za-z0-9._:-]{1,100}"))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "资源编码无效");
            String scope = grant.scopeCode() == null ? "" : grant.scopeCode().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ALL", "SELF", "DEPARTMENT", "DEPARTMENT_SUBTREE",
                    "SELECTED_DEPARTMENTS", "SELECTED_MEMBERS").contains(scope))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据范围编码无效");
            if (!seen.add(grant.resourceCode().trim()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "同一资源的数据范围配置重复");
            if (grant.scopeConfig() != null && !(grant.scopeConfig() instanceof Map<?, ?>))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "范围补充配置必须是 JSON 对象");
        }
        var current = roleData.list(new LambdaQueryWrapper<UnPlatRoleData>()
                .eq(UnPlatRoleData::getRoleId, roleId));
        Set<String> desired = request.grants().stream()
                .map(g -> g.resourceCode().trim()).collect(java.util.stream.Collectors.toSet());
        for (UnPlatRoleData row : current) {
            if (!desired.contains(row.getResourceCode())) roleData.removeByIds(List.of(row.getId()));
        }
        for (DataGrant grant : request.grants()) {
            String resource = grant.resourceCode().trim();
            String scope = grant.scopeCode().trim().toUpperCase(Locale.ROOT);
            UnPlatRoleData row = roleData.getOne(new LambdaQueryWrapper<UnPlatRoleData>()
                    .eq(UnPlatRoleData::getRoleId, roleId).eq(UnPlatRoleData::getResourceCode, resource), false);
            if (row == null) {
                row = new UnPlatRoleData();
                row.setRoleId(roleId);
                row.setResourceCode(resource);
                row.setCreateTime(LocalDateTime.now());
            }
            row.setScopeCode(scope);
            row.setScopeConfig(writeJson(grant.scopeConfig()));
            row.setUpdateTime(LocalDateTime.now());
            roleData.addOrUpdate(row);
        }
        audit(operator.getId(), "PLATFORM_ROLE_DATA_SAVE", roleId, Map.of("grants", request.grants()));
        return roleData(token, roleId);
    }

    private UnPlatMenu activeMenu(Long id) {
        UnPlatMenu row = menus.queryById(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "菜单不存在或已停用");
        return row;
    }

    private UnPlatRole activeRole(Long id) {
        UnPlatRole row = roles.queryById(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "角色不存在或已停用");
        return row;
    }

    private void activeMember(Long id) {
        var row = members.queryById(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台成员不存在或已停用");
    }

    private void ensureNoCycle(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (id != null && id.equals(cursor))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "菜单上级不能是自身或下级");
            if (!visited.add(cursor) || visited.size() > 1000)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "菜单层级存在循环");
            UnPlatMenu parent = menus.queryById(cursor);
            if (parent == null) break;
            cursor = parent.getParentId();
        }
    }

    private Set<Long> positiveDistinct(List<Long> values, String label) {
        Set<Long> result = new HashSet<>();
        for (Long value : values) {
            if (value == null || value < 1 || !result.add(value))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "无效或重复");
        }
        return result;
    }

    private String normalizeCode(String value, String label) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_.:-]{1,99}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "格式无效");
        return code;
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "DISABLED").contains(status))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "状态无效");
        return status;
    }

    private MenuView menuView(UnPlatMenu row) {
        return new MenuView(row.getId(), row.getParentId(), row.getCode(), row.getName(), row.getRoute(),
                row.getSortNo(), row.getStatus(), row.getUpdateTime());
    }

    private RoleView roleView(UnPlatRole row) {
        return new RoleView(row.getId(), row.getCode(), row.getName(), row.getStatus(), row.getDescription(),
                row.getUpdateTime());
    }

    private Object readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return json.readValue(value, Object.class);
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台数据范围配置已损坏");
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台数据范围配置无法保存");
        }
    }

    private void audit(Long accountId, String action, Long objectId, Object detail) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId);
        log.setActionCode(action);
        log.setObjectType("PLATFORM_PERMISSION");
        log.setObjectId(objectId);
        log.setCreateTime(LocalDateTime.now());
        try {
            log.setDetailJson(json.writeValueAsString(detail == null ? Map.of() : detail));
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "平台权限审计内容无法保存", ex);
        }
        logs.addOrUpdate(log);
    }
}
