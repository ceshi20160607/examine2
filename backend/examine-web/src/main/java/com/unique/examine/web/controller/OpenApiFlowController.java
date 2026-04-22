package com.unique.examine.web.controller;

import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowLogAction;
import com.unique.examine.flow.entity.po.FlowLogTrace;
import com.unique.examine.flow.entity.po.FlowRecord;
import com.unique.examine.flow.entity.po.FlowTask;
import com.unique.examine.flow.service.IFlowLogActionService;
import com.unique.examine.flow.service.IFlowLogTraceService;
import com.unique.examine.flow.service.IFlowRecordService;
import com.unique.examine.flow.service.IFlowTaskService;
import com.unique.examine.flow.manage.FlowEngineService;
import com.unique.examine.web.service.FlowBizActionService;
import com.unique.examine.web.service.FlowTaskInboxService;
import com.unique.examine.web.service.OpenApiIdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Tag(name = "对外开放-flow")
@RestController
@RequestMapping("/v1/open/flow")
public class OpenApiFlowController {

    @Autowired
    private FlowEngineService flowEngineService;
    @Autowired
    private OpenApiIdempotencyService openApiIdempotencyService;
    @Autowired
    private IFlowRecordService flowRecordService;
    @Autowired
    private IFlowTaskService flowTaskService;
    @Autowired
    private IFlowLogActionService flowLogActionService;
    @Autowired
    private IFlowLogTraceService flowLogTraceService;
    @Autowired
    private FlowTaskInboxService flowTaskInboxService;
    @Autowired
    private FlowBizActionService flowBizActionService;

    public record StartBody(String tempCode, String bizType, String bizId, String title, Map<String, Object> vars) {}

    public record ActionBody(String commentText) {}
    public record TerminateBody(String reason) {}
    public record TransferBody(Long toPlatId) {}
    public record BizActionBody(String bizType, String bizId, Long taskId, String commentText) {}

