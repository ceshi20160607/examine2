package com.unique.unexamine.plat.manage;

import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatMenu;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.entity.UnPlatRoleData;
import com.unique.unexamine.plat.base.entity.UnPlatRoleMenu;
import com.unique.unexamine.plat.base.mapper.UnPlatDepartmentMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberRoleMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMenuMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleDataMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleMenuMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.plat.manage.PlatformSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;

/** 平台角色、菜单和数据范围授权。 */
@RestController
@RequestMapping("/api/platform")
public class PlatformAuthorizationController {
    private final UnPlatRoleMapper roles;
    private final UnPlatMenuMapper menus;
    private final UnPlatMemberMapper members;
    private final UnPlatDepartmentMapper departments;
    private final UnPlatMemberRoleMapper memberRoles;
    private final UnPlatRoleMenuMapper roleMenus;
    private final UnPlatRoleDataMapper roleData;
    private final PlatformSessionAccess access;

    public PlatformAuthorizationController(UnPlatRoleMapper roles, UnPlatMenuMapper menus,
                                           UnPlatMemberMapper members, UnPlatDepartmentMapper departments,
                                           UnPlatMemberRoleMapper memberRoles, UnPlatRoleMenuMapper roleMenus,
                                           UnPlatRoleDataMapper roleData, PlatformSessionAccess access) {
        this.roles = roles;
        this.menus = menus;
        this.members = members;
        this.departments = departments;
        this.memberRoles = memberRoles;
        this.roleMenus = roleMenus;
        this.roleData = roleData;
        this.access = access;
    }

    /** 角色分页只接受固定业务排序字段。 */
    @GetMapping("/roles")
    public IPage<UnPlatRole> roles(@RequestParam(defaultValue = "1") long page,
                                  @RequestParam(defaultValue = "20") long size,
                                  @RequestParam(defaultValue = "createTime") String sort,
                                  HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnPlatRole> query = new LambdaQueryWrapper<>();
        switch (sort) {
            case "roleCode" -> query.orderByAsc(UnPlatRole::getRoleCode);
            case "roleName" -> query.orderByAsc(UnPlatRole::getRoleName);
            case "createTime" -> query.orderByDesc(UnPlatRole::getCreateTime);
            default -> throw bad("不支持的角色排序字段");
        }
        return roles.selectPage(paging(page, size), query);
    }

    /** 新增自定义角色，内置管理员角色只由首次初始化创建。 */
    @PostMapping("/roles")
    public UnPlatRole createRole(@Valid @RequestBody RoleInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        checkRoleCode(input.roleCode(), null);
        UnPlatRole role = new UnPlatRole();
        fillRole(role, input);
        role.setRoleType((byte) 1);
        role.setStatus((byte) 1);
        roles.insert(role);
        return role;
    }

    /** 修改角色名称和备注，不允许改内置角色编码和类型。 */
    @PutMapping("/roles/{id}")
    public UnPlatRole updateRole(@PathVariable long id, @Valid @RequestBody RoleInput input,
                                 HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatRole role = role(id);
        if (role.getRoleType() != null && role.getRoleType() == 0 && !role.getRoleCode().equals(input.roleCode()))
            throw bad("不能修改内置角色编码");
        checkRoleCode(input.roleCode(), id);
        fillRole(role, input);
        roles.updateById(role);
        return role;
    }

    /** 内置管理员角色不可停用，以保留平台管理入口。 */
    @PutMapping("/roles/{id}/status")
    public UnPlatRole roleStatus(@PathVariable long id, @RequestBody StatusInput input,
                                 HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatRole role = role(id);
        if (role.getRoleType() != null && role.getRoleType() == 0 && input.status() == 0)
            throw bad("不能停用内置管理员角色");
        role.setStatus(status(input.status()));
        roles.updateById(role);
        return role;
    }

    /** 菜单列表按树形父级、排序号排列。 */
    @GetMapping("/menus")
    public List<UnPlatMenu> menus(HttpServletRequest request) {
        access.platformAccount(request);
        return menus.selectList(new LambdaQueryWrapper<UnPlatMenu>()
                .orderByAsc(UnPlatMenu::getParentId, UnPlatMenu::getSortNo, UnPlatMenu::getId));
    }

    /** 创建菜单前验证父级存在。 */
    @PostMapping("/menus")
    public UnPlatMenu createMenu(@Valid @RequestBody MenuInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        checkMenuCode(input.menuCode(), null);
        checkMenuParent(input.parentId(), null);
        UnPlatMenu menu = new UnPlatMenu();
        fillMenu(menu, input);
        menu.setStatus((byte) 1);
        menus.insert(menu);
        return menu;
    }

