package com.unique.examine.app.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.app.entity.PO.AppClientScope;
import com.unique.examine.app.service.IAppClientScopeService;
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
 * client 授权范围 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@RestController
@RequestMapping("/appClientScope")
public class AppClientScopeController {

    @Autowired
    private IAppClientScopeService appClientScopeService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<AppClientScope> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        AppClientScope entity = appClientScopeService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody AppClientScope entity) {
        appClientScopeService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody AppClientScope entity) {
        appClientScopeService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<AppClientScope>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(appClientScopeService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        appClientScopeService.deleteByIds(ids);
        return Result.ok();
    }
}
