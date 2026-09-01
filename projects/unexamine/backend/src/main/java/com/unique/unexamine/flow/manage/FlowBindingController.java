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

    public FlowBindingController(FlowBindingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<FlowBindingModels.BindingList> list(
            @RequestParam Long moduleId, @RequestParam(required = false) String triggerEvent) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), moduleId, triggerEvent));
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
}