    /** 菜单调整父级时防止循环引用。 */
    @PutMapping("/menus/{id}")
    public UnPlatMenu updateMenu(@PathVariable long id, @Valid @RequestBody MenuInput input,
                                 HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatMenu menu = menu(id);
        checkMenuCode(input.menuCode(), id);
        checkMenuParent(input.parentId(), id);
        fillMenu(menu, input);
        menus.updateById(menu);
        return menu;
    }

    /** 菜单停用不删除已经保存的授权。 */
    @PutMapping("/menus/{id}/status")
    public UnPlatMenu menuStatus(@PathVariable long id, @RequestBody StatusInput input,
                                 HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatMenu menu = menu(id);
        menu.setStatus(status(input.status()));
        menus.updateById(menu);
        return menu;
    }

    /** 查询某平台成员的角色主键。 */
    @GetMapping("/members/{memberId}/roles")
    public List<Long> memberRoles(@PathVariable long memberId, HttpServletRequest request) {
        access.platformAdmin(request);
        member(memberId);
        return memberRoles.selectList(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getMemberId, memberId)).stream().map(UnPlatMemberRole::getRoleId).toList();
    }

    /** 全量替换成员角色时先验证角色，再在同一事务中更新关系。 */
    @PutMapping("/members/{memberId}/roles")
    @Transactional
    public void replaceMemberRoles(@PathVariable long memberId, @RequestBody IdsInput input,
                                   HttpServletRequest request) {
        Long actorAccountId = access.platformAdmin(request).getId();
        member(memberId);
        List<Long> ids = uniqueIds(input.ids());
        for (Long id : ids) role(id);
        UnPlatMember target = members.selectById(memberId);
        if (actorAccountId.equals(target.getAccountId())
                && ids.stream().noneMatch(id -> role(id).getRoleType() != null && role(id).getRoleType() == 0))
            throw bad("不能移除当前管理员自己的管理角色");
        memberRoles.delete(new LambdaQueryWrapper<UnPlatMemberRole>().eq(UnPlatMemberRole::getMemberId, memberId));
        for (Long id : ids) {
            UnPlatMemberRole relation = new UnPlatMemberRole();
            relation.setMemberId(memberId);
            relation.setRoleId(id);
            memberRoles.insert(relation);
        }
    }

    /** 查询角色已有菜单授权。 */
    @GetMapping("/roles/{roleId}/menus")
    public List<Long> roleMenus(@PathVariable long roleId, HttpServletRequest request) {
        access.platformAdmin(request);
        role(roleId);
        return roleMenus.selectList(new LambdaQueryWrapper<UnPlatRoleMenu>()
                .eq(UnPlatRoleMenu::getRoleId, roleId)).stream().map(UnPlatRoleMenu::getMenuId).toList();
    }

    /** 全量替换角色菜单授权，拒绝不存在的菜单。 */
    @PutMapping("/roles/{roleId}/menus")
    @Transactional
    public void replaceRoleMenus(@PathVariable long roleId, @RequestBody IdsInput input,
                                 HttpServletRequest request) {
        access.platformAdmin(request);
        role(roleId);
        List<Long> ids = uniqueIds(input.ids());
        for (Long id : ids) menu(id);
        roleMenus.delete(new LambdaQueryWrapper<UnPlatRoleMenu>().eq(UnPlatRoleMenu::getRoleId, roleId));
        for (Long id : ids) {
            UnPlatRoleMenu relation = new UnPlatRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(id);
            roleMenus.insert(relation);
        }
    }

    /** 按角色读取数据范围规则。 */
    @GetMapping("/roles/{roleId}/data-scope")
    public List<UnPlatRoleData> dataScope(@PathVariable long roleId, HttpServletRequest request) {
        access.platformAdmin(request);
        role(roleId);
        return roleData.selectList(new LambdaQueryWrapper<UnPlatRoleData>()
                .eq(UnPlatRoleData::getRoleId, roleId));
    }

    /** 数据范围支持本人、部门、全部或指定部门；指定部门需要有效部门主键。 */
    @PutMapping("/roles/{roleId}/data-scope")
    @Transactional
    public void replaceDataScope(@PathVariable long roleId, @RequestBody List<DataScopeInput> inputs,
                                 HttpServletRequest request) {
        access.platformAdmin(request);
        role(roleId);
        if (inputs == null || inputs.isEmpty()) throw bad("至少选择一个数据范围");
        HashSet<String> seen = new HashSet<>();
        for (DataScopeInput input : inputs) {
            if (input.scopeType() < 1 || input.scopeType() > 6) throw bad("数据范围类型无效");
            long departmentId = input.scopeType() == 6 ? input.departmentId() : 0;
            if (input.scopeType() == 6 && departments.selectById(departmentId) == null)
                throw bad("指定部门不存在");
            if (!seen.add(input.scopeType() + ":" + departmentId)) throw bad("数据范围重复");
        }
        roleData.delete(new LambdaQueryWrapper<UnPlatRoleData>().eq(UnPlatRoleData::getRoleId, roleId));
        for (DataScopeInput input : inputs) {
            UnPlatRoleData item = new UnPlatRoleData();
            item.setRoleId(roleId);
            item.setScopeType(input.scopeType());
            item.setDepartmentId(input.scopeType() == 6 ? input.departmentId() : 0L);
            roleData.insert(item);
        }
    }

    private void fillRole(UnPlatRole item, RoleInput input) {
        item.setRoleCode(input.roleCode().trim());
        item.setRoleName(input.roleName().trim());
        item.setRemark(input.remark());
    }

    private void fillMenu(UnPlatMenu item, MenuInput input) {
        if (input.menuType() < 1 || input.menuType() > 3) throw bad("菜单类型无效");
        item.setParentId(input.parentId() == null ? 0L : input.parentId());
        item.setMenuCode(input.menuCode().trim());
        item.setMenuName(input.menuName().trim());
        item.setMenuType(input.menuType());
        item.setRoutePath(input.routePath());
        item.setPermissionCode(input.permissionCode());
        item.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
    }

    private void checkRoleCode(String code, Long exceptId) {
        LambdaQueryWrapper<UnPlatRole> query = new LambdaQueryWrapper<UnPlatRole>()
                .eq(UnPlatRole::getRoleCode, code.trim());
        if (exceptId != null) query.ne(UnPlatRole::getId, exceptId);
        if (roles.selectCount(query) > 0) throw conflict("角色编码已存在");
    }

    private void checkMenuCode(String code, Long exceptId) {
        LambdaQueryWrapper<UnPlatMenu> query = new LambdaQueryWrapper<UnPlatMenu>()
                .eq(UnPlatMenu::getMenuCode, code.trim());
        if (exceptId != null) query.ne(UnPlatMenu::getId, exceptId);
        if (menus.selectCount(query) > 0) throw conflict("菜单编码已存在");
    }

    private void checkMenuParent(Long parentId, Long currentId) {
        long next = parentId == null ? 0 : parentId;
        for (int depth = 0; next != 0; depth++) {
            if (depth > 100 || currentId != null && next == currentId) throw bad("菜单层级存在循环");
            UnPlatMenu parent = menu(next);
            next = parent.getParentId() == null ? 0 : parent.getParentId();
        }
    }

    private UnPlatRole role(long id) {
        UnPlatRole item = roles.selectById(id);
        if (item == null) throw missing("平台角色不存在");
        return item;
    }

    private UnPlatMenu menu(long id) {
        UnPlatMenu item = menus.selectById(id);
        if (item == null) throw missing("平台菜单不存在");
        return item;
    }

    private void member(long id) {
        if (members.selectById(id) == null) throw missing("平台成员不存在");
    }

    private List<Long> uniqueIds(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) throw bad("授权主键无效或重复");
        return ids;
    }

    private byte status(byte value) {
        if (value != 0 && value != 1) throw bad("状态只能是0或1");
        return value;
    }

    private <T> Page<T> paging(long page, long size) {
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        return new Page<>(page, size);
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
    private ResponseStatusException missing(String text) { return new ResponseStatusException(HttpStatus.NOT_FOUND, text); }

    /** 角色维护只接受业务可编辑字段。 */
    public record RoleInput(@NotBlank @Size(max = 64) String roleCode,
                            @NotBlank @Size(max = 100) String roleName, String remark) {}
    public record MenuInput(Long parentId, @NotBlank @Size(max = 100) String menuCode,
                            @NotBlank @Size(max = 100) String menuName, byte menuType,
                            String routePath, String permissionCode, Integer sortNo) {}
    public record StatusInput(byte status) {}
    public record IdsInput(List<Long> ids) {}
    public record DataScopeInput(byte scopeType, long departmentId) {}
}
