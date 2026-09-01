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
@RequestMapping("/api/admin/platform/ai")
@RequirePermission(resourceType = "PLATFORM", resourceCode = "AI", actionCode = "MANAGE")
public class PlatformAiConfigurationController {
    private final PlatformAiConfigurationService service;

    public PlatformAiConfigurationController(PlatformAiConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<AiConfigurationModels.PlatformOverview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/models")
    public ApiResult<AiConfigurationModels.ModelView> createModel(
            @Valid @RequestBody AiConfigurationModels.ModelRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.createModel(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/models/{modelId}")
    public ApiResult<AiConfigurationModels.ModelView> updateModel(
            @PathVariable Long modelId,
            @Valid @RequestBody AiConfigurationModels.ModelRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.updateModel(AuthenticationContextHolder.require(), modelId, body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/models/{modelId}/grants/{systemId}")
    public ApiResult<AiConfigurationModels.GrantView> grant(
            @PathVariable Long modelId,
            @PathVariable Long systemId,
            @Valid @RequestBody AiConfigurationModels.GrantRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.grant(AuthenticationContextHolder.require(), modelId, systemId, body,
                TraceIdFilter.current(request)));
    }
}
