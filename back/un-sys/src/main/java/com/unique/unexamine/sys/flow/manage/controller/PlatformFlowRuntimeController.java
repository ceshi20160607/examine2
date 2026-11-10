package com.unique.unexamine.sys.flow.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionRequest;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.StartRequest;
import com.unique.unexamine.sys.flow.manage.service.FlowRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台 Flow 的发起、查询和普通节点办理入口；审批节点仍由 examine 负责。 */
@RestController
@RequestMapping("/api/platform/flow-runtime")
@Tag(name = "平台 Flow 运行")
public class PlatformFlowRuntimeController {
    private final FlowRuntimeService service;

    public PlatformFlowRuntimeController(FlowRuntimeService service) {
        this.service = service;
    }

    /** 当前平台账号仍待处理的 Flow 实例。 */
    @GetMapping("/pending")
    @Operation(summary = "查询平台 Flow 待办")
    public Result<Page<ExampleView>> pending(@RequestHeader(value = "Authorization", required = false) String token,
                                             @ModelAttribute SearchBO search) {
        return Result.ok(service.pendingPlatform(token, search));
    }

    /** 平台账号发起一条独立 Flow。 */
    @PostMapping("/start")
    @Operation(summary = "发起平台 Flow")
    public Result<ExampleView> start(@RequestHeader(value = "Authorization", required = false) String token,
                                     @Valid @RequestBody StartRequest request) {
        return Result.ok(service.startPlatform(token, request.flowId(), request.requestId(), request.values()));
    }

    /** 查询当前平台账号可见的 Flow 实例。 */
    @GetMapping
    @Operation(summary = "查询平台 Flow 实例")
    public Result<List<ExampleView>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.listPlatform(token));
    }

    /** 查询平台 Flow 实例详情。 */
    @GetMapping("/{exampleId}")
    @Operation(summary = "查询平台 Flow 实例详情")
    public Result<ExampleView> query(@RequestHeader(value = "Authorization", required = false) String token,
                                     @PathVariable Long exampleId) {
        return Result.ok(service.queryPlatform(token, exampleId));
    }

    /** 办理平台 Flow 普通节点；审批等待节点不能在此入口直接办理。 */
    @PostMapping("/{exampleId}/tasks/{taskId}/actions")
    @Operation(summary = "办理平台 Flow 节点")
    public Result<ActionView> action(@RequestHeader(value = "Authorization", required = false) String token,
                                     @PathVariable Long exampleId, @PathVariable Long taskId,
                                     @Valid @RequestBody ActionRequest request) {
        return Result.ok(service.actionPlatform(token, exampleId, taskId, request));
    }
}
