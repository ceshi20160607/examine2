package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleListFilterTpl;
import com.unique.examine.module.entity.po.ModuleListView;
import com.unique.examine.module.entity.po.ModuleListViewCol;
import com.unique.examine.module.manage.SystemModuleListViewService;
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

@Tag(name = "自建系统态-module列表视图")
@RestController
@RequestMapping("/v1/system/module/list-views")
public class SystemModuleListViewController {

    @Autowired
    private SystemModuleListViewService systemModuleListViewService;

    @Operation(summary = "视图列表（按 modelId；可选 platId=当前用户，仅返回个人视图）")
    @GetMapping("/models/{modelId}")
    public ApiResult<List<ModuleListView>> listViews(@PathVariable("modelId") Long modelId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleListViewService.listViews(modelId, platId));
    }

    public record UpsertViewBody(Long id,
                                 Long appId,
                                 Long modelId,
                                 Long platId,
                                 String viewCode,
                                 String viewName,
                                 Integer defaultFlag,
                                 Integer status) {}

    @Operation(summary = "新增/更新视图")
    @PostMapping("/upsert")
    public ApiResult<ModuleListView> upsertView(@RequestBody UpsertViewBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleListViewService.upsertView(platId, new SystemModuleListViewService.UpsertViewCmd(
                body.id(), body.appId(), body.modelId(), body.platId(), body.viewCode(), body.viewName(), body.defaultFlag(), body.status()
        )));
    }

    @Operation(summary = "列配置列表（按 viewId）")
    @GetMapping("/{viewId}/cols")
    public ApiResult<List<ModuleListViewCol>> listCols(@PathVariable("viewId") Long viewId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleListViewService.listCols(viewId, platId));
    }

    public record UpsertColBody(Long id,
                                Long viewId,
                                Long fieldId,
                                String colTitle,
                                Integer width,
                                Integer sortNo,
                                Integer visibleFlag,
                                String fixedType,
                                String formatJson) {}

    @Operation(summary = "新增/更新列配置")
    @PostMapping("/cols/upsert")
    public ApiResult<ModuleListViewCol> upsertCol(@RequestBody UpsertColBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleListViewService.upsertCol(platId, new SystemModuleListViewService.UpsertColCmd(
                body.id(), body.viewId(), body.fieldId(), body.colTitle(), body.width(), body.sortNo(), body.visibleFlag(), body.fixedType(), body.formatJson()
        )));
    }

    public record DeleteIdsBody(List<Long> ids) {}

    @Operation(summary = "删除视图（按 id 列表）")
    @PostMapping("/delete")
    public ApiResult<Void> deleteViews(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleListViewService.deleteViews(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "删除列配置（按 id 列表）")
    @PostMapping("/cols/delete")
    public ApiResult<Void> deleteCols(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleListViewService.deleteCols(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "筛选模板列表（按 modelId；可选 menuId）")
    @GetMapping("/models/{modelId}/filter-tpls")
    public ApiResult<List<ModuleListFilterTpl>> listFilterTpls(@PathVariable("modelId") Long modelId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleListViewService.listFilterTpls(modelId, platId));
    }

    public record UpsertFilterTplBody(Long id,
                                      Long appId,
                                      Long modelId,
                                      Long menuId,
                                      String tplCode,
                                      String tplName,
                                      Integer status) {}

    @Operation(summary = "新增/更新筛选模板")
    @PostMapping("/filter-tpls/upsert")
    public ApiResult<ModuleListFilterTpl> upsertFilterTpl(@RequestBody UpsertFilterTplBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleListViewService.upsertFilterTpl(platId, new SystemModuleListViewService.UpsertFilterTplCmd(
                body.id(), body.appId(), body.modelId(), body.menuId(), body.tplCode(), body.tplName(), body.status()
        )));
    }

    @Operation(summary = "删除筛选模板（按 id 列表）")
    @PostMapping("/filter-tpls/delete")
    public ApiResult<Void> deleteFilterTpls(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleListViewService.deleteFilterTpls(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }
}

