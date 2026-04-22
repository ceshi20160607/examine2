package com.unique.examine.web.controller;

import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.manage.FlowEngineService;
import com.unique.examine.flow.manage.FlowBizActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "自建系统态-flow（MVP）")
@RestController
@RequestMapping("/v1/system/flow")
public class SystemFlowController {

    @Autowired
    private FlowEngineService flowEngineService;
    @Autowired
    private FlowBizActionService flowBizActionService;

    public record StartBody(String defCode, String bizType, String bizId, String title, Map<String, Object> vars) {}
    public record ActionBody(String commentText) {}
    public record TerminateBody(String reason) {}
    public record TransferBody(Long toPlatId) {}
    public record BizActionBody(String bizType, String bizId, Long taskId, String commentText) {}

    @Operation(summary = "发起审批（MVP-1：defCode 对应 un_flow_temp.temp_code；落库 un_flow_record + vars + 首条待办）")
    @PostMapping("/instances/start")
    public ApiResult<FlowEngineService.StartResult> start(@RequestBody StartBody body) {
        if (body == null) {
            return ApiResult.fail(400, "body 不能为空");
        }
        return ApiResult.ok(flowEngineService.startByDefCode(body.defCode(), body.bizType(), body.bizId(), body.title(), body.vars()));
    }

    @Operation(summary = "办理-同意（instanceId=un_flow_record.id；按 graph_json.edges 推进；cond 分支；exception_policy 兜底）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/approve")
    public ApiResult<FlowEngineService.TaskActionResult> approve(
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) ActionBody body
    ) {
        return ApiResult.ok(flowEngineService.approve(instanceId, taskId, body == null ? null : body.commentText()));
    }

    @Operation(summary = "办理-拒绝（MVP-1：拒绝后直接终态）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/reject")
    public ApiResult<FlowEngineService.TaskActionResult> reject(
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) ActionBody body
    ) {
        return ApiResult.ok(flowEngineService.reject(instanceId, taskId, body == null ? null : body.commentText()));
    }

    @Operation(summary = "撤回实例（发起人；待办取消）")
    @PostMapping("/instances/{instanceId}/withdraw")
    public ApiResult<Void> withdraw(@PathVariable("instanceId") Long instanceId) {
        flowEngineService.withdrawFlow(instanceId);
        return ApiResult.ok();
    }

    @Operation(summary = "终止实例（发起人）")
    @PostMapping("/instances/{instanceId}/terminate")
    public ApiResult<Void> terminate(@PathVariable("instanceId") Long instanceId,
                                   @RequestBody(required = false) TerminateBody body) {
        flowEngineService.terminateFlow(instanceId, body == null ? null : body.reason());
        return ApiResult.ok();
    }

    @Operation(summary = "转交任务（当前处理人）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/transfer")
    public ApiResult<Void> transfer(@PathVariable("instanceId") Long instanceId,
                                    @PathVariable("taskId") Long taskId,
                                    @RequestBody TransferBody body) {
        if (body == null || body.toPlatId() == null) {
            return ApiResult.fail(400, "toPlatId 不能为空");
        }
        flowEngineService.transferTask(instanceId, taskId, body.toPlatId());
        return ApiResult.ok();
    }

    @Operation(summary = "领取待办（并发保护：领取后仅领取人可办理）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/claim")
    public ApiResult<Void> claim(@PathVariable("instanceId") Long instanceId,
                                 @PathVariable("taskId") Long taskId) {
        flowEngineService.claimTask(instanceId, taskId);
        return ApiResult.ok();
    }

    @Operation(summary = "取消领取待办（仅领取人可取消；或签任务会将 assignee 置空回到候选状态）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/unclaim")
    public ApiResult<Void> unclaim(@PathVariable("instanceId") Long instanceId,
                                   @PathVariable("taskId") Long taskId) {
        flowEngineService.unclaimTask(instanceId, taskId);
        return ApiResult.ok();
    }

    @Operation(summary = "按 bizType+bizId 自动定位待办并同意（可选 taskId 精确指定；否则多待办=409）")
    @PostMapping("/instances/by-biz/approve")
    public ApiResult<FlowEngineService.TaskActionResult> approveByBiz(@RequestBody BizActionBody body) {
        if (body == null) {
            return ApiResult.fail(400, "body 不能为空");
        }
        if (body.taskId() != null) {
            return ApiResult.ok(flowBizActionService.approveByBiz(body.bizType(), body.bizId(), body.taskId(), body.commentText()));
        }
        return ApiResult.ok(flowBizActionService.approveByBiz(body.bizType(), body.bizId(), body.commentText()));
    }

    @Operation(summary = "按 bizType+bizId 自动定位待办并拒绝（可选 taskId 精确指定；否则多待办=409）")
    @PostMapping("/instances/by-biz/reject")
    public ApiResult<FlowEngineService.TaskActionResult> rejectByBiz(@RequestBody BizActionBody body) {
        if (body == null) {
            return ApiResult.fail(400, "body 不能为空");
        }
        if (body.taskId() != null) {
            return ApiResult.ok(flowBizActionService.rejectByBiz(body.bizType(), body.bizId(), body.taskId(), body.commentText()));
        }
        return ApiResult.ok(flowBizActionService.rejectByBiz(body.bizType(), body.bizId(), body.commentText()));
    }
}

