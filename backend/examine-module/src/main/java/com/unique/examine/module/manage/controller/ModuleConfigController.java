package com.unique.examine.module.manage.controller;

import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.ActionConfigSaveBO;
import com.unique.examine.module.manage.bo.AppSaveBO;
import com.unique.examine.module.manage.bo.AppUpdateBO;
import com.unique.examine.module.manage.bo.ConfigStatusBO;
import com.unique.examine.module.manage.bo.FieldSaveBO;
import com.unique.examine.module.manage.bo.FieldUpdateBO;
import com.unique.examine.module.manage.bo.MenuConfigSaveBO;
import com.unique.examine.module.manage.bo.ModuleSaveBO;
import com.unique.examine.module.manage.bo.ModuleUpdateBO;
import com.unique.examine.module.manage.bo.PageSchemaSaveBO;
import com.unique.examine.module.manage.bo.PublishRequestBO;
import com.unique.examine.module.manage.service.ModuleConfigService;
import com.unique.examine.module.manage.vo.ActionConfigVO;
import com.unique.examine.module.manage.vo.AppVO;
import com.unique.examine.module.manage.vo.FieldTypeVO;
import com.unique.examine.module.manage.vo.FieldVO;
import com.unique.examine.module.manage.vo.MenuConfigVO;
import com.unique.examine.module.manage.vo.ModuleVO;
import com.unique.examine.module.manage.vo.PageSchemaVO;
import com.unique.examine.module.manage.vo.PublishCheckResultVO;
import com.unique.examine.module.manage.vo.PublishVersionVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用、模块、字段和页面配置接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}")
public class ModuleConfigController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ModuleConfigService moduleConfigService;

    private final AuthSessionService authSessionService;

    /**
     * 查询应用列表。
     */
    @Operation(summary = "查询应用列表")
    @GetMapping("/apps")
    public List<AppVO> listApps(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        validateLogin(authorization);
        return moduleConfigService.listApps(systemId, tenantId, keyword, status);
    }

    /**
     * 创建应用草稿。
     */
    @Operation(summary = "创建应用草稿")
    @PostMapping("/apps")
    public AppVO createApp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            @Valid @RequestBody AppSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.createApp(systemId, saveBO);
    }

    /**
     * 查询应用详情。
     */
    @Operation(summary = "查询应用详情")
    @GetMapping("/apps/{appId}")
    public AppVO getApp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            @PathVariable Long appId) {
        validateLogin(authorization);
        return moduleConfigService.getApp(systemId, appId);
    }

    /**
     * 更新应用。
     */
    @Operation(summary = "更新应用")
    @PutMapping("/apps/{appId}")
    public AppVO updateApp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            @PathVariable Long appId, @Valid @RequestBody AppUpdateBO updateBO) {
        validateLogin(authorization);
        return moduleConfigService.updateApp(systemId, appId, updateBO);
    }

    /**
     * 变更应用状态。
     */
    @Operation(summary = "变更应用状态")
    @PatchMapping("/apps/{appId}/status")
    public AppVO changeAppStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long appId, @Valid @RequestBody ConfigStatusBO statusBO) {
        validateLogin(authorization);
        return moduleConfigService.changeAppStatus(systemId, appId, statusBO);
    }

    /**
     * 查询应用下模块列表。
     */
    @Operation(summary = "查询应用下模块列表")
    @GetMapping("/apps/{appId}/modules")
    public List<ModuleVO> listModules(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long appId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        validateLogin(authorization);
        return moduleConfigService.listModules(systemId, appId, keyword, status);
    }

    /**
     * 创建模块草稿。
     */
    @Operation(summary = "创建模块草稿")
    @PostMapping("/apps/{appId}/modules")
    public ModuleVO createModule(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long appId, @Valid @RequestBody ModuleSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.createModule(systemId, appId, saveBO);
    }

    /**
     * 查询模块详情。
     */
    @Operation(summary = "查询模块详情")
    @GetMapping("/modules/{moduleId}")
    public ModuleVO getModule(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return moduleConfigService.getModule(systemId, moduleId);
    }

    /**
     * 更新模块基础信息。
     */
    @Operation(summary = "更新模块基础信息")
    @PutMapping("/modules/{moduleId}")
    public ModuleVO updateModule(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody ModuleUpdateBO updateBO) {
        validateLogin(authorization);
        return moduleConfigService.updateModule(systemId, moduleId, updateBO);
    }

    /**
     * 变更模块状态。
     */
    @Operation(summary = "变更模块状态")
    @PatchMapping("/modules/{moduleId}/status")
    public ModuleVO changeModuleStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody ConfigStatusBO statusBO) {
        validateLogin(authorization);
        return moduleConfigService.changeModuleStatus(systemId, moduleId, statusBO);
    }

    /**
     * 查询模块字段。
     */
    @Operation(summary = "查询模块字段")
    @GetMapping("/modules/{moduleId}/fields")
    public List<FieldVO> listFields(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return moduleConfigService.listFields(systemId, moduleId);
    }

    /**
     * 创建字段草稿。
     */
    @Operation(summary = "创建字段草稿")
    @PostMapping("/modules/{moduleId}/fields")
    public FieldVO createField(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody FieldSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.createField(systemId, moduleId, saveBO);
    }

    /**
     * 更新字段。
     */
    @Operation(summary = "更新字段")
    @PutMapping("/modules/{moduleId}/fields/{fieldId}")
    public FieldVO updateField(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long fieldId,
            @Valid @RequestBody FieldUpdateBO updateBO) {
        validateLogin(authorization);
        return moduleConfigService.updateField(systemId, moduleId, fieldId, updateBO);
    }

    /**
     * 变更字段状态。
     */
    @Operation(summary = "变更字段状态")
    @PatchMapping("/modules/{moduleId}/fields/{fieldId}/status")
    public FieldVO changeFieldStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long fieldId,
            @Valid @RequestBody ConfigStatusBO statusBO) {
        validateLogin(authorization);
        return moduleConfigService.changeFieldStatus(systemId, moduleId, fieldId, statusBO);
    }

    /**
     * 查询可用字段类型。
     */
    @Operation(summary = "查询可用字段类型")
    @GetMapping("/field-types")
    public List<FieldTypeVO> fieldTypes(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return moduleConfigService.fieldTypes(systemId);
    }

    /**
     * 模块发布检查。
     */
    @Operation(summary = "模块发布检查")
    @PostMapping("/modules/{moduleId}/publish-check")
    public PublishCheckResultVO publishCheck(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return moduleConfigService.publishCheck(systemId, moduleId);
    }

    /**
     * 发布模块配置版本。
     */
    @Operation(summary = "发布模块配置版本")
    @PostMapping("/modules/{moduleId}/publish")
    public PublishVersionVO publish(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody PublishRequestBO requestBO) {
        validateLogin(authorization);
        return moduleConfigService.publish(systemId, moduleId, requestBO);
    }

    /**
     * 查询默认列表视图。
     */
    @Operation(summary = "查询默认列表视图")
    @GetMapping("/modules/{moduleId}/ui/list-views")
    public PageSchemaVO listView(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return moduleConfigService.getPageSchema(systemId, moduleId, "LIST");
    }

    /**
     * 保存默认列表视图。
     */
    @Operation(summary = "保存默认列表视图")
    @PutMapping("/modules/{moduleId}/ui/list-views/default")
    public PageSchemaVO saveListView(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody PageSchemaSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.savePageSchema(systemId, moduleId, "LIST", saveBO);
    }

    /**
     * 查询默认表单。
     */
    @Operation(summary = "查询默认表单")
    @GetMapping("/modules/{moduleId}/ui/forms/default")
    public PageSchemaVO form(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return moduleConfigService.getPageSchema(systemId, moduleId, "FORM");
    }

    /**
     * 保存默认表单。
     */
    @Operation(summary = "保存默认表单")
    @PutMapping("/modules/{moduleId}/ui/forms/default")
    public PageSchemaVO saveForm(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody PageSchemaSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.savePageSchema(systemId, moduleId, "FORM", saveBO);
    }

    /**
     * 查询默认详情。
     */
    @Operation(summary = "查询默认详情")
    @GetMapping("/modules/{moduleId}/ui/details/default")
    public PageSchemaVO detail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return moduleConfigService.getPageSchema(systemId, moduleId, "DETAIL");
    }

    /**
     * 保存默认详情。
     */
    @Operation(summary = "保存默认详情")
    @PutMapping("/modules/{moduleId}/ui/details/default")
    public PageSchemaVO saveDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody PageSchemaSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.savePageSchema(systemId, moduleId, "DETAIL", saveBO);
    }

    /**
     * 保存运行菜单配置。
     */
    @Operation(summary = "保存运行菜单配置")
    @PutMapping("/modules/{moduleId}/ui/menu")
    public MenuConfigVO saveMenu(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody MenuConfigSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.saveMenu(systemId, moduleId, saveBO);
    }

    /**
     * 保存模块动作配置。
     */
    @Operation(summary = "保存模块动作配置")
    @PutMapping("/modules/{moduleId}/ui/actions")
    public List<ActionConfigVO> saveActions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody ActionConfigSaveBO saveBO) {
        validateLogin(authorization);
        return moduleConfigService.saveActions(systemId, moduleId, saveBO);
    }

    private void validateLogin(String authorization) {
        authSessionService.me(resolveBearer(authorization));
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
