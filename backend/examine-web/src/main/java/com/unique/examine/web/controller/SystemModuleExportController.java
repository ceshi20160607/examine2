package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleExportTpl;
import com.unique.examine.module.entity.po.ModuleExportTplField;
import com.unique.examine.web.service.SystemModuleExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Tag(name = "自建系统态-module导出模板")
@RestController
@RequestMapping("/v1/system/module/exports")
public class SystemModuleExportController {

    @Autowired
    private SystemModuleExportService systemModuleExportService;

    @Operation(summary = "导出模板列表（按 modelId）")
    @GetMapping("/models/{modelId}/tpls")
    public ApiResult<List<ModuleExportTpl>> listTpls(@PathVariable("modelId") Long modelId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleExportService.listTpls(modelId, platId));
    }

    public record UpsertTplBody(Long id,
                                Long appId,
                                Long modelId,
                                Long menuId,
                                String tplCode,
                                String tplName,
                                String fileType,
                                Integer status) {}

    @Operation(summary = "新增/更新导出模板")
    @PostMapping("/tpls/upsert")
    public ApiResult<ModuleExportTpl> upsertTpl(@RequestBody UpsertTplBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleExportService.upsertTpl(platId, body));
    }

    @Operation(summary = "导出字段列表（按 tplId）")
    @GetMapping("/tpls/{tplId}/fields")
    public ApiResult<List<ModuleExportTplField>> listFields(@PathVariable("tplId") Long tplId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleExportService.listFields(tplId, platId));
    }

    public record UpsertFieldBody(Long id,
                                  Long tplId,
                                  Long fieldId,
                                  String colTitle,
                                  Integer sortNo,
                                  String formatJson) {}

    @Operation(summary = "新增/更新导出字段")
    @PostMapping("/fields/upsert")
    public ApiResult<ModuleExportTplField> upsertField(@RequestBody UpsertFieldBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleExportService.upsertField(platId, body));
    }

    public record DeleteIdsBody(List<Long> ids) {}

    @Operation(summary = "删除导出模板（按 id 列表；同时级联删除字段）")
    @PostMapping("/tpls/delete")
    public ApiResult<Void> deleteTpls(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleExportService.deleteTpls(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "删除导出字段（按 id 列表）")
    @PostMapping("/fields/delete")
    public ApiResult<Void> deleteFields(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleExportService.deleteFields(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "按导出模板导出 CSV（最小闭环；支持传入 DSL filters）")
    @PostMapping("/tpls/{tplId}/export/csv")
    public void exportCsv(@PathVariable("tplId") Long tplId,
                          @RequestBody(required = false) ModuleRecordDslQuery query,
                          HttpServletResponse response) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleExportService.exportCsv(tplId, platId, query, response);
    }
}

