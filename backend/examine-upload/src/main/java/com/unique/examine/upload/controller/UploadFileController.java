package com.unique.examine.upload.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.upload.entity.PO.UploadFile;
import com.unique.examine.upload.service.IUploadFileService;
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
 * 上传文件主表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@RestController
@RequestMapping("/uploadFile")
public class UploadFileController {

    @Autowired
    private IUploadFileService uploadFileService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<UploadFile> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        UploadFile entity = uploadFileService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody UploadFile entity) {
        uploadFileService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody UploadFile entity) {
        uploadFileService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<UploadFile>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(uploadFileService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        uploadFileService.deleteByIds(ids);
        return Result.ok();
    }
}
