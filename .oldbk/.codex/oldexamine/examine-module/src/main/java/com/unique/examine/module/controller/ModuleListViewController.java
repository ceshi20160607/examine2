package com.unique.examine.module.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.module.entity.po.ModuleListView;
import com.unique.examine.module.service.IModuleListViewService;
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
 * 列表视图/表头方案（支持用户个性化） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/moduleListView")
public class ModuleListViewController {

    @Autowired
    private IModuleListViewService moduleListViewService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<ModuleListView> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        ModuleListView entity = moduleListViewService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody ModuleListView entity) {
        moduleListViewService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody ModuleListView entity) {
        moduleListViewService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<ModuleListView>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(moduleListViewService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        moduleListViewService.deleteByIds(ids);
        return Result.ok();
    }
}
