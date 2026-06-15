package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowNodeTemp;
import com.unique.examine.flow.service.IFlowNodeTempService;
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
 * 节点模板（node_temp；可复用） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/flowNodeTemp")
public class FlowNodeTempController {

    @Autowired
    private IFlowNodeTempService flowNodeTempService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowNodeTemp> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowNodeTemp entity = flowNodeTempService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowNodeTemp entity) {
        flowNodeTempService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowNodeTemp entity) {
        flowNodeTempService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowNodeTemp>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowNodeTempService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowNodeTempService.deleteByIds(ids);
        return Result.ok();
    }
}
