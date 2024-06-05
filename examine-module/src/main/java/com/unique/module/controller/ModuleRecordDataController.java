package com.unique.module.controller;


import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.module.entity.po.ModuleField;
import com.unique.module.service.IModuleRecordDataService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import com.unique.module.entity.po.ModuleRecordData;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 主数据自定义字段存值表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@RestController
@RequestMapping("/module-record-data")
@Api(tags = "主数据自定义字段存值表")
public class ModuleRecordDataController {

    @Autowired
    private IModuleRecordDataService moduleRecordDataService;


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
        BasePage<Map<String, Object>> mapBasePage = moduleRecordDataService.queryPageList(search);
        return Result.ok(mapBasePage);
    }
    /**
    * 新建页面字段
    *
    */
    @PostMapping("/queryFieldAdd")
    @ApiOperation("查询新增所需字段")
    public Result<List> queryFieldAdd(@RequestParam(value = "type", required = false) String type) {
        if (ObjectUtil.isNotEmpty(type)) {
            return Result.ok(moduleRecordDataService.queryField(null));
        }
        return Result.ok(moduleRecordDataService.queryFormField(null));
    }

    /**
    * 编辑页面字段
    *
    * @param id
    */
    @PostMapping("/queryFieldEdit/{id}")
    @ApiOperation("查询修改数据所需信息")
    public Result<List> queryFieldEdit(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id,
        @RequestParam(value = "type", required = false) String type) {
        if (ObjectUtil.isNotEmpty(type)) {
            List<ModuleField> collect = moduleRecordDataService.queryField(id).stream().filter(field -> !field.getFieldName().equals("ownerUserId")).collect(Collectors.toList());
            return Result.ok(collect);
        }
        return Result.ok(moduleRecordDataService.queryFormField(id));
    }
    /**
    * 保存数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/add")
    @ApiOperation("保存数据")
    public Result<Map<String, Object>> add(@RequestBody ModuleRecordData baseModel) {
        Map<String, Object> map = moduleRecordDataService.addOrUpdate(baseModel, false);
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
    public Result<Map<String, Object>> update(@RequestBody ModuleRecordData baseModel) {
        Map<String, Object> map = moduleRecordDataService.addOrUpdate(baseModel, false);
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
        Map<String, Object>  model = moduleRecordDataService.queryById(id);
        return Result.ok(model);
    }
    /**
    * 查询详情页基本信息
    *
    * @param  id
    * @return data
    */
    @PostMapping("/information/{id}")
    @ApiOperation("查询详情页信息")
    public Result<List<ModuleField>> information(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {

        List<ModuleField> information = moduleRecordDataService.information(id);

        return Result.ok(information);
    }

    /**
    * 删除数据
    * @param ids 业务对象ids
    * @return data
    */
    @PostMapping("/deleteByIds")
    @ApiOperation("根据ID删除数据")
    public Result deleteByIds(@ApiParam(name = "ids", value = "id列表") @RequestBody List<Long> ids) {
        moduleRecordDataService.deleteByIds(ids);
        return Result.ok();
    }


}

