package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.AssignRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.FieldAccess;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.FieldGrant;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.FieldsRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.GrantRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.MemberRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto.RoleRequest;
import com.unique.unexamine.sys.module.manage.service.ModulePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 管理员配置入口；角色、成员和动作变更由同一业务服务保证授权及缓存一致性。 */
@RestController
@RequestMapping("/api/systems/{systemId}/permissions")
@Tag(name="系统模块授权")
public class ModulePermissionController {
    private final ModulePermissionService service;
    /** 注入统一的模块授权服务。 */
    public ModulePermissionController(ModulePermissionService service) { this.service=service; }
    /** 将现有账号加入当前租户，返回租户成员关系 ID。 */
    @PostMapping("/members")
    @Operation(summary="添加租户成员或调整部门")
    public Result<Long> member(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody MemberRequest request) { return Result.ok(service.addMember(token,systemId,tenantId,request)); }
    /** 创建普通业务角色。 */
    @PostMapping("/roles")
    @Operation(summary="创建租户角色")
    public Result<Long> role(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody RoleRequest request) { return Result.ok(service.createRole(token,systemId,tenantId,request)); }
    /** 赋予或撤销成员角色。 */
    @PostMapping("/assignments")
    @Operation(summary="配置成员角色")
    public Result<Void> assign(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody AssignRequest request) { service.assign(token,systemId,tenantId,request);
        return Result.ok();
    }
    /** 菜单许可和记录范围由角色菜单关系控制。 */
    @PostMapping("/roles/{roleId}/modules/{moduleId}/menus/{menuId}")
    @Operation(summary="配置角色菜单和数据范围")
    public Result<Void> grant(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long roleId,@PathVariable Long moduleId,@PathVariable Long menuId,
            @Valid @RequestBody GrantRequest request) { service.grant(token,systemId,tenantId,roleId,moduleId,menuId,request);
        return Result.ok();
    }
    /** 字段授权独立保存，新增字段不会继承普通角色的旧许可。 */
    @PostMapping("/roles/{roleId}/modules/{moduleId}/fields")
    @Operation(summary="配置角色模块字段许可")
    public Result<Void> fields(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long roleId,@PathVariable Long moduleId,
            @Valid @RequestBody FieldsRequest request) { service.saveFields(token,systemId,tenantId,roleId,moduleId,request);
        return Result.ok();
    }
    /** 后台字段授权回显。 */
    @GetMapping("/roles/{roleId}/modules/{moduleId}/fields")
    @Operation(summary="读取角色模块字段许可")
    public Result<java.util.List<FieldGrant>> fields(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long roleId,@PathVariable Long moduleId) { return Result.ok(service.configuredFields(token,systemId,tenantId,roleId,moduleId)); }
    /** 主租户管理员设置目标租户的字段上限。 */
    @PostMapping("/tenants/{tenantId}/modules/{moduleId}/fields")
    @Operation(summary="配置租户字段许可")
    public Result<Void> tenantFields(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable Long systemId,@PathVariable Long tenantId,@PathVariable Long moduleId,
            @RequestBody FieldAccess request) { service.tenantFields(token,systemId,tenantId,moduleId,request);
        return Result.ok();
    }
}
