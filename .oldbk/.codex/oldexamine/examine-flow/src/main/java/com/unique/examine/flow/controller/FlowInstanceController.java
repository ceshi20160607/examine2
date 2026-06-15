package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowInstance;
import com.unique.examine.flow.service.IFlowInstanceService;
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
 * 流程实例 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/flowInstance")
public class FlowInstanceController {

    @Autowired
    private IFlowInstanceService flowInstanceService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowInstance> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowInstance entity = flowInstanceService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowInstance entity) {
        flowInstanceService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowInstance entity) {
        flowInstanceService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowInstance>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowInstanceService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowInstanceService.deleteByIds(ids);
        return Result.ok();
    }
}
