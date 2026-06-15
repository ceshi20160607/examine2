package com.unique.examine.module.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.module.entity.po.ModuleFieldOption;
import com.unique.examine.module.service.IModuleFieldOptionService;
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
 * 字段下拉选项（不走字典时使用） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/moduleFieldOption")
public class ModuleFieldOptionController {

    @Autowired
    private IModuleFieldOptionService moduleFieldOptionService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<ModuleFieldOption> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        ModuleFieldOption entity = moduleFieldOptionService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody ModuleFieldOption entity) {
        moduleFieldOptionService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody ModuleFieldOption entity) {
        moduleFieldOptionService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<ModuleFieldOption>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(moduleFieldOptionService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        moduleFieldOptionService.deleteByIds(ids);
        return Result.ok();
    }
}
