package com.unique.unexamine.manage.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.UnSysDepartment;
import com.unique.unexamine.base.entity.UnSysMemberRole;
import com.unique.unexamine.base.entity.UnSysMenu;
import com.unique.unexamine.base.entity.UnSysRole;
import com.unique.unexamine.base.entity.UnSysRoleData;
import com.unique.unexamine.base.entity.UnSysRoleMenu;
import com.unique.unexamine.base.entity.UnSysTenantMember;
import com.unique.unexamine.base.mapper.UnSysDepartmentMapper;
import com.unique.unexamine.base.mapper.UnSysMemberRoleMapper;
import com.unique.unexamine.base.mapper.UnSysMenuMapper;
import com.unique.unexamine.base.mapper.UnSysRoleDataMapper;
import com.unique.unexamine.base.mapper.UnSysRoleMapper;
import com.unique.unexamine.base.mapper.UnSysRoleMenuMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMemberMapper;
import com.unique.unexamine.manage.common.SessionAccess;
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

/** 租户角色、菜单及数据权限管理，所有关联主键按租户核对。 */
@RestController
@RequestMapping("/api/system/tenants/{tenantId}")
public class TenantAuthorizationController {
    private final UnSysRoleMapper roles;
    private final UnSysMenuMapper menus;
    private final UnSysTenantMemberMapper members;
    private final UnSysDepartmentMapper departments;
    private final UnSysMemberRoleMapper memberRoles;
    private final UnSysRoleMenuMapper roleMenus;
    private final UnSysRoleDataMapper roleData;
    private final SessionAccess access;

    public TenantAuthorizationController(UnSysRoleMapper roles, UnSysMenuMapper menus,
                                         UnSysTenantMemberMapper members, UnSysDepartmentMapper departments,
                                         UnSysMemberRoleMapper memberRoles, UnSysRoleMenuMapper roleMenus,
                                         UnSysRoleDataMapper roleData, SessionAccess access) {
        this.roles = roles;
        this.menus = menus;
        this.members = members;
        this.departments = departments;
        this.memberRoles = memberRoles;
        this.roleMenus = roleMenus;
        this.roleData = roleData;
        this.access = access;
    }

    /** 角色列表受租户范围、分页和固定排序列约束。 */
    @GetMapping("/roles")
    public IPage<UnSysRole> roles(@PathVariable long tenantId,
                                 @RequestParam(defaultValue = "1") long page,
                                 @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(defaultValue = "createTime") String sort,
                                 HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        LambdaQueryWrapper<UnSysRole> query = new LambdaQueryWrapper<UnSysRole>()
                .eq(UnSysRole::getTenantId, tenantId);
        switch (sort) {
            case "roleCode" -> query.orderByAsc(UnSysRole::getRoleCode);
            case "roleName" -> query.orderByAsc(UnSysRole::getRoleName);
            case "createTime" -> query.orderByDesc(UnSysRole::getCreateTime);
            default -> throw bad("不支持的角色排序字段");
        }
        return roles.selectPage(paging(page, size), query);
    }

    /** 租户管理员只能新增自定义角色。 */
    @PostMapping("/roles")
    public UnSysRole createRole(@PathVariable long tenantId, @Valid @RequestBody RoleInput input,
                                HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        checkRoleCode(tenantId, input.roleCode(), null);
        UnSysRole item = new UnSysRole();
        item.setTenantId(tenantId);
        fillRole(item, input);
        item.setRoleType((byte) 1);
        item.setStatus((byte) 1);
        roles.insert(item);
        return item;
    }

    /** 修改角色前确认其确实属于当前租户。 */
    @PutMapping("/roles/{id}")
    public UnSysRole updateRole(@PathVariable long tenantId, @PathVariable long id,
                                @Valid @RequestBody RoleInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysRole item = role(tenantId, id);
        if (item.getRoleType() != null && item.getRoleType() == 0 && !item.getRoleCode().equals(input.roleCode()))
            throw bad("不能修改内置角色编码");
        checkRoleCode(tenantId, input.roleCode(), id);
        fillRole(item, input);
        roles.updateById(item);
        return item;
    }

    /** 内置管理员角色不可停用，以保留租户管理入口。 */
    @PutMapping("/roles/{id}/status")
    public UnSysRole roleStatus(@PathVariable long tenantId, @PathVariable long id,
                                @RequestBody StatusInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysRole item = role(tenantId, id);
        if (item.getRoleType() != null && item.getRoleType() == 0 && input.status() == 0)
            throw bad("不能停用内置管理员角色");
        item.setStatus(status(input.status()));
        roles.updateById(item);
        return item;
    }

