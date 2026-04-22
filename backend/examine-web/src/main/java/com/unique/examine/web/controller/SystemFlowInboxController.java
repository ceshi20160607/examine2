package com.unique.examine.web.controller;

import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTask;
import com.unique.examine.web.service.FlowTaskInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "自建系统态-flow（待办）")
@RestController
@RequestMapping("/v1/system/flow/inbox")
public class SystemFlowInboxController {

    @Autowired
    private FlowTaskInboxService flowTaskInboxService;

    @Operation(summary = "我的待办（含或签候选；默认最多 50 条）")
    @GetMapping("/tasks/pending")
    public ApiResult<List<FlowTask>> myPending(@RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.ok(flowTaskInboxService.listMyPendingTasks(limit));
    }

    @Operation(summary = "我的抄送（cc；onlyUnread=1 仅未读；默认最多 50 条）")
    @GetMapping("/cc")
    public ApiResult<List<FlowTask>> myCc(@RequestParam(value = "limit", required = false) Integer limit,
                                          @RequestParam(value = "onlyUnread", required = false) Integer onlyUnread) {
        return ApiResult.ok(flowTaskInboxService.listMyCcTasks(limit, onlyUnread));
    }

    @Operation(summary = "抄送标记已读（cc）")
    @PostMapping("/cc/{taskId}/read")
    public ApiResult<Void> readCc(@PathVariable("taskId") Long taskId) {
        flowTaskInboxService.markMyCcRead(taskId);
        return ApiResult.ok();
    }
}

