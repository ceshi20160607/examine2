package com.unique.unexamine.work.manage.controller;

import com.unique.unexamine.work.manage.service.WorkTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

/** 工作任务 HTTP 入口；普通任务创建与列表独立，详情和操作复用同一业务服务。 */
@RestController
@RequestMapping("/api/work/tasks")
@Tag(name = "工作任务")
public class WorkTaskController {
    private final WorkTaskService service;

    /** 注入任务业务服务。 */
    public WorkTaskController(WorkTaskService service) { this.service = service; }

    /** 快速创建平台或系统租户内的普通任务。 */
    @PostMapping
    @Operation(summary = "快速创建普通任务")
    public WorkTaskService.TaskView create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId,
            @Valid @RequestBody WorkTaskService.CreateRequest request) {
        return service.create(token, systemId, tenantId, request);
    }

    /** 按当前作用域分页查询本人任务。 */
    @GetMapping
    @Operation(summary = "我的普通任务")
    public WorkTaskService.TaskPage list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return service.queryPageList(token, systemId, tenantId, status, page, size);
    }

    /** 查询当前作用域本人可见的任务详情。 */
    @GetMapping("/{taskId}")
    @Operation(summary = "工作任务详情")
    public WorkTaskService.TaskView detail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId) {
        return service.queryById(token, systemId, tenantId, taskId);
    }

    /** 编辑本人可维护的普通任务内容。 */
    @PutMapping("/{taskId}")
    @Operation(summary = "编辑工作任务")
    public WorkTaskService.TaskView edit(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.EditRequest request) {
        return service.edit(token, systemId, tenantId, taskId, request);
    }

    /** 将任务指派或转交给当前作用域的有效账号。 */
    @PostMapping("/{taskId}/transfer")
    @Operation(summary = "指派或转交工作任务")
    public WorkTaskService.TaskView transfer(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.TransferRequest request) {
        return service.transfer(token, systemId, tenantId, taskId, request);
    }

    /** 执行任务状态流转。 */
    @PostMapping("/{taskId}/status")
    @Operation(summary = "变更工作任务状态")
    public WorkTaskService.TaskView status(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.StatusRequest request) {
        return service.changeStatus(token, systemId, tenantId, taskId, request);
    }

    /** 查询当前任务的真实操作历史。 */
    @GetMapping("/{taskId}/history")
    @Operation(summary = "普通任务操作历史")
    public List<WorkTaskService.HistoryView> history(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId) {
        return service.history(token, systemId, tenantId, taskId);
    }

    /** 在当前任务的操作历史中写入评论。 */
    @PostMapping("/{taskId}/comments")
    @Operation(summary = "评论普通任务")
    public WorkTaskService.HistoryView comment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.CommentRequest request) {
        return service.comment(token, systemId, tenantId, taskId, request);
    }

    /** 添加当前作用域内的任务参与人。 */
    @PostMapping("/{taskId}/members")
    @Operation(summary = "添加任务协作、关注或验收成员")
    public WorkTaskService.MemberView addMember(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.MemberRequest request) {
        return service.addMember(token, systemId, tenantId, taskId, request);
    }

    /** 查询当前任务有效参与人。 */
    @GetMapping("/{taskId}/members")
    @Operation(summary = "任务参与人列表")
    public List<WorkTaskService.MemberView> members(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId) {
        return service.members(token, systemId, tenantId, taskId);
    }

    /** 停用一条参与关系。 */
    @DeleteMapping("/{taskId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "移除任务参与人")
    public void removeMember(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @PathVariable Long memberId) {
        service.removeMember(token, systemId, tenantId, taskId, memberId);
    }

    /** 负责人申请延后任务截止时间。 */
    @PostMapping("/{taskId}/defer-requests")
    @Operation(summary = "申请任务延期")
    public WorkTaskService.DeferView requestDefer(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.DeferRequest request) {
        return service.requestDefer(token, systemId, tenantId, taskId, request);
    }

    /** 查询当前任务的待处理延期申请。 */
    @GetMapping("/{taskId}/defer-requests/pending")
    @Operation(summary = "待处理延期申请")
    public List<WorkTaskService.DeferView> pendingDefer(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId) {
        return service.pendingDefer(token, systemId, tenantId, taskId);
    }

    /** 创建人审批当前延期申请。 */
    @PostMapping("/{taskId}/defer-requests/decision")
    @Operation(summary = "审批任务延期")
    public WorkTaskService.TaskView decideDefer(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId,
            @Valid @RequestBody WorkTaskService.DeferDecision request) {
        return service.decideDefer(token, systemId, tenantId, taskId, request);
    }

    /** 归档任务并保留操作历史。 */
    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "归档工作任务")
    public void archive(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @RequestParam(required = false) Long systemId, @PathVariable Long taskId) {
        service.archive(token, systemId, tenantId, taskId);
    }
}
