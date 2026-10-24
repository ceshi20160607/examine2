package com.unique.unexamine.sys.module.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.DeleteRecordsRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.OperationView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RecordInformation;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RecordView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.SaveRecordRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ListView;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordListService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import com.unique.unexamine.sys.module.manage.service.ModuleRelationFieldService;
import com.unique.unexamine.sys.module.manage.service.ModuleRelationFieldService.Option;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final ModuleRelationFieldService relationFields;

    /** 注入模块记录业务服务。 */
    public ModuleRecordController(ModuleRecordService service,ModuleRecordListService listService,
            ModuleRelationFieldService relationFields) {
        this.service = service;
        this.listService = listService;
        this.relationFields = relationFields;
    }

    /** 关联字段的选择弹窗只返回目标模块当前有权读取的记录和配置展示列。 */
    @GetMapping("/relation-fields/{fieldKey}/options")
    @Operation(summary = "查询关联模块字段的可选记录")
    public Result<Page<Option>> relationOptions(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@PathVariable String fieldKey,
            @RequestParam String operation,@ModelAttribute SearchBO search) {
        return Result.ok(relationFields.options(token,systemId,tenantId,moduleId,fieldKey,operation,search));
    }

    /** 新增或编辑主记录和当前字段值。 */
    @PostMapping
    @Operation(summary = "保存业务记录")
    public Result<RecordView> save(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody SaveRecordRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, moduleId, request));
    }

    /** 按全部、本租户或共享范围分页读取未删除记录。 */
    @GetMapping
    @Operation(summary = "模块记录列表")
    public Result<Page<RecordView>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @ModelAttribute ModuleRecordSearchBO search) {
        return Result.ok(service.queryPageList(token, systemId, tenantId, moduleId, search));
    }

    /** 保存的场景和临时条件在数据库分页之前执行，并返回权限内列表列和值。 */
    @PostMapping("/query")
    @Operation(summary = "按场景和高级筛选查询模块记录")
    public Result<ListView> query(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @RequestBody ModuleRecordSearchBO request) {
        return Result.ok(listService.query(token,systemId,tenantId,moduleId,request));
    }

    /** 按主键只读基础记录。 */
    @GetMapping("/{recordId}")
    @Operation(summary = "查询基础记录")
    public Result<RecordView> byId(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        return Result.ok(service.queryById(token, systemId, tenantId, moduleId, recordId));
    }

    /** 使用已保存的表单配置绑定业务字段值。 */
    @GetMapping("/{recordId}/information")
    @Operation(summary = "业务记录详情表单")
    public Result<RecordInformation> information(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        return Result.ok(service.information(token, systemId, tenantId, moduleId, recordId));
    }

    /** 按记录和字段读取权限查询操作历史。 */
    @GetMapping("/{recordId}/operations")
    @Operation(summary = "读取业务操作历史")
    public Result<Page<OperationView>> operations(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId,
            @ModelAttribute SearchBO search) {
        return Result.ok(service.operations(token, systemId, tenantId, moduleId, recordId, search));
    }

    /** 逻辑删除业务记录并保留字段值及历史。 */
    @DeleteMapping("/{recordId}")
    @Operation(summary = "删除业务记录")
    public Result<Void> delete(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        service.delete(token, systemId, tenantId, moduleId, recordId);
        return Result.ok();
    }

    /** 列表批量选择后整批软删除，任一记录无权限时整批不生效。 */
    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除业务记录")
    public Result<Void> deleteByIds(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody DeleteRecordsRequest request) {
        service.deleteByIds(token,systemId,tenantId,moduleId,request);
        return Result.ok();
    }
}
