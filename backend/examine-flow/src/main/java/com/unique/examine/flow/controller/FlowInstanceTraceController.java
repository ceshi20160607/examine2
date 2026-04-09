package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.PO.FlowInstanceTrace;
import com.unique.examine.flow.service.IFlowInstanceTraceService;
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
 * 实例流转轨迹 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@RestController
@RequestMapping("/flowInstanceTrace")
public class FlowInstanceTraceController {

    @Autowired
    private IFlowInstanceTraceService flowInstanceTraceService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowInstanceTrace> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowInstanceTrace entity = flowInstanceTraceService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowInstanceTrace entity) {
        flowInstanceTraceService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowInstanceTrace entity) {
        flowInstanceTraceService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowInstanceTrace>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowInstanceTraceService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowInstanceTraceService.deleteByIds(ids);
        return Result.ok();
    }
}
