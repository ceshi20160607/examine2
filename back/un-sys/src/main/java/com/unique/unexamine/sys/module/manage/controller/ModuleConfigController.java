package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.CreateGroupRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.CreateModuleRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.FieldDefinitionView;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.FormView;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.ModuleView;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.NavigationGroup;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.SaveFieldRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.UpdateModuleRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto.TypeView;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldMigrationDto.Preview;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模块配置和表单接口，布局保存由 manage Service 处理。 */
@RestController
@RequestMapping("/api/systems/{systemId}")
@Tag(name = "模块配置")
public class ModuleConfigController {
    private final ModuleConfigService service;

    /** 注入模块配置业务服务。 */
    public ModuleConfigController(ModuleConfigService service) {
        this.service = service;
    }

    /** 创建运行态导航模块组。 */
    @PostMapping("/module-groups")
    @Operation(summary = "创建模块组")
    public Result<Long> createGroup(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody CreateGroupRequest request) {
        return Result.ok(service.createGroup(token, systemId, tenantId, request));
    }

    /** 在模块组内创建业务模块。 */
    @PostMapping("/modules")
    @Operation(summary = "创建模块")
    public Result<ModuleView> createModule(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody CreateModuleRequest request) {
        return Result.ok(service.createModule(token, systemId, tenantId, request));
    }

    /** 登录成员根据具体列表动作权限读取可用的模块组及模块。 */
    @GetMapping("/navigation")
    @Operation(summary = "查询获权模块导航")
    public Result<java.util.List<NavigationGroup>> navigation(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.navigation(token,systemId,tenantId));
    }

    /** 保存设计器给出的字段位置和占位宽度。 */
    @PostMapping("/modules/{moduleId}/fields")
    @Operation(summary = "保存模块字段并立即生效")
    public Result<Long> saveField(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody SaveFieldRequest request) {
        return Result.ok(service.saveField(token, systemId, tenantId, moduleId, request));
    }

    /** 设计器只使用固定类型目录，不接受任意类型名称。 */
    @GetMapping("/field-types")
    @Operation(summary = "查询固定字段类型")
    public Result<java.util.List<TypeView>> fieldTypes(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.fieldTypes(token, systemId, tenantId));
    }

    /** 读取当前字段顺序和布局。 */
    @GetMapping("/modules/{moduleId}/fields")
    @Operation(summary = "读取当前字段及稳定标识")
    public Result<java.util.List<FieldDefinitionView>> fields(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        return Result.ok(service.fieldViews(token, systemId, tenantId, moduleId));
    }

    /** 列出拟保存字段配置影响的旧值和记录，供管理员决定迁移映射。 */
    @PostMapping("/modules/{moduleId}/fields/{fieldId}/migration-preview")
    @Operation(summary = "预览字段变更影响")
    public Result<Preview> previewMigration(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long fieldId,
            @Valid @RequestBody SaveFieldRequest request) {
        return Result.ok(service.previewMigration(token,systemId,tenantId,moduleId,fieldId,request));
    }

    /** 模块名称、说明和启停保存即生效。 */
    @PutMapping("/modules/{moduleId}")
    @Operation(summary = "保存模块设置")
    public Result<ModuleView> updateModule(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody UpdateModuleRequest request) {
        return Result.ok(service.updateModule(token, systemId, tenantId, moduleId, request));
    }

    /** 新建页面读取当前保存的字段布局。 */
    @GetMapping("/modules/{moduleId}/form")
    @Operation(summary = "读取新建表单")
    public Result<FormView> form(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        return Result.ok(service.newForm(token, systemId, tenantId, moduleId));
    }
}
