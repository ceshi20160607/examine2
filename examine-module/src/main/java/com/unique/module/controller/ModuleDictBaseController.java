package com.unique.module.controller;


import cn.hutool.core.util.ObjectUtil;
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


import com.unique.module.service.IModuleDictBaseService;
import com.unique.module.entity.po.ModuleDictBase;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 数据字段基础表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
@RestController
@RequestMapping("/moduleDictBase")
@Api(tags = "数据字段基础表")
public class ModuleDictBaseController {

    @Autowired
    private IModuleDictBaseService moduleDictBaseService;


    /**
    * 查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @PostMapping("/queryPageList")
    @ApiOperation("查询列表页数据")
    public Result<BasePage<Map<String, Object>>> queryPageList(@RequestBody SearchBO search) {
        search.setPageType(1);
        BasePage<Map<String, Object>> mapBasePage = moduleDictBaseService.queryPageList(search);
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
    public Result<Map<String, Object>> add(@RequestBody ModuleDictBase baseModel) {
        Map<String, Object> map = moduleDictBaseService.addOrUpdate(baseModel, false);
        return Result.ok(map);
    }
    /**
    * 更新数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/update")
    @ApiOperation("修改数据")
    public Result<Map<String, Object>> update(@RequestBody ModuleDictBase baseModel) {
        Map<String, Object> map = moduleDictBaseService.addOrUpdate(baseModel, false);
        return Result.ok(map);
    }
    /**
    * 查询数据
    * @param id 业务对象id
    * @return data
    */
    @PostMapping("/queryById/{id}")
    @ApiOperation("根据ID查询")
    public Result<Map<String, Object> > queryById(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {
        Map<String, Object>  model = moduleDictBaseService.queryById(id);
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
        moduleDictBaseService.deleteByIds(ids);
        return Result.ok();
    }


}

