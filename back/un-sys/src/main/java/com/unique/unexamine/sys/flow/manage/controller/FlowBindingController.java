package com.unique.unexamine.sys.flow.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.FlowBindingDto.BindingView;
import com.unique.unexamine.sys.flow.manage.dto.FlowBindingDto.SaveBindingRequest;
import com.unique.unexamine.sys.flow.manage.service.FlowBindingService;
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

/** Flow 触发绑定管理入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/flow-bindings")
@Tag(name = "Flow 触发绑定")
public class FlowBindingController {
    private final FlowBindingService service;

    public FlowBindingController(FlowBindingService service) {
        this.service = service;
    }

    /** 查询当前系统租户的 Flow 绑定。 */
    @GetMapping
    @Operation(summary = "查询 Flow 绑定")
    public Result<List<BindingView>> list(@RequestHeader(value = "Authorization", required = false) String token,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                          @PathVariable Long systemId) {
        return Result.ok(service.list(token, systemId, tenantId));
    }

    /** 保存 Flow 触发绑定。 */
    @PutMapping
    @Operation(summary = "保存 Flow 绑定")
    public Result<BindingView> save(@RequestHeader(value = "Authorization", required = false) String token,
                                    @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                    @PathVariable Long systemId,
                                    @Valid @RequestBody SaveBindingRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 停用触发绑定。 */
    @PostMapping("/{bindingId}/disable")
    @Operation(summary = "停用 Flow 绑定")
    public Result<BindingView> disable(@RequestHeader(value = "Authorization", required = false) String token,
                                       @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                       @PathVariable Long systemId, @PathVariable Long bindingId) {
        return Result.ok(service.disable(token, systemId, tenantId, bindingId));
    }
}
