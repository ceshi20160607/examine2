package com.unique.examine.flow.controller;

import com.unique.examine.core.common.Result;
import com.unique.examine.flow.entity.po.FlowTempVerLineCond;
import com.unique.examine.flow.service.IFlowTempVerLineCondService;
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
 * temp_ver-连线条件（line_cond；关系表版） 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@RestController
@RequestMapping("/flowTempVerLineCond")
public class FlowTempVerLineCondController {

    @Autowired
    private IFlowTempVerLineCondService flowTempVerLineCondService;

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<FlowTempVerLineCond> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        FlowTempVerLineCond entity = flowTempVerLineCondService.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody FlowTempVerLineCond entity) {
        flowTempVerLineCondService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody FlowTempVerLineCond entity) {
        flowTempVerLineCondService.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<FlowTempVerLineCond>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(flowTempVerLineCondService.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        flowTempVerLineCondService.deleteByIds(ids);
        return Result.ok();
    }
}
