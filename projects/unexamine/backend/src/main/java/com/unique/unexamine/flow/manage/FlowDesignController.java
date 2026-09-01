package com.unique.unexamine.flow.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flows")
public class FlowDesignController {
    private final FlowDesignService service;

    public FlowDesignController(FlowDesignService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<FlowDesignModels.FlowView>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<FlowDesignModels.FlowView> create(
            @Valid @RequestBody FlowDesignModels.CreateFlowRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{flowId}")
    public ApiResult<FlowDesignModels.FlowView> detail(@PathVariable Long flowId) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), flowId));
    }

    @PutMapping("/{flowId}/draft")
    public ApiResult<FlowDesignModels.FlowView> saveDraft(
            @PathVariable Long flowId, @Valid @RequestBody FlowDesignModels.SaveDraftRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveDraft(AuthenticationContextHolder.require(), flowId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{flowId}/publication-check")
    public ApiResult<FlowDesignModels.PublicationCheck> publicationCheck(@PathVariable Long flowId) {
        return ApiResult.ok(service.publicationCheck(AuthenticationContextHolder.require(), flowId));
    }

    @PostMapping("/{flowId}/simulate")
    public ApiResult<FlowDesignModels.SimulationResult> simulate(
            @PathVariable Long flowId, @RequestBody(required = false) FlowDesignModels.SimulateRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.simulate(AuthenticationContextHolder.require(), flowId,
                body == null ? null : body.variables(), TraceIdFilter.current(request)));
    }

    @PostMapping("/{flowId}/publish")
    public ApiResult<FlowDesignModels.PublicationResult> publish(
            @PathVariable Long flowId, @Valid @RequestBody FlowDesignModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), flowId, body,
                TraceIdFilter.current(request)));
    }
}
