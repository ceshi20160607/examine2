package com.unique.examine.module.manage.config;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.module.manage.config.ModuleConfigModels.ActionConfigVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ActionSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictItemSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictItemVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictTypeQueryRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictTypeSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictTypeVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.DynamicListSchema;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldDefinitionVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldQueryRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImportExportConfigSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImportExportConfigVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleGroupSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleGroupVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleQueryRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PermissionBindingVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PrintTemplateSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.PrintTemplateVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishCheckResultVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishResult;
import com.unique.examine.module.manage.config.ModuleConfigModels.SceneSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.SceneSchemaVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module configuration API controller.
 */
@RestController
public class ModuleConfigController {

    private final ModuleConfigService moduleConfigService;

    public ModuleConfigController(ModuleConfigService moduleConfigService) {
        this.moduleConfigService = moduleConfigService;
    }

    @GetMapping("/api/v1/systems/{systemId}/module-groups")
    public ApiResponse<List<ModuleGroupVO>> moduleGroups(@PathVariable String systemId) {
        return ApiResponse.success(moduleConfigService.moduleGroups(systemId));
    }

    @PostMapping("/api/v1/systems/{systemId}/module-groups")
    public ApiResponse<ModuleGroupVO> createModuleGroup(@PathVariable String systemId,
                                                        @RequestBody ModuleGroupSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveModuleGroup(systemId, null, request));
    }

    @PatchMapping("/api/v1/systems/{systemId}/module-groups/{groupId}")
    public ApiResponse<ModuleGroupVO> updateModuleGroup(@PathVariable String systemId,
                                                        @PathVariable String groupId,
                                                        @RequestBody ModuleGroupSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveModuleGroup(systemId, groupId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/module-groups/{groupId}/publish")
    public ApiResponse<PublishResult> publishModuleGroup(@PathVariable String systemId,
                                                         @PathVariable String groupId,
                                                         @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(moduleConfigService.publishTarget(systemId, groupId, "MODULE_GROUP", request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules")
    public ApiResponse<PageResult<ModuleVO>> modules(@PathVariable String systemId,
                                                     @RequestParam(defaultValue = "1") int pageNo,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     ModuleQueryRequest query) {
        return ApiResponse.success(moduleConfigService.modules(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules")
    public ApiResponse<ModuleVO> createModule(@PathVariable String systemId,
                                              @RequestBody ModuleSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveModule(systemId, null, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}")
    public ApiResponse<ModuleVO> moduleDetail(@PathVariable String systemId, @PathVariable String moduleId) {
        return ApiResponse.success(moduleConfigService.moduleDetail(systemId, moduleId));
    }

    @PatchMapping("/api/v1/systems/{systemId}/modules/{moduleId}")
    public ApiResponse<ModuleVO> updateModule(@PathVariable String systemId,
                                              @PathVariable String moduleId,
                                              @RequestBody ModuleSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveModule(systemId, moduleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/fields")
    public ApiResponse<PageResult<FieldDefinitionVO>> fields(@PathVariable String systemId,
                                                             @PathVariable String moduleId,
                                                             @RequestParam(defaultValue = "1") int pageNo,
                                                             @RequestParam(defaultValue = "50") int pageSize,
                                                             FieldQueryRequest query) {
        return ApiResponse.success(moduleConfigService.fields(systemId, moduleId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/fields")
    public ApiResponse<FieldDefinitionVO> createField(@PathVariable String systemId,
                                                      @PathVariable String moduleId,
                                                      @RequestBody FieldSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveField(systemId, moduleId, null, request));
    }

    @PatchMapping("/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}")
    public ApiResponse<FieldDefinitionVO> updateField(@PathVariable String systemId,
                                                      @PathVariable String moduleId,
                                                      @PathVariable String fieldId,
                                                      @RequestBody FieldSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveField(systemId, moduleId, fieldId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/dict-types")
    public ApiResponse<PageResult<DictTypeVO>> dictTypes(@PathVariable String systemId,
                                                         @RequestParam(defaultValue = "1") int pageNo,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         DictTypeQueryRequest query) {
        return ApiResponse.success(moduleConfigService.dictTypes(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/dict-types")
    public ApiResponse<DictTypeVO> createDictType(@PathVariable String systemId,
                                                  @RequestBody DictTypeSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveDictType(systemId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/dict-types/{dictTypeId}/items")
    public ApiResponse<List<DictItemVO>> dictItems(@PathVariable String systemId,
                                                   @PathVariable String dictTypeId) {
        return ApiResponse.success(moduleConfigService.dictItems(systemId, dictTypeId));
    }

    @PostMapping("/api/v1/systems/{systemId}/dict-types/{dictTypeId}/items")
    public ApiResponse<DictItemVO> createDictItem(@PathVariable String systemId,
                                                  @PathVariable String dictTypeId,
                                                  @RequestBody DictItemSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveDictItem(systemId, dictTypeId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/scenes")
    public ApiResponse<List<SceneSchemaVO>> scenes(@PathVariable String systemId, @PathVariable String moduleId) {
        return ApiResponse.success(moduleConfigService.scenes(systemId, moduleId));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/scenes")
    public ApiResponse<SceneSchemaVO> saveScene(@PathVariable String systemId,
                                                @PathVariable String moduleId,
                                                @RequestBody SceneSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveScene(systemId, moduleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/list-schema")
    public ApiResponse<DynamicListSchema> listSchema(@PathVariable String systemId,
                                                     @PathVariable String moduleId,
                                                     @RequestParam(required = false) String sceneId) {
        return ApiResponse.success(moduleConfigService.listSchema(systemId, moduleId, sceneId));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/actions")
    public ApiResponse<List<ActionConfigVO>> actions(@PathVariable String systemId, @PathVariable String moduleId) {
        return ApiResponse.success(moduleConfigService.actions(systemId, moduleId));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/actions")
    public ApiResponse<ActionConfigVO> saveAction(@PathVariable String systemId,
                                                  @PathVariable String moduleId,
                                                  @RequestBody ActionSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveAction(systemId, moduleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/permissions")
    public ApiResponse<List<PermissionBindingVO>> permissions(@PathVariable String systemId,
                                                              @PathVariable String moduleId) {
        return ApiResponse.success(moduleConfigService.permissions(systemId, moduleId));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/import-export-config")
    public ApiResponse<ImportExportConfigVO> importExportConfig(@PathVariable String systemId,
                                                                @PathVariable String moduleId) {
        return ApiResponse.success(moduleConfigService.importExportConfig(systemId, moduleId));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/import-export-config")
    public ApiResponse<ImportExportConfigVO> saveImportExportConfig(@PathVariable String systemId,
                                                                    @PathVariable String moduleId,
                                                                    @RequestBody ImportExportConfigSaveRequest request) {
        return ApiResponse.success(moduleConfigService.saveImportExportConfig(systemId, moduleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/print-templates")
    public ApiResponse<List<PrintTemplateVO>> printTemplates(@PathVariable String systemId,
                                                             @PathVariable String moduleId) {
        return ApiResponse.success(moduleConfigService.printTemplates(systemId, moduleId));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/print-templates")
    public ApiResponse<PrintTemplateVO> savePrintTemplate(@PathVariable String systemId,
                                                          @PathVariable String moduleId,
                                                          @RequestBody PrintTemplateSaveRequest request) {
        return ApiResponse.success(moduleConfigService.savePrintTemplate(systemId, moduleId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/publish-check")
    public ApiResponse<PublishCheckResultVO> publishCheck(@PathVariable String systemId,
                                                          @PathVariable String moduleId,
                                                          @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(moduleConfigService.publishCheck(systemId, moduleId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/publish")
    public ApiResponse<PublishResult> publishModule(@PathVariable String systemId,
                                                    @PathVariable String moduleId,
                                                    @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(moduleConfigService.publishTarget(systemId, moduleId, "MODULE", request));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/rollback")
    public ApiResponse<PublishResult> rollbackModule(@PathVariable String systemId,
                                                     @PathVariable String moduleId,
                                                     @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(moduleConfigService.rollbackModule(systemId, moduleId, request));
    }
}
