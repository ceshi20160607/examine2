package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordStatusDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordStatusDto.View;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 模块状态配置入口，业务状态与审批状态分别管理。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/statuses")
@Tag(name="模块业务状态")
public class ModuleRecordStatusController {
    private final ModuleRecordStatusService service;
    public ModuleRecordStatusController(ModuleRecordStatusService service) { this.service=service; }

    @GetMapping
    @Operation(summary="查询模块业务状态")
    public Result<List<View>> list(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(service.configuration(token,systemId,tenantId,moduleId));
    }

    @PutMapping
    @Operation(summary="保存模块自定义业务状态")
    public Result<View> save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token,systemId,tenantId,moduleId,request));
    }
}
