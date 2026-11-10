package com.unique.unexamine.sys.flow.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.FlowView;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.SaveFlowRequest;
import com.unique.unexamine.sys.flow.manage.service.FlowDefinitionService;
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

/** 系统 Flow 设计器管理入口；保存立即发布新版本。 */
@RestController
@RequestMapping("/api/systems/{systemId}/flows")
@Tag(name = "Flow 定义管理")
public class FlowDefinitionController {
    private final FlowDefinitionService service;

    public FlowDefinitionController(FlowDefinitionService service) {
        this.service = service;
    }

    /** 查询当前系统租户的 Flow 定义。 */
    @GetMapping
    @Operation(summary = "查询 Flow 定义")
    public Result<List<FlowView>> list(@RequestHeader(value = "Authorization", required = false) String token,
                                       @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                       @PathVariable Long systemId) {
        return Result.ok(service.list(token, systemId, tenantId));
    }

    /** 查询 Flow 当前快照和版本历史。 */
    @GetMapping("/{flowId}")
    @Operation(summary = "查询 Flow 详情")
    public Result<FlowView> query(@RequestHeader(value = "Authorization", required = false) String token,
                                  @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                  @PathVariable Long systemId, @PathVariable Long flowId) {
        return Result.ok(service.query(token, systemId, tenantId, flowId));
    }

    /** 校验并保存 Flow，立即启用新发布版本。 */
    @PutMapping
    @Operation(summary = "保存并启用 Flow")
    public Result<FlowView> save(@RequestHeader(value = "Authorization", required = false) String token,
                                 @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                 @PathVariable Long systemId, @Valid @RequestBody SaveFlowRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 停用 Flow 和新触发绑定，历史实例继续使用自己的版本快照。 */
    @PostMapping("/{flowId}/disable")
    @Operation(summary = "停用 Flow")
    public Result<FlowView> disable(@RequestHeader(value = "Authorization", required = false) String token,
                                    @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                    @PathVariable Long systemId, @PathVariable Long flowId) {
        return Result.ok(service.disable(token, systemId, tenantId, flowId));
    }
}
