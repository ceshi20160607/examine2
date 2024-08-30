package com.unique.module.controller;


import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.module.entity.po.ModuleRoleUser;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.unique.module.service.IModuleUserService;
import com.unique.module.entity.po.ModuleUser;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
@RestController
@RequestMapping("/moduleUser")
@Api(tags = "用户表")
public class ModuleUserController {

    @Autowired
    private IModuleUserService moduleUserService;


    /**
    * 查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @PostMapping("/queryPageList")
    @ApiOperation("查询列表页数据")
    public Result<BasePage<ModuleUser>> queryPageList(@RequestBody SearchBO search) {
        search.setPageType(1);
        BasePage<ModuleUser> mapBasePage = moduleUserService.queryPageList(search);
        return Result.ok(mapBasePage);
    }
    /**
    * 保存数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/add")
    @ApiOperation("保存数据")
    public Result add(@RequestBody ModuleUser baseModel) {
        moduleUserService.addOrUpdate(baseModel, false);
        return Result.ok();
    }
    /**
    * 更新数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/update")
    @ApiOperation("修改数据")
    public Result update(@RequestBody ModuleUser baseModel) {
        moduleUserService.addOrUpdate(baseModel, false);
        return Result.ok();
    }
    /**
    * 查询数据
    * @param id 业务对象id
    * @return data
    */
    @PostMapping("/queryById/{id}")
    @ApiOperation("根据ID查询")
    public Result<ModuleUser > queryById(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {
        ModuleUser  model = moduleUserService.queryById(id);
        return Result.ok(model);
    }

    /**
    * 删除数据
    * @param ids 业务对象ids
    * @return data
    */
    @PostMapping("/deleteByIds")
    @ApiOperation("根据ID删除数据")
    public Result deleteByIds(@ApiParam(name = "ids", value = "id列表") @RequestBody List<Long> ids) {
        moduleUserService.deleteByIds(ids);
        return Result.ok();
    }
}

