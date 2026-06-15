package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowTempVerNodeSetting;
import com.unique.examine.flow.service.IFlowTempVerNodeSettingService;
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
 * temp_ver-节点设置（setting；异常兜底覆盖） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/flowTempVerNodeSetting")
public class FlowTempVerNodeSettingController {

    @Autowired
    private IFlowTempVerNodeSettingService flowTempVerNodeSettingService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowTempVerNodeSetting> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowTempVerNodeSetting entity = flowTempVerNodeSettingService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowTempVerNodeSetting entity) {
        flowTempVerNodeSettingService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowTempVerNodeSetting entity) {
        flowTempVerNodeSettingService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowTempVerNodeSetting>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowTempVerNodeSettingService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowTempVerNodeSettingService.deleteByIds(ids);
        return Result.ok();
    }
}
