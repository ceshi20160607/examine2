package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleMenu;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 模块组名称和顺序管理当前运行导航，只有有权管理模块的成员可修改。 */
    @PutMapping("/groups/{groupId}")
    public ApiResult<ConfiguredModuleGroup> updateGroup(@PathVariable Long groupId,
                                                         @Valid @RequestBody UpdateModuleGroupRequest request,
                                                         HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.updateGroup(AuthenticationContextHolder.require(), groupId,
                request, TraceIdFilter.current(servletRequest)));
    }

    /** 含有效模块的分组不得删除，空分组保留历史编码。 */
    @DeleteMapping("/groups/{groupId}")
    public ApiResult<ConfiguredModuleGroup> deleteGroup(@PathVariable Long groupId, @RequestParam Integer version,
                                                         HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.deleteGroup(AuthenticationContextHolder.require(), groupId,
                version, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/modules")
    public ApiResult<ModuleDraft> createModule(
            @Valid @RequestBody CreateModuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createModule(
                AuthenticationContextHolder.require(), request, TraceIdFilter.current(servletRequest)));
    }

    /** 复制配置为独立草稿，不复制业务记录、授权或发布版本。 */
    @PostMapping("/modules/{moduleId}/copy")
    public ApiResult<ModuleDraft> copyModule(@PathVariable Long moduleId,
                                              @Valid @RequestBody CopyModuleRequest request,
                                              HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.copyModule(AuthenticationContextHolder.require(), moduleId,
                request, TraceIdFilter.current(servletRequest)));
    }

    /** 修改模块显示名称后生成新草稿修订，运行态仍按发布版本显示。 */
    @PutMapping("/modules/{moduleId}/name")
    public ApiResult<ConfiguredModule> renameModule(@PathVariable Long moduleId,
                                                     @Valid @RequestBody RenameModuleRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.renameModule(AuthenticationContextHolder.require(), moduleId,
                request, TraceIdFilter.current(servletRequest)));
    }

    /** 删除未发布模块草稿；已发布或被其他模块引用时由业务层拒绝。 */
    @DeleteMapping("/modules/{moduleId}")
    public ApiResult<ConfiguredModule> deleteModule(@PathVariable Long moduleId, @RequestParam Integer version,
                                                     HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.deleteModule(AuthenticationContextHolder.require(), moduleId,
                version, TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/draft")
    public ApiResult<ModuleDraft> draft(@PathVariable Long moduleId) {
        return ApiResult.ok(configurationService.draft(AuthenticationContextHolder.require(), moduleId));
    }

    /** 转化配置读取目标当前发布字段，沿用模块配置查看权限及系统、租户边界。 */
    @GetMapping("/modules/{moduleId}/published")
    public ApiResult<RuntimeModuleConfiguration> published(@PathVariable Long moduleId) {
        var context = AuthenticationContextHolder.require();
        var draft = configurationService.draft(context, moduleId);
        return ApiResult.ok(publicationService.published(context, draft.module().getCode()));
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

    /** 删除尚未发布且没有页面、规则或索引引用的字段草稿。 */
    @DeleteMapping("/modules/{moduleId}/fields/{fieldId}")
    public ApiResult<ConfiguredModuleField> deleteField(@PathVariable Long moduleId, @PathVariable Long fieldId,
                                                        @RequestParam Integer version, HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.deleteField(AuthenticationContextHolder.require(), moduleId,
                fieldId, version, TraceIdFilter.current(servletRequest)));
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

    /** 页面显示名称作为草稿发布，页面编码保持稳定。 */
    @PutMapping("/modules/{moduleId}/pages/{pageType}/name")
    public ApiResult<ConfiguredModulePage> renamePage(@PathVariable Long moduleId, @PathVariable String pageType,
                                                       @Valid @RequestBody RenameModulePageRequest request,
                                                       HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.renamePage(AuthenticationContextHolder.require(), moduleId,
                pageType, request, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/modules/{moduleId}/pages")
    public ApiResult<ConfiguredModulePage> createPage(
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateModulePageRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createPage(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }

    @DeleteMapping("/modules/{moduleId}/pages/{pageType}")
    public ApiResult<ConfiguredModulePage> deletePage(
            @PathVariable Long moduleId,
            @PathVariable String pageType,
            @RequestParam Integer version,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.deletePage(
                AuthenticationContextHolder.require(), moduleId, pageType, version,
                TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/modules/{moduleId}/menus/{menuId}")
    public ApiResult<CfgModuleMenu> updateMenu(
            @PathVariable Long moduleId,
            @PathVariable Long menuId,
            @Valid @RequestBody UpdateModuleMenuRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.updateMenu(
                AuthenticationContextHolder.require(), moduleId, menuId, request, TraceIdFilter.current(servletRequest)));
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

    @PostMapping("/modules/{moduleId}/actions")
    public ApiResult<ConfiguredModuleAction> createCustomAction(
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateCustomActionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createCustomAction(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }

    /** 所属人可给已建模块补建可选打印动作，权限和版本仍走模块发布流程。 */
    @PostMapping("/modules/{moduleId}/actions/print")
    public ApiResult<ConfiguredModuleAction> createPrintAction(@PathVariable Long moduleId,
                                                                HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createPrintAction(AuthenticationContextHolder.require(),
                moduleId, TraceIdFilter.current(servletRequest)));
    }

    /** 审批动作作为模块草稿配置，须经模块发布和 Flow 绑定后才可从记录发起。 */
    @PostMapping("/modules/{moduleId}/actions/approval")
    public ApiResult<ConfiguredModuleAction> createApprovalAction(@PathVariable Long moduleId,
                                                                   HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createApprovalAction(AuthenticationContextHolder.require(),
                moduleId, TraceIdFilter.current(servletRequest)));
    }

    /** 模块所属人在草稿中配置可选首页入口，发布后才对运行态生效。 */
    @PostMapping("/modules/{moduleId}/actions/dashboard")
    public ApiResult<ConfiguredModuleAction> createDashboardAction(@PathVariable Long moduleId,
                                                                    HttpServletRequest servletRequest) {
        return ApiResult.ok(configurationService.createDashboardAction(AuthenticationContextHolder.require(),
                moduleId, TraceIdFilter.current(servletRequest)));
    }

    /** 已发布旧模板的模块只发布 PRINT 过渡版本，不顺带发布其他草稿修改。 */
    @PostMapping("/modules/{moduleId}/legacy-print-transition")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PUBLISH")
    public ApiResult<PublishedModuleResult> transitionLegacyPrintAction(
            @PathVariable Long moduleId, @RequestParam Integer expectedPublicationVersion,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(publicationService.transitionLegacyPrintAction(AuthenticationContextHolder.require(),
                moduleId, expectedPublicationVersion, TraceIdFilter.current(servletRequest)));
    }

    /** 仅发布仪表盘入口动作，防止顺带发布未完成的模块草稿。 */
    @PostMapping("/modules/{moduleId}/dashboard-action-publish")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PUBLISH")
    public ApiResult<PublishedModuleResult> publishDashboardAction(
            @PathVariable Long moduleId, @RequestParam Integer expectedPublicationVersion,
            @RequestParam Integer expectedActionVersion, HttpServletRequest servletRequest) {
        return ApiResult.ok(publicationService.publishDashboardAction(AuthenticationContextHolder.require(),
                moduleId, expectedPublicationVersion, expectedActionVersion, TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/publication-check")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PREVIEW")
    public ApiResult<PublicationCheckResult> publicationCheck(@PathVariable Long moduleId) {
        return ApiResult.ok(publicationService.check(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/modules/{moduleId}/publish")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PUBLISH")
    public ApiResult<PublishedModuleResult> publish(
            @PathVariable Long moduleId,
            @Valid @RequestBody PublishModuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(publicationService.publish(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/versions")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PREVIEW")
    public ApiResult<List<PublishedVersionSummary>> versions(@PathVariable Long moduleId) {
        return ApiResult.ok(publicationService.versions(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/modules/{moduleId}/rollback")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "ROLLBACK")
    public ApiResult<RuntimeModuleConfiguration> rollback(
            @PathVariable Long moduleId,
            @Valid @RequestBody RollbackModuleRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(publicationService.rollback(
                AuthenticationContextHolder.require(), moduleId, request, TraceIdFilter.current(servletRequest)));
    }
}
