package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.sys.module.manage.service.ModuleRecordListService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通用模块记录接口，主记录和自定义值都交由业务 Service 编排。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/records")
@Tag(name = "模块记录")
public class ModuleRecordController {
    private final ModuleRecordService service;
    private final ModuleRecordListService listService;

    /** 注入模块记录业务服务。 */
    public ModuleRecordController(ModuleRecordService service,ModuleRecordListService listService) {
        this.service = service;
        this.listService = listService;
    }

    /** 新增或编辑主记录和当前字段值。 */
    @PostMapping
    @Operation(summary = "保存业务记录")
    public ModuleRecordService.RecordView save(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody ModuleRecordService.SaveRecordRequest request) {
        return service.save(token, systemId, tenantId, moduleId, request);
    }

    /** 按全部、本租户或共享范围分页读取未删除记录。 */
    @GetMapping
    @Operation(summary = "模块记录列表")
    public ModuleRecordService.RecordPage list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return service.queryPageList(token, systemId, tenantId, moduleId, scope,page, size);
    }

    /** 保存的场景和临时条件在数据库分页之前执行，并返回权限内列表列和值。 */
    @PostMapping("/query")
    @Operation(summary = "按场景和高级筛选查询模块记录")
    public ModuleRecordListService.Result query(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @RequestBody ModuleRecordListService.Query request) {
        return listService.query(token,systemId,tenantId,moduleId,request);
    }

    /** 按主键只读基础记录。 */
    @GetMapping("/{recordId}")
    @Operation(summary = "查询基础记录")
    public ModuleRecordService.RecordView byId(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        return service.queryById(token, systemId, tenantId, moduleId, recordId);
    }

    /** 使用已保存的表单配置绑定业务字段值。 */
    @GetMapping("/{recordId}/information")
    @Operation(summary = "业务记录详情表单")
    public ModuleRecordService.RecordInformation information(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        return service.information(token, systemId, tenantId, moduleId, recordId);
    }

    /** 按记录和字段读取权限查询操作历史。 */
    @GetMapping("/{recordId}/operations")
    @Operation(summary = "读取业务操作历史")
    public ModuleRecordService.OperationPage operations(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return service.operations(token, systemId, tenantId, moduleId, recordId, page, size);
    }

    /** 逻辑删除业务记录并保留字段值及历史。 */
    @DeleteMapping("/{recordId}")
    @Operation(summary = "删除业务记录")
    public void delete(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        service.delete(token, systemId, tenantId, moduleId, recordId);
    }

    /** 列表批量选择后整批软删除，任一记录无权限时整批不生效。 */
    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除业务记录")
    public void deleteByIds(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody ModuleRecordService.DeleteRecordsRequest request) {
        service.deleteByIds(token,systemId,tenantId,moduleId,request);
    }
}