    /** 菜单树只返回当前租户的数据。 */
    @GetMapping("/menus")
    public List<UnSysMenu> menus(@PathVariable long tenantId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        return menus.selectList(new LambdaQueryWrapper<UnSysMenu>()
                .eq(UnSysMenu::getTenantId, tenantId)
                .orderByAsc(UnSysMenu::getParentId, UnSysMenu::getSortNo, UnSysMenu::getId));
    }

    /** 菜单编码在租户内唯一。 */
    @PostMapping("/menus")
    public UnSysMenu createMenu(@PathVariable long tenantId, @Valid @RequestBody MenuInput input,
                                HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        checkMenuCode(tenantId, input.menuCode(), null);
        checkMenuParent(tenantId, input.parentId(), null);
        UnSysMenu item = new UnSysMenu();
        item.setTenantId(tenantId);
        fillMenu(item, input);
        item.setStatus((byte) 1);
        menus.insert(item);
        return item;
    }

    /** 菜单移动前验证父级属于当前租户且不形成环。 */
    @PutMapping("/menus/{id}")
    public UnSysMenu updateMenu(@PathVariable long tenantId, @PathVariable long id,
                                @Valid @RequestBody MenuInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysMenu item = menu(tenantId, id);
        checkMenuCode(tenantId, input.menuCode(), id);
        checkMenuParent(tenantId, input.parentId(), id);
        fillMenu(item, input);
        menus.updateById(item);
        return item;
    }

    /** 停用菜单保留角色配置以便重新启用。 */
    @PutMapping("/menus/{id}/status")
    public UnSysMenu menuStatus(@PathVariable long tenantId, @PathVariable long id,
                                @RequestBody StatusInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysMenu item = menu(tenantId, id);
        item.setStatus(status(input.status()));
        menus.updateById(item);
        return item;
    }

