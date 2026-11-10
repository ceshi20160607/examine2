package com.unique.unexamine.sys.flow.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.FlowView;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.SaveFlowRequest;
import com.unique.unexamine.sys.flow.manage.service.PlatformFlowDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台内 Flow 管理入口；对外服务仍须通过应用授权。 */
@RestController
@RequestMapping("/api/platform/flows")
@Tag(name = "平台 Flow 管理")
public class PlatformFlowDefinitionController {
    private final PlatformFlowDefinitionService service;

    public PlatformFlowDefinitionController(PlatformFlowDefinitionService service) {
        this.service = service;
    }

    /** 查询平台 Flow 定义及启停状态。 */
    @GetMapping
    @Operation(summary = "查询平台 Flow")
    public Result<List<FlowView>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.list(token));
    }

    /** 查询平台 Flow 当前发布快照和版本历史。 */
    @GetMapping("/{flowId}")
    @Operation(summary = "查询平台 Flow 详情")
    public Result<FlowView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long flowId) {
        return Result.ok(service.query(token, flowId));
    }

    /** 保存平台 Flow 并立即启用新快照。 */
    @PutMapping
    @Operation(summary = "保存平台 Flow")
    public Result<FlowView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SaveFlowRequest request) {
        return Result.ok(service.save(token, request));
    }

    /** 停用平台 Flow；历史实例仍保留原快照。 */
    @PostMapping("/{flowId}/disable")
    @Operation(summary = "停用平台 Flow")
    public Result<FlowView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long flowId) {
        return Result.ok(service.disable(token, flowId));
    }
}
