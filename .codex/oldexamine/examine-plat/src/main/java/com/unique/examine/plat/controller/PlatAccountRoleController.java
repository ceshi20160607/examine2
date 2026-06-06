package com.unique.examine.plat.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.plat.entity.po.PlatAccountRole;
import com.unique.examine.plat.service.IPlatAccountRoleService;
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
 * 平台账号与角色关联 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/platAccountRole")
public class PlatAccountRoleController {

    @Autowired
    private IPlatAccountRoleService platAccountRoleService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<PlatAccountRole> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        PlatAccountRole entity = platAccountRoleService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody PlatAccountRole entity) {
        platAccountRoleService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody PlatAccountRole entity) {
        platAccountRoleService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<PlatAccountRole>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(platAccountRoleService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        platAccountRoleService.deleteByIds(ids);
        return Result.ok();
    }
}
