package com.unique.examine.plat.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.plat.entity.po.PlatAccount;
import com.unique.examine.plat.service.IPlatAccountService;
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
 * 平台账号 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/platAccount")
public class PlatAccountController {

    @Autowired
    private IPlatAccountService platAccountService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<PlatAccount> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        PlatAccount entity = platAccountService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody PlatAccount entity) {
        platAccountService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody PlatAccount entity) {
        platAccountService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<PlatAccount>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(platAccountService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        platAccountService.deleteByIds(ids);
        return Result.ok();
    }
}
