package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.entity.po.ModuleMenu;
import com.unique.examine.module.entity.po.ModuleRole;
import com.unique.examine.web.service.SystemModuleRbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "自建系统态-module RBAC")
@RestController
@RequestMapping("/v1/system/module/rbac")
public class SystemModuleRbacController {

    @Autowired
    private SystemModuleRbacService systemModuleRbacService;

    @Operation(summary = "角色列表（按 appId）")
    @GetMapping("/apps/{appId}/roles")
    public ApiResult<List<ModuleRole>> listRoles(@PathVariable("appId") Long appId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleRbacService.listRoles(appId, platId));
    }

    @Operation(summary = "菜单列表（按 appId）")
    @GetMapping("/apps/{appId}/menus")
    public ApiResult<List<ModuleMenu>> listMenus(@PathVariable("appId") Long appId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleRbacService.listMenus(appId, platId));
    }

    public record UpsertRoleBody(Long id, String roleCode, String roleName, Integer status) {}

    @Operation(summary = "新增/更新角色（按 appId）")
    @PostMapping("/apps/{appId}/roles/upsert")
    public ApiResult<ModuleRole> upsertRole(@PathVariable("appId") Long appId, @RequestBody UpsertRoleBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleRbacService.upsertRole(appId, platId, body));
    }

    public record UpsertMenuBody(Long id,
                                 Long parentId,
                                 String menuName,
                                 Long pageId,
                                 Integer sortNo,
                                 Integer visibleFlag,
                                 String permKey,
                                 String apiPattern) {}

    @Operation(summary = "新增/更新菜单（按 appId）")
    @PostMapping("/apps/{appId}/menus/upsert")
    public ApiResult<ModuleMenu> upsertMenu(@PathVariable("appId") Long appId, @RequestBody UpsertMenuBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleRbacService.upsertMenu(appId, platId, body));
    }

    public record SetRoleMenuPermBody(Long roleId, List<Long> menuIds, Integer permLevel) {}

    @Operation(summary = "设置角色菜单权限（覆盖写；按 roleId）")
    @PostMapping("/roles/menu-perms/set")
    public ApiResult<Void> setRoleMenuPerms(@RequestBody SetRoleMenuPermBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleRbacService.setRoleMenuPerms(platId, body);
        return ApiResult.ok();
    }

    public record AssignMemberRoleBody(Long appId, Long memberPlatId, Long roleId) {}

    @Operation(summary = "给成员分配角色（按 appId + memberPlatId；覆盖写 roleId）")
    @PostMapping("/members/assign-role")
    public ApiResult<ModuleMember> assignMemberRole(@RequestBody AssignMemberRoleBody body) {
        Long operatorPlatId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleRbacService.assignMemberRole(operatorPlatId, body));
    }
}