    /** 读取成员在当前租户中的角色授权。 */
    @GetMapping("/members/{memberId}/roles")
    public List<Long> memberRoles(@PathVariable long tenantId, @PathVariable long memberId,
                                  HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        member(tenantId, memberId);
        return memberRoles.selectList(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getTenantMemberId, memberId)).stream().map(UnSysMemberRole::getRoleId).toList();
    }

    /** 全量授权前验证所有角色同属租户，并保留当前管理员自己的管理权限。 */
    @PutMapping("/members/{memberId}/roles")
    @Transactional
    public void replaceMemberRoles(@PathVariable long tenantId, @PathVariable long memberId,
                                   @RequestBody IdsInput input, HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantAdmin(request, tenantId);
        member(tenantId, memberId);
        List<Long> ids = uniqueIds(input.ids());
        for (Long id : ids) role(tenantId, id);
        if (actor.getId().equals(memberId) && ids.stream().noneMatch(id -> role(tenantId, id).getRoleType() == 0))
            throw bad("不能移除当前管理员自己的管理角色");
        memberRoles.delete(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getTenantMemberId, memberId));
        for (Long id : ids) {
            UnSysMemberRole relation = new UnSysMemberRole();
            relation.setTenantMemberId(memberId);
            relation.setRoleId(id);
            memberRoles.insert(relation);
        }
    }

    /** 读取角色当前菜单授权。 */
    @GetMapping("/roles/{roleId}/menus")
    public List<Long> roleMenus(@PathVariable long tenantId, @PathVariable long roleId,
                                HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        role(tenantId, roleId);
        return roleMenus.selectList(new LambdaQueryWrapper<UnSysRoleMenu>()
                .eq(UnSysRoleMenu::getRoleId, roleId)).stream().map(UnSysRoleMenu::getMenuId).toList();
    }

    /** 全量替换角色菜单，拒绝任何跨租户菜单主键。 */
    @PutMapping("/roles/{roleId}/menus")
    @Transactional
    public void replaceRoleMenus(@PathVariable long tenantId, @PathVariable long roleId,
                                 @RequestBody IdsInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        role(tenantId, roleId);
        List<Long> ids = uniqueIds(input.ids());
        for (Long id : ids) menu(tenantId, id);
        roleMenus.delete(new LambdaQueryWrapper<UnSysRoleMenu>().eq(UnSysRoleMenu::getRoleId, roleId));
        for (Long id : ids) {
            UnSysRoleMenu relation = new UnSysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(id);
            roleMenus.insert(relation);
        }
    }

    /** 读取角色数据范围。 */
    @GetMapping("/roles/{roleId}/data-scope")
    public List<UnSysRoleData> dataScope(@PathVariable long tenantId, @PathVariable long roleId,
                                        HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        role(tenantId, roleId);
        return roleData.selectList(new LambdaQueryWrapper<UnSysRoleData>()
                .eq(UnSysRoleData::getRoleId, roleId));
    }

    /** 指定部门的数据范围必须引用当前租户部门。 */
    @PutMapping("/roles/{roleId}/data-scope")
    @Transactional
    public void replaceDataScope(@PathVariable long tenantId, @PathVariable long roleId,
                                 @RequestBody List<DataScopeInput> inputs, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        role(tenantId, roleId);
        if (inputs == null || inputs.isEmpty()) throw bad("至少选择一个数据范围");
        HashSet<String> seen = new HashSet<>();
        for (DataScopeInput input : inputs) {
            if (input.scopeType() < 1 || input.scopeType() > 6) throw bad("数据范围类型无效");
            long departmentId = input.scopeType() == 6 ? input.departmentId() : 0;
            if (input.scopeType() == 6) department(tenantId, departmentId);
            if (!seen.add(input.scopeType() + ":" + departmentId)) throw bad("数据范围重复");
        }
        roleData.delete(new LambdaQueryWrapper<UnSysRoleData>().eq(UnSysRoleData::getRoleId, roleId));
        for (DataScopeInput input : inputs) {
            UnSysRoleData item = new UnSysRoleData();
            item.setRoleId(roleId);
            item.setScopeType(input.scopeType());
            item.setDepartmentId(input.scopeType() == 6 ? input.departmentId() : 0L);
            roleData.insert(item);
        }
    }

    private void fillRole(UnSysRole item, RoleInput input) {
        item.setRoleCode(input.roleCode().trim());
        item.setRoleName(input.roleName().trim());
        item.setRemark(input.remark());
    }

    private void fillMenu(UnSysMenu item, MenuInput input) {
        if (input.menuType() < 1 || input.menuType() > 3) throw bad("菜单类型无效");
        item.setParentId(input.parentId() == null ? 0L : input.parentId());
        item.setMenuCode(input.menuCode().trim());
        item.setMenuName(input.menuName().trim());
        item.setMenuType(input.menuType());
        item.setRoutePath(input.routePath());
        item.setPermissionCode(input.permissionCode());
        item.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
    }

    private void checkRoleCode(long tenantId, String code, Long currentId) {
        LambdaQueryWrapper<UnSysRole> query = new LambdaQueryWrapper<UnSysRole>()
                .eq(UnSysRole::getTenantId, tenantId).eq(UnSysRole::getRoleCode, code.trim());
        if (currentId != null) query.ne(UnSysRole::getId, currentId);
        if (roles.selectCount(query) > 0) throw conflict("租户角色编码已存在");
    }

    private void checkMenuCode(long tenantId, String code, Long currentId) {
        LambdaQueryWrapper<UnSysMenu> query = new LambdaQueryWrapper<UnSysMenu>()
                .eq(UnSysMenu::getTenantId, tenantId).eq(UnSysMenu::getMenuCode, code.trim());
        if (currentId != null) query.ne(UnSysMenu::getId, currentId);
        if (menus.selectCount(query) > 0) throw conflict("租户菜单编码已存在");
    }

    private void checkMenuParent(long tenantId, Long parentId, Long currentId) {
        long next = parentId == null ? 0 : parentId;
        for (int depth = 0; next != 0; depth++) {
            if (depth > 100 || currentId != null && next == currentId) throw bad("菜单层级存在循环");
            UnSysMenu parent = menu(tenantId, next);
            next = parent.getParentId() == null ? 0 : parent.getParentId();
        }
    }

    private UnSysRole role(long tenantId, long id) {
        UnSysRole item = roles.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) throw missing("租户角色不存在");
        return item;
    }

    private UnSysMenu menu(long tenantId, long id) {
        UnSysMenu item = menus.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) throw missing("租户菜单不存在");
        return item;
    }

    private void member(long tenantId, long id) {
        UnSysTenantMember item = members.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) throw missing("租户成员不存在");
    }

    private void department(long tenantId, long id) {
        UnSysDepartment item = departments.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) throw missing("租户部门不存在");
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

    /** 客户端不可修改租户主键与角色类型。 */
    public record RoleInput(@NotBlank @Size(max = 64) String roleCode,
                            @NotBlank @Size(max = 100) String roleName, String remark) {}
    public record MenuInput(Long parentId, @NotBlank @Size(max = 100) String menuCode,
                            @NotBlank @Size(max = 100) String menuName, byte menuType,
                            String routePath, String permissionCode, Integer sortNo) {}
    public record StatusInput(byte status) {}
    public record IdsInput(List<Long> ids) {}
    public record DataScopeInput(byte scopeType, long departmentId) {}
}
