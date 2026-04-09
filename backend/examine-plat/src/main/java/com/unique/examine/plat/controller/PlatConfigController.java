package com.unique.examine.plat.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.plat.entity.PO.PlatConfig;
import com.unique.examine.plat.service.IPlatConfigService;
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
 * 平台基础配置 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@RestController
@RequestMapping("/platConfig")
public class PlatConfigController {

    @Autowired
    private IPlatConfigService platConfigService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<PlatConfig> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        PlatConfig entity = platConfigService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody PlatConfig entity) {
        platConfigService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody PlatConfig entity) {
        platConfigService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<PlatConfig>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(platConfigService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        platConfigService.deleteByIds(ids);
        return Result.ok();
    }
}
