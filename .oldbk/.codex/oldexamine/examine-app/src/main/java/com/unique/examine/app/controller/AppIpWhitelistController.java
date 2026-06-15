package com.unique.examine.app.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.app.entity.po.AppIpWhitelist;
import com.unique.examine.app.service.IAppIpWhitelistService;
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
 * client IP 白名单 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/appIpWhitelist")
public class AppIpWhitelistController {

    @Autowired
    private IAppIpWhitelistService appIpWhitelistService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<AppIpWhitelist> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        AppIpWhitelist entity = appIpWhitelistService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody AppIpWhitelist entity) {
        appIpWhitelistService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody AppIpWhitelist entity) {
        appIpWhitelistService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<AppIpWhitelist>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(appIpWhitelistService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        appIpWhitelistService.deleteByIds(ids);
        return Result.ok();
    }
}
