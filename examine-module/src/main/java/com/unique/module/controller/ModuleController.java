package com.unique.module.controller;


import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.module.entity.po.ModuleField;
import com.unique.module.entity.po.Module;
import com.unique.module.entity.vo.ModuleVO;
import com.unique.module.service.IModuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 模块表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@RestController
@RequestMapping("/module")
@Api(tags = "模块表")
public class ModuleController {

    @Autowired
    private IModuleService moduleService;


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
        BasePage<Map<String, Object>> mapBasePage = moduleService.queryPageList(search);
        return Result.ok(mapBasePage);
    }
    @PostMapping("/queryPageListBean")
    @ApiOperation("查询列表页数据")
    public Result<BasePage<Module>> queryPageListBean(@RequestBody SearchBO search) {
        search.setPageType(1);
        BasePage<Module> mapBasePage = moduleService.queryPageListBean(search);
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
    public Result<Map<String, Object>> add(@RequestBody Module baseModel) {
        Map<String, Object> map = moduleService.addOrUpdate(baseModel, false);
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
    public Result<Map<String, Object>> update(@RequestBody Module baseModel) {
        Map<String, Object> map = moduleService.addOrUpdate(baseModel, false);
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
        Map<String, Object>  model = moduleService.queryById(id);
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
        moduleService.deleteByIds(ids);
        return Result.ok();
    }



    /**
     * 模块的数据--树形结构
     *
     * @param search 业务查询对象
     * @return data
     */
    @PostMapping("/queryPageListTree")
    @ApiOperation("查询列表页数据")
    public Result<List<Module>> queryPageListTree(@RequestBody SearchBO search) {
        List<Module> ret = moduleService.queryPageListTree(search);
        return Result.ok(ret);
    }
}

