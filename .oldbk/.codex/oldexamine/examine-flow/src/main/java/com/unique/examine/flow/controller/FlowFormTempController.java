package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowFormTemp;
import com.unique.examine.flow.service.IFlowFormTempService;
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
 * 表单模板（form_temp；发起/审批） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/flowFormTemp")
public class FlowFormTempController {

    @Autowired
    private IFlowFormTempService flowFormTempService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowFormTemp> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowFormTemp entity = flowFormTempService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowFormTemp entity) {
        flowFormTempService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowFormTemp entity) {
        flowFormTempService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowFormTemp>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowFormTempService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowFormTempService.deleteByIds(ids);
        return Result.ok();
    }
}
