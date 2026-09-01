package com.unique.unexamine.ai.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
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

@RestController
@RequestMapping("/api/admin/system/ai")
public class SystemAiConfigurationController {
    private final SystemAiConfigurationService service;

    public SystemAiConfigurationController(SystemAiConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "VIEW")
    public ApiResult<AiConfigurationModels.SystemOverview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/agents")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
    public ApiResult<AiConfigurationModels.AgentView> createAgent(
            @Valid @RequestBody AiConfigurationModels.AgentDraftRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.createAgent(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/agents/{agentId}/draft")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
    public ApiResult<AiConfigurationModels.AgentView> saveDraft(
            @PathVariable Long agentId,
            @Valid @RequestBody AiConfigurationModels.AgentDraftRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveDraft(AuthenticationContextHolder.require(), agentId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/agents/{agentId}/preview")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "VIEW")
    public ApiResult<AiConfigurationModels.AgentPreview> preview(@PathVariable Long agentId) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), agentId));
    }

    @PostMapping("/agents/{agentId}/publish")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "PUBLISH")
    public ApiResult<AiConfigurationModels.PublishResult> publish(
            @PathVariable Long agentId,
            @Valid @RequestBody AiConfigurationModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), agentId, body,
                TraceIdFilter.current(request)));
    }
}
