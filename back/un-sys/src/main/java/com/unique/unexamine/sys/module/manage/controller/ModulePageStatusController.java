package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModulePageStatusDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePageStatusDto.View;
import com.unique.unexamine.sys.module.manage.service.ModulePageStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审批状态字段属于模块页面配置，可把不同动作的审批映射到同一字段。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/pages/approval-statuses")
@Tag(name="模块页面审批状态配置")
public class ModulePageStatusController {
    private final ModulePageStatusService service;
    public ModulePageStatusController(ModulePageStatusService service) { this.service=service; }

    /** 列出本模块配置的全部审批状态组。 */
    @GetMapping
    @Operation(summary="查询模块页面审批状态组")
    public Result<List<View>> list(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(service.configuration(token,systemId,tenantId,moduleId));
    }

    /** 新建、编辑、删除及自定义动作通过各自审批绑定选用这个状态组。 */
    @PutMapping
    @Operation(summary="保存模块页面审批状态组")
    public Result<View> save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token,systemId,tenantId,moduleId,request));
    }
}
