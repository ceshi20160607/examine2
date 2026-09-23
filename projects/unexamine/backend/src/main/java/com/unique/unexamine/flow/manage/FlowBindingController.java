package com.unique.unexamine.flow.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flow-bindings")
public class FlowBindingController {
    private final FlowBindingService service;
    private final FlowTriggerEventService events;

    public FlowBindingController(FlowBindingService service, FlowTriggerEventService events) {
        this.service = service;
        this.events = events;
    }

    @GetMapping
    public ApiResult<FlowBindingModels.BindingList> list(
            @RequestParam Long moduleId, @RequestParam(required = false) Long actionId,
            @RequestParam(required = false) String triggerEvent) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), moduleId, actionId, triggerEvent));
    }

    @PostMapping("/publish")
    public ApiResult<FlowBindingModels.BindingView> publish(
            @Valid @RequestBody FlowBindingModels.PublishBindingRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/resolve")
    public ApiResult<FlowBindingModels.ResolutionView> resolve(
            @Valid @RequestBody FlowBindingModels.ResolveBindingRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.resolve(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/condition-fields")
    public ApiResult<java.util.List<FlowBindingModels.ConditionField>> conditionFields(
            @RequestParam Long moduleId, @RequestParam String triggerEvent) {
        return ApiResult.ok(service.conditionFields(AuthenticationContextHolder.require(), moduleId, triggerEvent));
    }

    @GetMapping("/events")
    public ApiResult<java.util.List<FlowBindingModels.EventDeliveryView>> events(
            @RequestParam Long moduleId, @RequestParam String triggerEvent) {
        var context = AuthenticationContextHolder.require();
        // 复用绑定列表的模块范围和查看权限校验，不开放任意发件箱查询。
        service.list(context, moduleId, null, triggerEvent);
        return ApiResult.ok(events.recent(context, moduleId, triggerEvent));
    }

    @PostMapping("/preview-condition")
    public ApiResult<FlowBindingModels.ConditionPreview> previewCondition(
            @Valid @RequestBody FlowBindingModels.PreviewConditionRequest body) {
        return ApiResult.ok(service.previewCondition(AuthenticationContextHolder.require(), body));
    }
}
