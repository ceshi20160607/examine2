package com.unique.examine.app.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.app.entity.PO.AppClient;
import com.unique.examine.app.service.IAppClientService;
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
 * 对外 client 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@RestController
@RequestMapping("/appClient")
public class AppClientController {

    @Autowired
    private IAppClientService appClientService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<AppClient> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        AppClient entity = appClientService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody AppClient entity) {
        appClientService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody AppClient entity) {
        appClientService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<AppClient>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(appClientService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        appClientService.deleteByIds(ids);
        return Result.ok();
    }
}
