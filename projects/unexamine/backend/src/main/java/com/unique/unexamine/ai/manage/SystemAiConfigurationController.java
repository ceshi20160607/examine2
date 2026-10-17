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
    private final PlatformAiConfigurationService models;

    public SystemAiConfigurationController(SystemAiConfigurationService service,
            PlatformAiConfigurationService models) {
        this.service = service;
        this.models = models;
    }

    /** 运行态 VIEW 只允许使用助手，后台模型和工具策略必须持有 MANAGE。 */
    @GetMapping
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
    public ApiResult<AiConfigurationModels.SystemOverview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    /** 系统管理员配置本系统专属的第三方模型和受控密钥引用。 */
    @PostMapping("/models")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
    public ApiResult<AiConfigurationModels.ModelView> createModel(
            @Valid @RequestBody AiConfigurationModels.ModelRequest body, HttpServletRequest request) {
        return ApiResult.ok(models.createSystemModel(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    /** 系统只能修改自己创建的第三方模型。 */
    @PutMapping("/models/{modelId}")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
    public ApiResult<AiConfigurationModels.ModelView> updateModel(
            @PathVariable Long modelId, @Valid @RequestBody AiConfigurationModels.ModelRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(models.updateSystemModel(AuthenticationContextHolder.require(), modelId, body,
                TraceIdFilter.current(request)));
    }

    /** 停用后撤销自建模型授权，已发布 Agent 后续调用会降级。 */
    @PostMapping("/models/{modelId}/disable")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
    public ApiResult<AiConfigurationModels.ModelView> disableModel(
            @PathVariable Long modelId, HttpServletRequest request) {
        return ApiResult.ok(models.disableSystemModel(AuthenticationContextHolder.require(), modelId,
                TraceIdFilter.current(request)));
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

    /** 权限预览包含发布人的策略快照，仅后台管理者可读取。 */
    @GetMapping("/agents/{agentId}/preview")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "MANAGE")
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
