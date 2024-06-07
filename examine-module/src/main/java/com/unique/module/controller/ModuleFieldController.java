package com.unique.module.controller;


import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.module.entity.bo.ModuleFieldBO;
import com.unique.module.entity.po.ModuleField;
import com.unique.module.service.IModuleFieldService;
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
 * 自定义字段表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@RestController
@RequestMapping("/moduleField")
@Api(tags = "自定义字段表")
public class ModuleFieldController {

    @Autowired
    private IModuleFieldService moduleFieldService;

    /**
    * 新建页面字段
    *
    */
    @PostMapping("/queryFieldAdd")
    @ApiOperation("查询新增所需字段")
    public Result<List> queryFieldAdd(@RequestParam(value = "type", required = false) String type,@ApiParam(name = "moduleId", value = "moduleId") Long moduleId) {
        if (ObjectUtil.isNotEmpty(type)) {
            return Result.ok(moduleFieldService.queryField(moduleId));
        }
        return Result.ok(moduleFieldService.queryFormField(moduleId));
    }

    /**
    * 编辑页面字段
    *
    * @param moduleId
    */
    @PostMapping("/queryFieldEdit/{moduleId}")
    @ApiOperation("查询修改数据所需信息")
    public Result<List> queryFieldEdit(@PathVariable("moduleId") @ApiParam(name = "moduleId", value = "moduleId") Long moduleId,
        @RequestParam(value = "type", required = false) String type) {
        if (ObjectUtil.isNotEmpty(type)) {
            List<ModuleField> collect = moduleFieldService.queryField(moduleId).stream().filter(field -> !field.getFieldName().equals("ownerUserId")).collect(Collectors.toList());
            return Result.ok(collect);
        }
        return Result.ok(moduleFieldService.queryFormField(moduleId));
    }
    /**
    * 保存数据
    *
    * @param moduleBO 业务对象
    * @return data
    */
    @PostMapping("/add")
    @ApiOperation("保存数据")
    public Result add(@RequestBody ModuleFieldBO moduleBO) {
        moduleFieldService.addOrUpdate(moduleBO, false);
        return Result.ok();
    }
    /**
    * 更新数据
    *
    * @param moduleBO 业务对象
    * @return data
    */
    @PostMapping("/update")
    @ApiOperation("修改数据")
    public Result update(@RequestBody ModuleFieldBO moduleBO) {
        moduleFieldService.addOrUpdate(moduleBO, false);
        return Result.ok();
    }

//    @PostMapping("/queryFieldNameWithMainTableList/{moduleId}/{type}")
//    @ApiOperation("查询字段在主表的扩展字段")
//    public Result queryFieldNameWithMainTableList(@PathVariable("moduleId") @ApiParam(name = "moduleId", value = "moduleId") Long moduleId,
//                                                  @PathVariable("type") @ApiParam(name = "type", value = "type") Integer type) {
//        List<String> ret = moduleFieldService.queryFieldNameWithMainTableList(moduleId, type);
//        return Result.ok(ret);
//    }

}

