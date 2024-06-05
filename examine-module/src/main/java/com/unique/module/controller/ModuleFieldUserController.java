package com.unique.module.controller;


import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.module.entity.bo.ModuleFieldUserBO;
import com.unique.module.entity.po.ModuleField;
import com.unique.module.service.IModuleFieldUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import com.unique.module.entity.po.ModuleFieldUser;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 自定义字段关联用户表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@RestController
@RequestMapping("/moduleFieldUser")
@Api(tags = "自定义字段关联用户表")
public class ModuleFieldUserController {

    @Autowired
    private IModuleFieldUserService moduleFieldUserService;

    /**
     * 列表页面字段
     *
     */
    @PostMapping("/queryFieldHead")
    @ApiOperation("查询列表所需字段")
    public Result<List<ModuleField>> queryFieldHead(@ApiParam(name = "moduleId", value = "moduleId") Long moduleId) {
        return Result.ok(moduleFieldUserService.queryFieldHead(moduleId));
    }

    /**
     * 列表筛选页面字段
     *
     */
    @PostMapping("/queryFieldSearch")
    @ApiOperation("查询列表所需字段")
    public Result<List<ModuleField>> queryFieldSearch(@ApiParam(name = "moduleId", value = "moduleId") Long moduleId) {
        return Result.ok(moduleFieldUserService.queryFieldSearch(moduleId));
    }

    /**
     * 列表排序字段
     *
     */
    @PostMapping("/changeFieldSort")
    @ApiOperation("修改列表所需字段")
    public Result changeFieldSort(@RequestBody ModuleFieldUserBO moduleFieldUserBO) {
        moduleFieldUserService.changeFieldSort(moduleFieldUserBO);
        return Result.ok();
    }



}

