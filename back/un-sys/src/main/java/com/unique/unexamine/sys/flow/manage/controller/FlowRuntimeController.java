package com.unique.unexamine.sys.flow.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionRequest;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView;
import com.unique.unexamine.sys.flow.manage.service.FlowRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 业务记录详情页使用的 Flow 实例和节点办理入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/records/{recordId}/flows")
@Tag(name = "模块 Flow 运行")
public class FlowRuntimeController {
    private final FlowRuntimeService service;

    public FlowRuntimeController(FlowRuntimeService service) {
        this.service = service;
    }

    /** 返回记录关联的 Flow 实例、当前状态和全部节点历史。 */
    @GetMapping
    @Operation(summary = "查询记录 Flow")
    public Result<List<ExampleView>> list(@RequestHeader(value = "Authorization", required = false) String token,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                          @PathVariable Long systemId, @PathVariable Long moduleId,
                                          @PathVariable Long recordId) {
        return Result.ok(service.listForRecord(token, systemId, tenantId, moduleId, recordId));
    }

    /** 查询一个 Flow 实例的待办和历史节点。 */
    @GetMapping("/{exampleId}")
    @Operation(summary = "查询 Flow 实例")
    public Result<ExampleView> query(@RequestHeader(value = "Authorization", required = false) String token,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                     @PathVariable Long systemId, @PathVariable Long moduleId,
                                     @PathVariable Long recordId, @PathVariable Long exampleId) {
        return Result.ok(service.query(token, systemId, tenantId, moduleId, recordId, exampleId));
    }

    /** 执行当前流程节点；审批办理由 examine 独立入口负责。 */
    @PostMapping("/{exampleId}/tasks/{taskId}/actions")
    @Operation(summary = "办理 Flow 节点")
    public Result<ActionView> action(@RequestHeader(value = "Authorization", required = false) String token,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                     @PathVariable Long systemId, @PathVariable Long moduleId,
                                     @PathVariable Long recordId, @PathVariable Long exampleId,
                                     @PathVariable Long taskId, @Valid @RequestBody ActionRequest request) {
        return Result.ok(service.action(token, systemId, tenantId, moduleId, recordId, exampleId, taskId, request));
    }
}
