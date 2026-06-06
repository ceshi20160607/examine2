package com.unique.examine.module.manage.controller;

import com.unique.examine.core.common.ApiResult;
import com.unique.examine.module.manage.bo.ModuleExportJobBO;
import com.unique.examine.module.manage.bo.ModuleFieldSaveBO;
import com.unique.examine.module.manage.bo.ModuleModelSaveBO;
import com.unique.examine.module.manage.bo.ModulePageSaveBO;
import com.unique.examine.module.manage.bo.ModuleRecordSaveBO;
import com.unique.examine.module.manage.bo.ModuleStatusBO;
import com.unique.examine.module.manage.service.ModuleManageService;
import com.unique.examine.module.manage.vo.ModuleManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 动态模块管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/modules")
@Tag(name = "动态模块")
public class ModuleManageController {

    private final ModuleManageService moduleManageService;

    /**
     * 查询应用下模块。
     *
     * @param appId 应用 ID
     * @return 模块列表
     */
    @GetMapping("/models")
    @Operation(summary = "查询模块模型")
    public ApiResult<List<ModuleManageVO>> listModels(@RequestParam(required = false) Long appId) {
        return ApiResult.success(moduleManageService.listModels(appId));
    }

    /**
     * 创建模块。
     *
     * @param bo 模块保存入参
     * @return 模块信息
     */
    @PostMapping("/models")
    @Operation(summary = "创建模块模型")
    public ApiResult<ModuleManageVO> createModel(@RequestBody ModuleModelSaveBO bo) {
        return ApiResult.success(moduleManageService.createModel(bo));
    }

    /**
     * 变更模块状态。
     *
     * @param id 模块 ID
     * @param bo 状态入参
     * @return 模块信息
     */
    @PatchMapping("/models/status")
    @Operation(summary = "变更模块状态")
    public ApiResult<ModuleManageVO> updateModelStatus(@RequestParam Long id, @RequestBody ModuleStatusBO bo) {
        return ApiResult.success(moduleManageService.updateModelStatus(id, bo));
    }

    /**
     * 查询字段。
     *
     * @param moduleId 模块 ID
     * @return 字段列表
     */
    @GetMapping("/fields")
    @Operation(summary = "查询模块字段")
    public ApiResult<List<ModuleManageVO>> listFields(@RequestParam Long moduleId) {
        return ApiResult.success(moduleManageService.listFields(moduleId));
    }

    /**
     * 创建字段。
     *
     * @param bo 字段保存入参
     * @return 字段信息
     */
    @PostMapping("/fields")
    @Operation(summary = "创建模块字段")
    public ApiResult<ModuleManageVO> createField(@RequestBody ModuleFieldSaveBO bo) {
        return ApiResult.success(moduleManageService.createField(bo));
    }

    /**
     * 查询页面。
     *
     * @param moduleId 模块 ID
     * @return 页面列表
     */
    @GetMapping("/pages")
    @Operation(summary = "查询模块页面")
    public ApiResult<List<ModuleManageVO>> listPages(@RequestParam Long moduleId) {
        return ApiResult.success(moduleManageService.listPages(moduleId));
    }

    /**
     * 创建页面。
     *
     * @param bo 页面保存入参
     * @return 页面信息
     */
    @PostMapping("/pages")
    @Operation(summary = "创建模块页面")
    public ApiResult<ModuleManageVO> createPage(@RequestBody ModulePageSaveBO bo) {
        return ApiResult.success(moduleManageService.createPage(bo));
    }

    /**
     * 查询记录。
     *
     * @param moduleId 模块 ID
     * @return 记录列表
     */
    @GetMapping("/records")
    @Operation(summary = "查询模块记录")
    public ApiResult<List<ModuleManageVO>> listRecords(@RequestParam Long moduleId) {
        return ApiResult.success(moduleManageService.listRecords(moduleId));
    }

    /**
     * 查询记录详情。
     *
     * @param id 记录 ID
     * @return 记录详情
     */
    @GetMapping("/records/detail")
    @Operation(summary = "查询模块记录详情")
    public ApiResult<ModuleManageVO> getRecord(@RequestParam Long id) {
        return ApiResult.success(moduleManageService.getRecord(id));
    }

    /**
     * 创建记录。
     *
     * @param bo 记录保存入参
     * @return 记录详情
     */
    @PostMapping("/records")
    @Operation(summary = "创建模块记录")
    public ApiResult<ModuleManageVO> createRecord(@RequestBody ModuleRecordSaveBO bo) {
        return ApiResult.success(moduleManageService.createRecord(bo));
    }

    /**
     * 更新记录。
     *
     * @param id 记录 ID
     * @param bo 记录保存入参
     * @return 记录详情
     */
    @PutMapping("/records")
    @Operation(summary = "更新模块记录")
    public ApiResult<ModuleManageVO> updateRecord(@RequestParam Long id, @RequestBody ModuleRecordSaveBO bo) {
        return ApiResult.success(moduleManageService.updateRecord(id, bo));
    }

    /**
     * 删除记录。
     *
     * @param id 记录 ID
     * @return 删除前记录信息
     */
    @DeleteMapping("/records")
    @Operation(summary = "删除模块记录")
    public ApiResult<ModuleManageVO> deleteRecord(@RequestParam Long id) {
        return ApiResult.success(moduleManageService.deleteRecord(id));
    }

    /**
     * 创建导出任务。
     *
     * @param bo 导出任务入参
     * @return 导出任务信息
     */
    @PostMapping("/export-jobs")
    @Operation(summary = "创建模块导出任务")
    public ApiResult<ModuleManageVO> createExportJob(@RequestBody ModuleExportJobBO bo) {
        return ApiResult.success(moduleManageService.createExportJob(bo));
    }
}
