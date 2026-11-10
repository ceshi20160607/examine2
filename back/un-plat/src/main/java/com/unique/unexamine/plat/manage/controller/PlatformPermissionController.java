package com.unique.unexamine.plat.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MenuGrant;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MenuRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MenuView;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.DataGrant;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberRolesRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleDataRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleMenusRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleView;
import com.unique.unexamine.plat.manage.service.PlatformPermissionManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台菜单、角色及授权关系接口；具体校验和审计由平台权限 Service 处理。 */
@RestController
@RequestMapping("/api/platform/permissions")
@Tag(name = "平台角色权限")
public class PlatformPermissionController {
    private final PlatformPermissionManageService service;

    public PlatformPermissionController(PlatformPermissionManageService service) {
        this.service = service;
    }

    /** 查询平台有效菜单。 */
    @GetMapping("/menus")
    @Operation(summary = "查询平台菜单")
    public Result<List<MenuView>> menus(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.menus(token));
    }

    /** 保存平台菜单。 */
    @PutMapping("/menus")
    @Operation(summary = "保存平台菜单")
    public Result<MenuView> saveMenu(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody MenuRequest request) {
        return Result.ok(service.saveMenu(token, request));
    }

    /** 停用平台菜单。 */
    @PostMapping("/menus/{menuId}/disable")
    @Operation(summary = "停用平台菜单")
    public Result<MenuView> disableMenu(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long menuId) {
        return Result.ok(service.disableMenu(token, menuId));
    }

    /** 保存平台角色。 */
    @PutMapping("/roles")
    @Operation(summary = "保存平台角色")
    public Result<RoleView> saveRole(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody RoleRequest request) {
        return Result.ok(service.saveRole(token, request));
    }

    /** 查询成员当前角色。 */
    @GetMapping("/members/{memberId}/roles")
    @Operation(summary = "查询成员角色")
    public Result<List<Long>> memberRoles(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long memberId) {
        return Result.ok(service.memberRoles(token, memberId));
    }

    /** 替换成员角色关系。 */
    @PutMapping("/members/{memberId}/roles")
    @Operation(summary = "保存成员角色")
    public Result<List<Long>> saveMemberRoles(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long memberId, @Valid @RequestBody MemberRolesRequest request) {
        return Result.ok(service.saveMemberRoles(token, memberId, request));
    }

    /** 查询角色菜单动作授权。 */
    @GetMapping("/roles/{roleId}/menus")
    @Operation(summary = "查询角色菜单授权")
    public Result<List<MenuGrant>> roleMenus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long roleId) {
        return Result.ok(service.roleMenus(token, roleId));
    }

    /** 替换角色菜单动作授权。 */
    @PutMapping("/roles/{roleId}/menus")
    @Operation(summary = "保存角色菜单授权")
    public Result<List<MenuGrant>> saveRoleMenus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long roleId, @Valid @RequestBody RoleMenusRequest request) {
        return Result.ok(service.saveRoleMenus(token, roleId, request));
    }

    /** 查询角色数据范围配置。 */
    @GetMapping("/roles/{roleId}/data")
    @Operation(summary = "查询角色数据范围")
    public Result<List<DataGrant>> roleData(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long roleId) {
        return Result.ok(service.roleData(token, roleId));
    }

    /** 替换角色数据范围配置。 */
    @PutMapping("/roles/{roleId}/data")
    @Operation(summary = "保存角色数据范围")
    public Result<List<DataGrant>> saveRoleData(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long roleId, @Valid @RequestBody RoleDataRequest request) {
        return Result.ok(service.saveRoleData(token, roleId, request));
    }
}
