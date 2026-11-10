package com.unique.unexamine.examine.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ModuleExamineDto.View;
import com.unique.unexamine.examine.manage.dto.ModuleExamineDto.SaveRequest;
import com.unique.unexamine.examine.manage.service.ModuleExamineBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 模块审批触发绑定管理入口；绑定保存即生效，审批运行数据由 examine 独立保留。 */
@RestController
@RequestMapping("/api/systems/{systemId}/module-examines")
@Tag(name = "模块审批绑定管理")
public class ModuleExamineBindingController {
    private final ModuleExamineBindingService service;

    public ModuleExamineBindingController(ModuleExamineBindingService service) {
        this.service = service;
    }

    /** 查询当前租户的模块审批绑定，可按模块过滤。 */
    @GetMapping
    @Operation(summary = "查询模块审批绑定")
    public Result<List<View>> list(@RequestHeader(value = "Authorization", required = false) String token,
                                   @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                   @PathVariable Long systemId,
                                   @RequestParam(required = false) Long moduleId) {
        return Result.ok(service.list(token, systemId, tenantId, moduleId));
    }

    /** 查询单个模块审批绑定。 */
    @GetMapping("/{bindingId}")
    @Operation(summary = "查询模块审批绑定详情")
    public Result<View> query(@RequestHeader(value = "Authorization", required = false) String token,
                              @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                              @PathVariable Long systemId, @PathVariable Long bindingId) {
        return Result.ok(service.query(token, systemId, tenantId, bindingId));
    }

    /** 保存并启用模块审批绑定。 */
    @PutMapping
    @Operation(summary = "保存模块审批绑定")
    public Result<View> save(@RequestHeader(value = "Authorization", required = false) String token,
                             @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                             @PathVariable Long systemId, @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 停用模块审批绑定；历史审批实例继续保留。 */
    @PostMapping("/{bindingId}/disable")
    @Operation(summary = "停用模块审批绑定")
    public Result<View> disable(@RequestHeader(value = "Authorization", required = false) String token,
                                @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                @PathVariable Long systemId, @PathVariable Long bindingId) {
        return Result.ok(service.disable(token, systemId, tenantId, bindingId));
    }
}
