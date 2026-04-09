package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.PO.FlowFormTemplate;
import com.unique.examine.flow.service.IFlowFormTemplateService;
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
 * 表单模板（发起/审批） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@RestController
@RequestMapping("/flowFormTemplate")
public class FlowFormTemplateController {

    @Autowired
    private IFlowFormTemplateService flowFormTemplateService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowFormTemplate> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowFormTemplate entity = flowFormTemplateService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowFormTemplate entity) {
        flowFormTemplateService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowFormTemplate entity) {
        flowFormTemplateService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowFormTemplate>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowFormTemplateService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowFormTemplateService.deleteByIds(ids);
        return Result.ok();
    }
}
