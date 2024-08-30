package com.unique.module.controller;


import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


import com.unique.module.service.IModuleDeptService;
import com.unique.module.entity.po.ModuleDept;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 部门表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
@RestController
@RequestMapping("/moduleDept")
@Api(tags = "部门表")
public class ModuleDeptController {

    @Autowired
    private IModuleDeptService moduleDeptService;


    /**
    * 查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @PostMapping("/queryPageList")
    @ApiOperation("查询列表页数据")
    public Result<BasePage<ModuleDept>> queryPageList(@RequestBody SearchBO search) {
        search.setPageType(1);
        BasePage<ModuleDept> mapBasePage = moduleDeptService.queryPageList(search);
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
    public Result add(@RequestBody ModuleDept baseModel) {
        moduleDeptService.addOrUpdate(baseModel, false);
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
    public Result update(@RequestBody ModuleDept baseModel) {
        moduleDeptService.addOrUpdate(baseModel, false);
        return Result.ok();
    }
    /**
    * 查询数据
    * @param id 业务对象id
    * @return data
    */
    @PostMapping("/queryById/{id}")
    @ApiOperation("根据ID查询")
    public Result<ModuleDept > queryById(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {
        ModuleDept  model = moduleDeptService.queryById(id);
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
        moduleDeptService.deleteByIds(ids);
        return Result.ok();
    }
}

