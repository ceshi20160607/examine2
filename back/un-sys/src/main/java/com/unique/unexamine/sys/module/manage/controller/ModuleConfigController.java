package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldMigrationService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模块配置和表单接口，布局保存与发布由 manage Service 处理。 */
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
    public Long createGroup(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody ModuleConfigService.CreateGroupRequest request) {
        return service.createGroup(token, systemId, tenantId, request);
    }

    /** 在模块组内创建业务模块。 */
    @PostMapping("/modules")
    @Operation(summary = "创建模块")
    public ModuleConfigService.ModuleView createModule(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody ModuleConfigService.CreateModuleRequest request) {
        return service.createModule(token, systemId, tenantId, request);
    }

    /** 登录成员根据具体列表动作权限读取可用的模块组及模块。 */
    @GetMapping("/navigation")
    @Operation(summary = "查询获权模块导航")
    public java.util.List<ModuleConfigService.NavigationGroup> navigation(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return service.navigation(token,systemId,tenantId);
    }

    /** 保存设计器给出的字段位置和占位宽度。 */
    @PostMapping("/modules/{moduleId}/fields")
    @Operation(summary = "保存模块字段草稿")
    public Long saveField(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody ModuleConfigService.SaveFieldRequest request) {
        return service.saveField(token, systemId, tenantId, moduleId, request);
    }

    /** 设计器只使用固定类型目录，不接受任意类型名称。 */
    @GetMapping("/field-types")
    @Operation(summary = "查询固定字段类型")
    public java.util.List<ModuleFieldService.TypeView> fieldTypes(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return service.fieldTypes(token, systemId, tenantId);
    }

    /** 发布字段顺序和布局快照。 */
    @GetMapping("/modules/{moduleId}/fields")
    @Operation(summary = "读取发布字段及稳定标识")
    public java.util.List<ModuleConfigService.PublishedFieldView> fields(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        return service.publishedFieldViews(token, systemId, tenantId, moduleId);
    }

    /** 列出当前字段草稿影响的旧值和记录，供管理员决定迁移映射。 */
    @GetMapping("/modules/{moduleId}/fields/{fieldId}/migration-preview")
    @Operation(summary = "预览字段变更影响")
    public ModuleFieldMigrationService.Preview previewMigration(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long fieldId) {
        return service.previewMigration(token,systemId,tenantId,moduleId,fieldId);
    }

    /** 发布字段顺序和布局快照。 */
    @PostMapping("/modules/{moduleId}/publish")
    @Operation(summary = "发布模块配置")
    public ModuleConfigService.ModuleView publish(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @RequestBody(required = false) ModuleConfigService.PublishRequest request) {
        return service.publish(token, systemId, tenantId, moduleId,request);
    }

    /** 新建页面读取已经发布的字段布局。 */
    @GetMapping("/modules/{moduleId}/form")
    @Operation(summary = "读取新建表单")
    public ModuleConfigService.FormView form(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        return service.newForm(token, systemId, tenantId, moduleId);
    }
}
