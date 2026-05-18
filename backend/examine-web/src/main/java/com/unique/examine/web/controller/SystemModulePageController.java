package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModulePage;
import com.unique.examine.module.entity.po.ModulePageBlock;
import com.unique.examine.module.manage.SystemModulePageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "自建系统态-module页面设计")
@RestController
@RequestMapping("/v1/system/module/pages")
public class SystemModulePageController {

    @Autowired
    private SystemModulePageService systemModulePageService;

    @Operation(summary = "页面列表（按 appId）")
    @GetMapping("/apps/{appId}")
    public ApiResult<List<ModulePage>> listPages(@PathVariable Long appId) {
        return ApiResult.ok(systemModulePageService.listPages(appId, AuthContextHolder.getPlatId()));
    }

    @Operation(summary = "页面选择器")
    @GetMapping("/apps/{appId}/picker")
    public ApiResult<List<Map<String, Object>>> pagePicker(@PathVariable Long appId) {
        return ApiResult.ok(systemModulePageService.listPagePickerOptions(appId, AuthContextHolder.getPlatId()));
    }

    @Operation(summary = "页面详情（含区块）")
    @GetMapping("/{pageId}/detail")
    public ApiResult<Map<String, Object>> pageDetail(@PathVariable Long pageId) {
        return ApiResult.ok(systemModulePageService.getPageDetail(pageId, AuthContextHolder.getPlatId()));
    }

    @Operation(summary = "页面运行时配置（list/form 渲染用）")
    @GetMapping("/{pageId}/runtime")
    public ApiResult<Map<String, Object>> pageRuntime(@PathVariable Long pageId) {
        return ApiResult.ok(systemModulePageService.getPageRuntime(pageId, AuthContextHolder.getPlatId()));
    }

    public record UpsertPageBody(
            Long id,
            Long appId,
            String pageCode,
            String pageName,
            String pageType,
            String routePath,
            String configJson,
            String formFieldsJson,
            Integer status
    ) {}

    @Operation(summary = "新增/更新页面")
    @PostMapping("/upsert")
    public ApiResult<ModulePage> upsertPage(@RequestBody UpsertPageBody body) {
        return ApiResult.ok(systemModulePageService.upsertPage(
                AuthContextHolder.getPlatId(),
                new SystemModulePageService.UpsertPageCmd(
                        body.id(),
                        body.appId(),
                        body.pageCode(),
                        body.pageName(),
                        body.pageType(),
                        body.routePath(),
                        body.configJson(),
                        body.formFieldsJson(),
                        body.status())));
    }

    public record DeleteIdsBody(List<Long> ids) {}

    @Operation(summary = "删除页面（级联区块）")
    @PostMapping("/delete")
    public ApiResult<Void> deletePages(@RequestBody DeleteIdsBody body) {
        systemModulePageService.deletePages(AuthContextHolder.getPlatId(), body == null ? null : body.ids());
        return ApiResult.ok();
    }

    public record UpsertBlockBody(Long id, Long appId, Long pageId, String blockType, Integer sortNo, String configJson) {}

    @Operation(summary = "新增/更新页面区块")
    @PostMapping("/blocks/upsert")
    public ApiResult<ModulePageBlock> upsertBlock(@RequestBody UpsertBlockBody body) {
        return ApiResult.ok(systemModulePageService.upsertBlock(
                AuthContextHolder.getPlatId(),
                new SystemModulePageService.UpsertBlockCmd(
                        body.id(),
                        body.appId(),
                        body.pageId(),
                        body.blockType(),
                        body.sortNo(),
                        body.configJson())));
    }

    @Operation(summary = "删除页面区块")
    @PostMapping("/blocks/delete")
    public ApiResult<Void> deleteBlocks(@RequestBody DeleteIdsBody body) {
        systemModulePageService.deleteBlocks(AuthContextHolder.getPlatId(), body == null ? null : body.ids());
        return ApiResult.ok();
    }
}
