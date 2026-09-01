package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleRule;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.CreateIndexRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.CreateRuleRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.QueryIndexDraft;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.RuleIndexDraft;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.RuleTestResult;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.TestRuleRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.UpdateIndexRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.UpdateRuleRequest;
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
@RequestMapping("/api/admin/module-config/modules/{moduleId}/rules-indexes")
@RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "MANAGE")
public class ModuleRuleConfigurationController {
    private final ModuleRuleConfigurationService service;

    public ModuleRuleConfigurationController(ModuleRuleConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<RuleIndexDraft> draft(@PathVariable Long moduleId) {
        return ApiResult.ok(service.draft(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/rules")
    public ApiResult<CfgModuleRule> createRule(
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateRuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.createRule(AuthenticationContextHolder.require(), moduleId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/rules/{ruleId}")
    public ApiResult<CfgModuleRule> updateRule(
            @PathVariable Long moduleId,
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateRuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.updateRule(AuthenticationContextHolder.require(), moduleId, ruleId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/rules/{ruleId}/test")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PREVIEW")
    public ApiResult<RuleTestResult> testRule(
            @PathVariable Long moduleId,
            @PathVariable Long ruleId,
            @Valid @RequestBody TestRuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.testRule(AuthenticationContextHolder.require(), moduleId, ruleId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/indexes")
    public ApiResult<QueryIndexDraft> createIndex(
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateIndexRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.createIndex(AuthenticationContextHolder.require(), moduleId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/indexes/{indexId}")
    public ApiResult<QueryIndexDraft> updateIndex(
            @PathVariable Long moduleId,
            @PathVariable Long indexId,
            @Valid @RequestBody UpdateIndexRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.updateIndex(AuthenticationContextHolder.require(), moduleId, indexId, request,
                TraceIdFilter.current(servletRequest)));
    }
}
