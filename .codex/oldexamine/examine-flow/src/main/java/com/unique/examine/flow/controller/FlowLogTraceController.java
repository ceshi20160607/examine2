package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowLogTrace;
import com.unique.examine.flow.service.IFlowLogTraceService;
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
 * 流转轨迹日志（log_trace；原 instance_trace） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/flowLogTrace")
public class FlowLogTraceController {

    @Autowired
    private IFlowLogTraceService flowLogTraceService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowLogTrace> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowLogTrace entity = flowLogTraceService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowLogTrace entity) {
        flowLogTraceService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowLogTrace entity) {
        flowLogTraceService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowLogTrace>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowLogTraceService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowLogTraceService.deleteByIds(ids);
        return Result.ok();
    }
}
