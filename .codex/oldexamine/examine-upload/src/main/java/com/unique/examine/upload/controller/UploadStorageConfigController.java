package com.unique.examine.upload.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.upload.entity.po.UploadStorageConfig;
import com.unique.examine.upload.service.IUploadStorageConfigService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 上传存储配置（不含密钥） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/uploadStorageConfig")
public class UploadStorageConfigController {

    @Autowired
    private IUploadStorageConfigService uploadStorageConfigService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<UploadStorageConfig> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        UploadStorageConfig entity = uploadStorageConfigService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody UploadStorageConfig entity) {
        uploadStorageConfigService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody UploadStorageConfig entity) {
        uploadStorageConfigService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<UploadStorageConfig>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(uploadStorageConfigService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        uploadStorageConfigService.deleteByIds(ids);
        return Result.ok();
    }
}
