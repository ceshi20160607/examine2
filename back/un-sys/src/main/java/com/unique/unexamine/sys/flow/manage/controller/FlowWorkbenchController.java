package com.unique.unexamine.sys.flow.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView;
import com.unique.unexamine.sys.flow.manage.service.FlowRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统 Flow 待办工作台；候选人和模块详情权限由运行服务统一核对。 */
@RestController
@RequestMapping("/api/systems/{systemId}/flow-tasks")
@Tag(name = "Flow 工作台")
public class FlowWorkbenchController {
    private final FlowRuntimeService service;

    public FlowWorkbenchController(FlowRuntimeService service) {
        this.service = service;
    }

    /** 当前系统租户中当前账号仍待处理的 Flow 实例。 */
    @GetMapping
    @Operation(summary = "查询 Flow 待办")
    public Result<Page<ExampleView>> pending(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @ModelAttribute SearchBO search) {
        return Result.ok(service.pending(token, systemId, tenantId, search));
    }
}
