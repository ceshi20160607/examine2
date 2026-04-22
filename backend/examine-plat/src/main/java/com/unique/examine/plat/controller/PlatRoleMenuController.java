package com.unique.examine.plat.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.plat.entity.po.PlatRoleMenu;
import com.unique.examine.plat.service.IPlatRoleMenuService;
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
 * 平台角色与菜单（权限项）关联 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/platRoleMenu")
public class PlatRoleMenuController {

    @Autowired
    private IPlatRoleMenuService platRoleMenuService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<PlatRoleMenu> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        PlatRoleMenu entity = platRoleMenuService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody PlatRoleMenu entity) {
        platRoleMenuService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody PlatRoleMenu entity) {
        platRoleMenuService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<PlatRoleMenu>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(platRoleMenuService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        platRoleMenuService.deleteByIds(ids);
        return Result.ok();
    }
}
