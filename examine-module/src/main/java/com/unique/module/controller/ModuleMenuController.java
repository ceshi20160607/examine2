package com.unique.module.controller;


import com.unique.core.common.Result;
import com.unique.module.entity.bo.ModuleMenuBO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


import com.unique.module.service.IModuleMenuService;
import com.unique.module.entity.po.ModuleMenu;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 模块菜单功能权限配置表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
@RestController
@RequestMapping("/moduleMenu")
@Api(tags = "模块菜单功能权限配置表")
public class ModuleMenuController {

    @Autowired
    private IModuleMenuService moduleMenuService;


//    /**
//    * 查询所有数据
//    *
//    * @param search 业务查询对象
//    * @return data
//    */
//    @PostMapping("/queryPageList")
//    @ApiOperation("查询列表页数据")
//    public Result<BasePage<Map<String, Object>>> queryPageList(@RequestBody SearchBO search) {
//        search.setPageType(1);
//        BasePage<Map<String, Object>> mapBasePage = moduleMenuService.queryPageList(search);
//        return Result.ok(mapBasePage);
//    }

    /**
    * 保存数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/add")
    @ApiOperation("保存数据")
    public Result add(@RequestBody ModuleMenuBO baseModel) {
        moduleMenuService.addOrUpdate(baseModel);
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
    public Result  update(@RequestBody ModuleMenuBO baseModel) {
        moduleMenuService.addOrUpdate(baseModel);
        return Result.ok();
    }
    /**
    * 查询数据
    * @return data
    */
    @PostMapping("/queryModuleMenuList")
    @ApiOperation("查询模块的菜单")
    public Result<List<ModuleMenu>> queryById(@RequestParam(value = "moduleId",required = false) Long moduleId) {
        List<ModuleMenu> ret = moduleMenuService.queryModuleMenuList(moduleId);
        return Result.ok(ret);
    }


    /**
    * 删除数据
    * @param ids 业务对象ids
    * @return data
    */
    @PostMapping("/deleteByIds")
    @ApiOperation("根据ID删除数据")
    public Result deleteByIds(@ApiParam(name = "ids", value = "id列表") @RequestBody List<Long> ids) {
        moduleMenuService.deleteByIds(ids);
        return Result.ok();
    }


}

