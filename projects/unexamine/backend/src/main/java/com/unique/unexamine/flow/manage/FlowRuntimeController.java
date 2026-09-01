package com.unique.unexamine.flow.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flow-runtime")
public class FlowRuntimeController {
    private final FlowRuntimeService service;

    public FlowRuntimeController(FlowRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/instances")
    public ApiResult<List<FlowRuntimeModels.InstanceView>> instances() {
        return ApiResult.ok(service.instances(AuthenticationContextHolder.require()));
    }

    @PostMapping("/instances")
    public ApiResult<FlowRuntimeModels.ActionResult> start(
            @Valid @RequestBody FlowRuntimeModels.StartRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.start(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResult<FlowRuntimeModels.InstanceView> instance(@PathVariable Long instanceId) {
        return ApiResult.ok(service.instance(AuthenticationContextHolder.require(), instanceId));
    }

    @PostMapping("/tasks/{taskId}/actions")
    public ApiResult<FlowRuntimeModels.ActionResult> handle(
            @PathVariable Long taskId, @Valid @RequestBody FlowRuntimeModels.HandleTaskRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.handle(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/instances/{instanceId}/actions")
    public ApiResult<FlowRuntimeModels.ActionResult> actOnInstance(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.InstanceActionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.actOnInstance(AuthenticationContextHolder.require(), instanceId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/instances/{instanceId}/manual-node-preview")
    public ApiResult<FlowRuntimeModels.ManualNodePreview> previewManualNode(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.ManualNodeRequest body) {
        return ApiResult.ok(service.previewManualNode(AuthenticationContextHolder.require(), instanceId, body));
    }

    @PostMapping("/instances/{instanceId}/manual-nodes")
    public ApiResult<FlowRuntimeModels.ManualNodeResult> addManualNode(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.ManualNodeRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.addManualNode(AuthenticationContextHolder.require(), instanceId, body,
                TraceIdFilter.current(request)));
    }
}
