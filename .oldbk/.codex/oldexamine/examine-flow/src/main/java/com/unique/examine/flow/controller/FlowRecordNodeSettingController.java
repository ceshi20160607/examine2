package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowRecordNodeSetting;
import com.unique.examine.flow.service.IFlowRecordNodeSettingService;
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
 * record-节点设置（setting；可覆盖模板/实例） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/flowRecordNodeSetting")
public class FlowRecordNodeSettingController {

    @Autowired
    private IFlowRecordNodeSettingService flowRecordNodeSettingService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowRecordNodeSetting> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowRecordNodeSetting entity = flowRecordNodeSettingService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowRecordNodeSetting entity) {
        flowRecordNodeSettingService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowRecordNodeSetting entity) {
        flowRecordNodeSettingService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowRecordNodeSetting>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowRecordNodeSettingService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowRecordNodeSettingService.deleteByIds(ids);
        return Result.ok();
    }
}
