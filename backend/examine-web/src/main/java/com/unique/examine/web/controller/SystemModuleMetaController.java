package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleAction;
import com.unique.examine.module.entity.po.ModuleApp;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleModel;
import com.unique.examine.module.entity.po.ModuleRelation;
import com.unique.examine.module.manage.SystemModuleMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "自建系统态-module元数据")
@RestController
@RequestMapping("/v1/system/module/meta")
public class SystemModuleMetaController {

    @Autowired
    private SystemModuleMetaService systemModuleMetaService;

    @Operation(summary = "应用列表")
    @GetMapping("/apps")
    public ApiResult<List<ModuleApp>> listApps() {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.listApps(platId));
    }

    public record UpsertAppBody(Long id, String appCode, String appName, String iconUrl, Integer status, Integer publishedFlag, String remark) {}

    @Operation(summary = "新增/更新应用")
    @PostMapping("/apps/upsert")
    public ApiResult<ModuleApp> upsertApp(@RequestBody UpsertAppBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.upsertApp(platId, new SystemModuleMetaService.UpsertAppCmd(
                body.id(), body.appCode(), body.appName(), body.iconUrl(), body.status(), body.publishedFlag(), body.remark()
        )));
    }

    public record DeleteIdsBody(List<Long> ids) {}

    @Operation(summary = "删除应用（按 id 列表）")
    @PostMapping("/apps/delete")
    public ApiResult<Void> deleteApps(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleMetaService.deleteApps(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "模型列表（按 appId）")
    @GetMapping("/apps/{appId}/models")
    public ApiResult<List<ModuleModel>> listModels(@PathVariable("appId") Long appId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.listModels(appId, platId));
    }

    public record UpsertModelBody(Long id, Long appId, String modelCode, String modelName, Integer status, String remark) {}

    @Operation(summary = "新增/更新模型")
    @PostMapping("/models/upsert")
    public ApiResult<ModuleModel> upsertModel(@RequestBody UpsertModelBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.upsertModel(platId, new SystemModuleMetaService.UpsertModelCmd(
                body.id(), body.appId(), body.modelCode(), body.modelName(), body.status(), body.remark()
        )));
    }

    @Operation(summary = "删除模型（按 id 列表）")
    @PostMapping("/models/delete")
    public ApiResult<Void> deleteModels(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleMetaService.deleteModels(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "字段列表（按 modelId）")
    @GetMapping("/models/{modelId}/fields")
    public ApiResult<List<ModuleField>> listFields(@PathVariable("modelId") Long modelId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.listFields(modelId, platId));
    }

    public record UpsertFieldBody(Long id,
                                  Long appId,
                                  Long modelId,
                                  String fieldCode,
                                  String fieldName,
                                  String fieldType,
                                  Integer requiredFlag,
                                  Integer uniqueFlag,
                                  Integer hiddenFlag,
                                  String tips,
                                  Integer maxLength,
                                  Integer minLength,
                                  String validateType,
                                  String dateFormat,
                                  String dictCode,
                                  Integer multiFlag,
                                  String defaultValue,
                                  Integer sortNo,
                                  Integer status) {}

    @Operation(summary = "新增/更新字段")
    @PostMapping("/fields/upsert")
    public ApiResult<ModuleField> upsertField(@RequestBody UpsertFieldBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.upsertField(platId, new SystemModuleMetaService.UpsertFieldCmd(
                body.id(), body.appId(), body.modelId(), body.fieldCode(), body.fieldName(), body.fieldType(),
                body.requiredFlag(), body.uniqueFlag(), body.hiddenFlag(), body.tips(),
                body.maxLength(), body.minLength(), body.validateType(), body.dateFormat(), body.dictCode(),
                body.multiFlag(), body.defaultValue(), body.sortNo(), body.status()
        )));
    }

    @Operation(summary = "删除字段（按 id 列表）")
    @PostMapping("/fields/delete")
    public ApiResult<Void> deleteFields(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleMetaService.deleteFields(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "关系列表（按 appId）")
    @GetMapping("/apps/{appId}/relations")
    public ApiResult<List<ModuleRelation>> listRelations(@PathVariable("appId") Long appId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.listRelations(appId, platId));
    }

    public record UpsertRelationBody(Long id, Long appId, Long srcModelId, Long dstModelId, String relType, String configJson) {}

    @Operation(summary = "新增/更新关系")
    @PostMapping("/relations/upsert")
    public ApiResult<ModuleRelation> upsertRelation(@RequestBody UpsertRelationBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.upsertRelation(platId, new SystemModuleMetaService.UpsertRelationCmd(
                body.id(), body.appId(), body.srcModelId(), body.dstModelId(), body.relType(), body.configJson()
        )));
    }

    @Operation(summary = "删除关系（按 id 列表）")
    @PostMapping("/relations/delete")
    public ApiResult<Void> deleteRelations(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleMetaService.deleteRelations(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "动作字典列表（系统内置）")
    @GetMapping("/actions")
    public ApiResult<List<ModuleAction>> listActions() {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleMetaService.listActions(platId));
    }
}

