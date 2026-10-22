package com.unique.unexamine.sys.module.manage;

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
    public Long member(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody ModulePermissionService.MemberRequest request) { return service.addMember(token,systemId,tenantId,request); }
    /** 创建普通业务角色。 */
    @PostMapping("/roles")
    @Operation(summary="创建租户角色")
    public Long role(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody ModulePermissionService.RoleRequest request) { return service.createRole(token,systemId,tenantId,request); }
    /** 赋予或撤销成员角色。 */
    @PostMapping("/assignments")
    @Operation(summary="配置成员角色")
    public void assign(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody ModulePermissionService.AssignRequest request) { service.assign(token,systemId,tenantId,request); }
    /** 同一角色各动作可以配置不同数据和字段范围。 */
    @PostMapping("/roles/{roleId}/modules/{moduleId}/{action}")
    @Operation(summary="配置角色模块动作和数据字段权限")
    public void grant(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long roleId,@PathVariable Long moduleId,@PathVariable String action,
            @Valid @RequestBody ModulePermissionService.GrantRequest request) { service.grant(token,systemId,tenantId,roleId,moduleId,action,request); }
    /** 主租户管理员设置目标租户的字段上限。 */
    @PostMapping("/tenants/{tenantId}/modules/{moduleId}/fields")
    @Operation(summary="配置租户字段许可")
    public void tenantFields(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable Long systemId,@PathVariable Long tenantId,@PathVariable Long moduleId,
            @RequestBody ModulePermissionService.FieldAccess request) { service.tenantFields(token,systemId,tenantId,moduleId,request); }
}