    @Operation(summary = "发起审批（需 AK/SK + X-Acting-Plat-Id；可选 Idempotency-Key）")
    @PostMapping("/instances/start")
    public ApiResult<FlowEngineService.StartResult> start(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody StartBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    if (body == null) {
                        return ApiResult.fail(400, "body 不能为空");
                    }
                    return ApiResult.ok(flowEngineService.startByTempCode(
                            body.tempCode(), body.bizType(), body.bizId(), body.title(), body.vars()));
                });
    }

    @Operation(summary = "办理-同意")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/approve")
    public ApiResult<FlowEngineService.TaskActionResult> approve(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) ActionBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> ApiResult.ok(flowEngineService.approve(instanceId, taskId,
                        body == null ? null : body.commentText())));
    }

    @Operation(summary = "办理-拒绝")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/reject")
    public ApiResult<FlowEngineService.TaskActionResult> reject(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) ActionBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> ApiResult.ok(flowEngineService.reject(instanceId, taskId,
                        body == null ? null : body.commentText())));
    }

    @Operation(summary = "撤回实例")
    @PostMapping("/instances/{instanceId}/withdraw")
    public ApiResult<Void> withdraw(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    flowEngineService.withdrawFlow(instanceId);
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "终止实例")
    @PostMapping("/instances/{instanceId}/terminate")
    public ApiResult<Void> terminate(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId,
            @RequestBody(required = false) TerminateBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    flowEngineService.terminateFlow(instanceId, body == null ? null : body.reason());
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "转交任务")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/transfer")
    public ApiResult<Void> transfer(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId,
            @RequestBody TransferBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    if (body == null || body.toPlatId() == null) {
                        return ApiResult.fail(400, "toPlatId 不能为空");
                    }
                    flowEngineService.transferTask(instanceId, taskId, body.toPlatId());
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "领取待办（并发保护；可选 Idempotency-Key）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/claim")
    public ApiResult<Void> claim(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    flowEngineService.claimTask(instanceId, taskId);
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "取消领取待办（仅领取人可取消；可选 Idempotency-Key）")
    @PostMapping("/instances/{instanceId}/tasks/{taskId}/unclaim")
    public ApiResult<Void> unclaim(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("instanceId") Long instanceId,
            @PathVariable("taskId") Long taskId) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    flowEngineService.unclaimTask(instanceId, taskId);
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "按 bizType+bizId 自动定位待办并同意（可选 taskId 精确指定；可选 Idempotency-Key）")
    @PostMapping("/instances/by-biz/approve")
    public ApiResult<FlowEngineService.TaskActionResult> approveByBiz(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody BizActionBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    if (body == null) {
                        return ApiResult.fail(400, "body 不能为空");
                    }
                    if (body.taskId() != null) {
                        return ApiResult.ok(flowBizActionService.approveByBiz(body.bizType(), body.bizId(), body.taskId(), body.commentText()));
                    }
                    return ApiResult.ok(flowBizActionService.approveByBiz(body.bizType(), body.bizId(), body.commentText()));
                });
    }

    @Operation(summary = "按 bizType+bizId 自动定位待办并拒绝（可选 taskId 精确指定；可选 Idempotency-Key）")
    @PostMapping("/instances/by-biz/reject")
    public ApiResult<FlowEngineService.TaskActionResult> rejectByBiz(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody BizActionBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    if (body == null) {
                        return ApiResult.fail(400, "body 不能为空");
                    }
                    if (body.taskId() != null) {
                        return ApiResult.ok(flowBizActionService.rejectByBiz(body.bizType(), body.bizId(), body.taskId(), body.commentText()));
                    }
                    return ApiResult.ok(flowBizActionService.rejectByBiz(body.bizType(), body.bizId(), body.commentText()));
                });
    }

    @Operation(summary = "实例详情（instanceId=un_flow_record.id）")
    @GetMapping("/instances/{instanceId}")
    public ApiResult<FlowRecord> instance(@PathVariable("instanceId") Long instanceId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getId, instanceId)
                .last("limit 1")
                .one();
        return rec == null ? ApiResult.fail(404, "实例不存在") : ApiResult.ok(rec);
    }

    @Operation(summary = "实例任务列表（待办/已办）")
    @GetMapping("/instances/{instanceId}/tasks")
    public ApiResult<List<FlowTask>> tasks(@PathVariable("instanceId") Long instanceId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        return ApiResult.ok(flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getRecordId, instanceId)
                .orderByDesc(FlowTask::getCreateTime)
                .list());
    }

    @Operation(summary = "实例动作日志（log_action）")
    @GetMapping("/instances/{instanceId}/actions")
    public ApiResult<List<FlowLogAction>> actions(@PathVariable("instanceId") Long instanceId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        return ApiResult.ok(flowLogActionService.lambdaQuery()
                .eq(FlowLogAction::getSystemId, systemId)
                .eq(FlowLogAction::getTenantId, tenantId)
                .eq(FlowLogAction::getRecordId, instanceId)
                .orderByAsc(FlowLogAction::getActionTime)
                .orderByAsc(FlowLogAction::getId)
                .list());
    }

    @Operation(summary = "实例轨迹（log_trace）")
    @GetMapping("/instances/{instanceId}/traces")
    public ApiResult<List<FlowLogTrace>> traces(@PathVariable("instanceId") Long instanceId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        return ApiResult.ok(flowLogTraceService.lambdaQuery()
                .eq(FlowLogTrace::getSystemId, systemId)
                .eq(FlowLogTrace::getTenantId, tenantId)
                .eq(FlowLogTrace::getRecordId, instanceId)
                .orderByAsc(FlowLogTrace::getEventTime)
                .orderByAsc(FlowLogTrace::getId)
                .list());
    }

    @Operation(summary = "我的待办（开放 API；含或签候选）")
    @GetMapping("/inbox/tasks/pending")
    public ApiResult<List<FlowTask>> myPending(@RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.ok(flowTaskInboxService.listMyPendingTasks(limit));
    }

    @Operation(summary = "我的抄送（cc；开放 API；onlyUnread=1 仅未读）")
    @GetMapping("/inbox/cc")
    public ApiResult<List<FlowTask>> myCc(@RequestParam(value = "limit", required = false) Integer limit,
                                          @RequestParam(value = "onlyUnread", required = false) Integer onlyUnread) {
        return ApiResult.ok(flowTaskInboxService.listMyCcTasks(limit, onlyUnread));
    }

    @Operation(summary = "抄送标记已读（cc；开放 API；可选 Idempotency-Key）")
    @PostMapping("/inbox/cc/{taskId}/read")
    public ApiResult<Void> readCc(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("taskId") Long taskId) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    flowTaskInboxService.markMyCcRead(taskId);
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "按 bizType+bizId 查询实例（默认返回最新一条；可选 onlyRunning=1 仅运行中）")
    @GetMapping("/instances/by-biz")
    public ApiResult<FlowRecord> byBiz(@RequestParam("bizType") String bizType,
                                       @RequestParam("bizId") String bizId,
                                       @RequestParam(value = "onlyRunning", required = false) Integer onlyRunning) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        var q = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getBizType, bizType)
                .eq(FlowRecord::getBizId, bizId);
        if (onlyRunning != null && onlyRunning == 1) {
            q.eq(FlowRecord::getStatus, 1);
        }
        FlowRecord rec = q.orderByDesc(FlowRecord::getId).last("limit 1").one();
        return rec == null ? ApiResult.fail(404, "实例不存在") : ApiResult.ok(rec);
    }

    @Operation(summary = "按 bizType+bizId 查询实例与待办（返回 record + pendingTasks）")
    @GetMapping("/instances/by-biz/with-pending-tasks")
    public ApiResult<Map<String, Object>> byBizWithPendingTasks(@RequestParam("bizType") String bizType,
                                                                @RequestParam("bizId") String bizId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getBizType, bizType)
                .eq(FlowRecord::getBizId, bizId)
                .orderByDesc(FlowRecord::getId)
                .last("limit 1")
                .one();
        if (rec == null) {
            return ApiResult.fail(404, "实例不存在");
        }
        List<FlowTask> pending = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getRecordId, rec.getId())
                .eq(FlowTask::getStatus, 1)
                .orderByDesc(FlowTask::getCreateTime)
                .list();
        return ApiResult.ok(Map.of("record", rec, "pendingTasks", pending));
    }

    @Operation(summary = "按 bizType+bizId 查询当前用户可办理的待办（运行中最新实例）")
    @GetMapping("/instances/by-biz/actionable-tasks")
    public ApiResult<List<FlowTask>> actionableTasksByBiz(@RequestParam("bizType") String bizType,
                                                          @RequestParam("bizId") String bizId) {
        return ApiResult.ok(flowBizActionService.listActionablePendingTasksByBiz(bizType, bizId));
    }

    @Operation(summary = "子流程实例列表（parentRecordId=instanceId）")
    @GetMapping("/instances/{instanceId}/children")
    public ApiResult<List<FlowRecord>> children(@PathVariable("instanceId") Long instanceId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        return ApiResult.ok(flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getParentRecordId, instanceId)
                .orderByAsc(FlowRecord::getId)
                .list());
    }

    @Operation(summary = "根单据下所有子实例（rootRecordId=instanceId 或 rootRecordId=record.rootRecordId）")
    @GetMapping("/instances/{instanceId}/tree")
    public ApiResult<List<FlowRecord>> tree(@PathVariable("instanceId") Long instanceId) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getId, instanceId)
                .last("limit 1")
                .one();
        if (rec == null) {
            return ApiResult.fail(404, "实例不存在");
        }
        Long rootId = rec.getRootRecordId() == null ? rec.getId() : rec.getRootRecordId();
        return ApiResult.ok(flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .and(w -> w.eq(FlowRecord::getRootRecordId, rootId)
                        .or()
                        .eq(FlowRecord::getId, rootId))
                .orderByAsc(FlowRecord::getId)
                .list());
    }

    @Operation(summary = "实例分页列表（按 system/tenant 隔离；支持 status/bizType/tempId/rootOnly/currentNodeKey/starterPlatId/keyword/时间范围）")
    @GetMapping("/instances/page")
    public ApiResult<Map<String, Object>> instancesPage(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "starterPlatId", required = false) Long starterPlatId,
            @RequestParam(value = "tempId", required = false) Long tempId,
            @RequestParam(value = "rootOnly", required = false) Integer rootOnly,
            @RequestParam(value = "currentNodeKey", required = false) String currentNodeKey,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startFrom", required = false) String startFrom,
            @RequestParam(value = "startTo", required = false) String startTo
    ) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        var q = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId);
        if (status != null) {
            q.eq(FlowRecord::getStatus, status);
        }
        if (bizType != null && !bizType.isBlank()) {
            q.eq(FlowRecord::getBizType, bizType.trim());
        }
        if (starterPlatId != null) {
            q.eq(FlowRecord::getStarterPlatId, starterPlatId);
        }
        if (tempId != null) {
            q.eq(FlowRecord::getTempId, tempId);
        }
        if (rootOnly != null && rootOnly == 1) {
            q.isNull(FlowRecord::getParentRecordId);
        }
        if (currentNodeKey != null && !currentNodeKey.isBlank()) {
            q.eq(FlowRecord::getCurrentNodeKey, currentNodeKey.trim());
        }
        LocalDateTime from = parseIsoDateTimeNullable(startFrom);
        LocalDateTime to = parseIsoDateTimeNullable(startTo);
        if (from != null) {
            q.ge(FlowRecord::getStartTime, from);
        }
        if (to != null) {
            q.le(FlowRecord::getStartTime, to);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(FlowRecord::getTitle, kw)
                    .or()
                    .like(FlowRecord::getBizId, kw));
        }

        long total = q.count();
        List<FlowRecord> records = total == 0 ? List.of() : q.orderByDesc(FlowRecord::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    @Operation(summary = "任务分页列表（按 system/tenant 隔离；支持 status/recordId/taskType/assigneePlatId/nodeKey/keyword/创建时间范围）")
    @GetMapping("/tasks/page")
    public ApiResult<Map<String, Object>> tasksPage(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "recordId", required = false) Long recordId,
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "assigneePlatId", required = false) Long assigneePlatId,
            @RequestParam(value = "nodeKey", required = false) String nodeKey,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "createFrom", required = false) String createFrom,
            @RequestParam(value = "createTo", required = false) String createTo
    ) {
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        var q = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId);
        if (status != null) {
            q.eq(FlowTask::getStatus, status);
        }
        if (recordId != null) {
            q.eq(FlowTask::getRecordId, recordId);
        }
        if (taskType != null && !taskType.isBlank()) {
            q.eq(FlowTask::getTaskType, taskType.trim());
        }
        if (assigneePlatId != null) {
            q.eq(FlowTask::getAssigneePlatId, assigneePlatId);
        }
        if (nodeKey != null && !nodeKey.isBlank()) {
            q.eq(FlowTask::getNodeKey, nodeKey.trim());
        }
        LocalDateTime cFrom = parseIsoDateTimeNullable(createFrom);
        LocalDateTime cTo = parseIsoDateTimeNullable(createTo);
        if (cFrom != null) {
            q.ge(FlowTask::getCreateTime, cFrom);
        }
        if (cTo != null) {
            q.le(FlowTask::getCreateTime, cTo);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(FlowTask::getNodeName, kw)
                    .or()
                    .like(FlowTask::getNodeKey, kw));
        }

        long total = q.count();
        List<FlowTask> tasks = total == 0 ? List.of() : q.orderByDesc(FlowTask::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", tasks));
    }

    @Operation(summary = "我的相关实例分页（我发起 + 我待办 + 我抄送；开放 API；可选关键字/时间范围/仅运行中/tempId/rootOnly/currentNodeKey）")
    @GetMapping("/instances/my/page")
    public ApiResult<Map<String, Object>> myInstancesPage(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "onlyRunning", required = false) Integer onlyRunning,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startFrom", required = false) String startFrom,
            @RequestParam(value = "startTo", required = false) String startTo,
            @RequestParam(value = "tempId", required = false) Long tempId,
            @RequestParam(value = "rootOnly", required = false) Integer rootOnly,
            @RequestParam(value = "currentNodeKey", required = false) String currentNodeKey,
            @RequestParam(value = "includeStarted", required = false) Integer includeStarted,
            @RequestParam(value = "includeTodo", required = false) Integer includeTodo,
            @RequestParam(value = "includeCc", required = false) Integer includeCc
    ) {
        Long platId = com.unique.examine.core.security.AuthContextHolder.getPlatId();
        if (platId == null) {
            return ApiResult.fail(401, "未登录");
        }
        long systemId = com.unique.examine.core.security.AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            return ApiResult.fail(403, "请先进入自建系统");
        }
        long tenantId = com.unique.examine.core.security.AuthContextHolder.getTenantIdOrDefault();
        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        boolean incStarted = includeStarted == null || includeStarted == 1;
        boolean incTodo = includeTodo == null || includeTodo == 1;
        boolean incCc = includeCc == null || includeCc == 1;
        boolean runningOnly = onlyRunning != null && onlyRunning == 1;

        LocalDateTime from = parseIsoDateTimeNullable(startFrom);
        LocalDateTime to = parseIsoDateTimeNullable(startTo);

        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (incStarted) {
            List<FlowRecord> started = flowRecordService.lambdaQuery()
                    .eq(FlowRecord::getSystemId, systemId)
                    .eq(FlowRecord::getTenantId, tenantId)
                    .eq(FlowRecord::getStarterPlatId, platId)
                    .orderByDesc(FlowRecord::getId)
                    .last("limit 300")
                    .list();
            if (started != null) {
                for (FlowRecord r : started) {
                    if (r.getId() != null) {
                        idSet.add(r.getId());
                    }
                }
            }
        }
        if (incTodo) {
            List<FlowTask> todo = flowTaskInboxService.listMyPendingTasks(200);
            if (todo != null) {
                for (FlowTask t : todo) {
                    if (t.getRecordId() != null) {
                        idSet.add(t.getRecordId());
                    }
                }
            }
        }
        if (incCc) {
            List<FlowTask> cc = flowTaskInboxService.listMyCcTasks(200, null);
            if (cc != null) {
                for (FlowTask t : cc) {
                    if (t.getRecordId() != null) {
                        idSet.add(t.getRecordId());
                    }
                }
            }
        }

        if (idSet.isEmpty()) {
            return ApiResult.ok(Map.of("page", p, "size", s, "total", 0, "records", List.of()));
        }

        var q = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .in(FlowRecord::getId, idSet);
        if (runningOnly) {
            q.eq(FlowRecord::getStatus, 1);
        }
        if (from != null) {
            q.ge(FlowRecord::getStartTime, from);
        }
        if (to != null) {
            q.le(FlowRecord::getStartTime, to);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(FlowRecord::getTitle, kw)
                    .or()
                    .like(FlowRecord::getBizId, kw));
        }
        if (tempId != null) {
            q.eq(FlowRecord::getTempId, tempId);
        }
        if (rootOnly != null && rootOnly == 1) {
            q.isNull(FlowRecord::getParentRecordId);
        }
        if (currentNodeKey != null && !currentNodeKey.isBlank()) {
            q.eq(FlowRecord::getCurrentNodeKey, currentNodeKey.trim());
        }

        long total = q.count();
        List<FlowRecord> records = total == 0 ? List.of() : q.orderByDesc(FlowRecord::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    private static LocalDateTime parseIsoDateTimeNullable(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new com.unique.examine.core.exception.BusinessException(400, "时间格式错误，请使用 ISO-8601：yyyy-MM-ddTHH:mm:ss");
        }
    }
}
