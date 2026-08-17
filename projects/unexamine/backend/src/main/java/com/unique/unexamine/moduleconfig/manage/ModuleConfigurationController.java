package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
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
@RequestMapping("/api/admin/module-config")
@RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "MANAGE")
public class ModuleConfigurationController {
    private final ModuleConfigurationService configurationService;
    private final ModulePublicationService publicationService;

    public ModuleConfigurationController(
            ModuleConfigurationService configurationService,
            ModulePublicationService publicationService) {
        this.configurationService = configurationService;
        this.publicationService = publicationService;
    }

    @GetMapping
    public ApiResult<ModuleConfigurationOverview> overview() {
        return ApiResult.ok(configurationService.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/groups")
    public ApiResult<ConfiguredModuleGroup> createGroup(
            @Valid @RequestBody CreateModuleGroupRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createGroup(
                AuthenticationContextHolder.require(), request, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/modules")
    public ApiResult<ModuleDraft> createModule(
            @Valid @RequestBody CreateModuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createModule(
                AuthenticationContextHolder.require(), request, TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/draft")
    public ApiResult<ModuleDraft> draft(@PathVariable Long moduleId) {
        return ApiResult.ok(configurationService.draft(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/modules/{moduleId}/fields")
    public ApiResult<ConfiguredModuleField> createField(
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateModuleFieldRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createField(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/modules/{moduleId}/fields/{fieldId}")
    public ApiResult<ConfiguredModuleField> updateField(
            @PathVariable Long moduleId,
            @PathVariable Long fieldId,
            @Valid @RequestBody UpdateModuleFieldRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.updateField(
                AuthenticationContextHolder.require(), moduleId, fieldId, request, TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/modules/{moduleId}/pages/{pageType}")
    public ApiResult<ConfiguredModulePage> updatePage(
            @PathVariable Long moduleId,
            @PathVariable String pageType,
            @Valid @RequestBody UpdatePageConfigurationRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.updatePage(
                AuthenticationContextHolder.require(), moduleId, pageType, request, TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/modules/{moduleId}/actions/{actionCode}")
    public ApiResult<ConfiguredModuleAction> updateAction(
            @PathVariable Long moduleId,
            @PathVariable String actionCode,
            @Valid @RequestBody UpdateActionConfigurationRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.updateAction(
                AuthenticationContextHolder.require(), moduleId, actionCode, request, TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/publication-check")
    public ApiResult<PublicationCheckResult> publicationCheck(@PathVariable Long moduleId) {
        return ApiResult.ok(publicationService.check(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/modules/{moduleId}/publish")
    public ApiResult<PublishedModuleResult> publish(
            @PathVariable Long moduleId,
            @Valid @RequestBody PublishModuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(publicationService.publish(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/versions")
    public ApiResult<List<PublishedVersionSummary>> versions(@PathVariable Long moduleId) {
        return ApiResult.ok(publicationService.versions(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/modules/{moduleId}/rollback")
    public ApiResult<RuntimeModuleConfiguration> rollback(
            @PathVariable Long moduleId,
            @Valid @RequestBody RollbackModuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(publicationService.rollback(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }
}
