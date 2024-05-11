package com.unique.examine.controller;


import com.unique.examine.entity.dto.ExamineNodeAdd;
import com.unique.examine.entity.dto.ExamineNodeFill;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.examine.service.IExamineRecordNodeService;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 审批节点表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-01-30
 */
@RestController
@RequestMapping("/examineRecordNode")
public class ExamineRecordNodeController {

    @Autowired
    private IExamineRecordNodeService examineRecordNodeService;

    @PostMapping("/addNewNode")
    @ApiOperation("动态添加审批节点")
    public Result addNewNode(@RequestBody ExamineNodeAdd nodeAdd){
        examineRecordNodeService.addNewNode(nodeAdd);
        return Result.ok();
    }


    @GetMapping("/queryList/{examineRecordId}")
    public Result list(@PathVariable("examineRecordId") Long examineRecordId){
//        BasePage<ExamineRecordNode> ret = examineRecordNodeService.pageList(examineRecordId);
        return Result.ok(null);
    }
}
