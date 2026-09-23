package com.unique.unexamine.work.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 工作项目与任务主链路接口。 */
@RestController
@RequestMapping("/api/work")
public class WorkManagementController {
    private final WorkManagementService service;

    public WorkManagementController(WorkManagementService service) {
        this.service = service;
    }

    @GetMapping("/projects")
    public ApiResult<List<WorkManagementModels.ProjectView>> projects() {
        return ApiResult.ok(service.projects(AuthenticationContextHolder.require()));
    }

    @PostMapping("/projects")
    public ApiResult<WorkManagementModels.ProjectView> createProject(
            @Valid @RequestBody WorkManagementModels.CreateProjectRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.createProject(AuthenticationContextHolder.require(), body, TraceIdFilter.current(request)));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResult<WorkManagementModels.ProjectView> project(@PathVariable Long projectId) {
        return ApiResult.ok(service.project(AuthenticationContextHolder.require(), projectId));
    }

    @PutMapping("/projects/{projectId}")
    public ApiResult<WorkManagementModels.ProjectView> updateProject(
            @PathVariable Long projectId, @Valid @RequestBody WorkManagementModels.UpdateProjectRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.updateProject(AuthenticationContextHolder.require(), projectId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/projects/{projectId}/groups")
    public ApiResult<WorkManagementModels.ProjectView> createTaskGroup(
            @PathVariable Long projectId, @Valid @RequestBody WorkManagementModels.CreateTaskGroupRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.createTaskGroup(AuthenticationContextHolder.require(), projectId, body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/projects/{projectId}/groups/{groupId}")
    public ApiResult<WorkManagementModels.ProjectView> updateTaskGroup(
            @PathVariable Long projectId, @PathVariable Long groupId,
            @Valid @RequestBody WorkManagementModels.UpdateTaskGroupRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.updateTaskGroup(AuthenticationContextHolder.require(), projectId, groupId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ApiResult<WorkManagementModels.TaskView> createTask(
            @PathVariable Long projectId, @Valid @RequestBody WorkManagementModels.CreateTaskRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.createTask(AuthenticationContextHolder.require(), projectId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/tasks")
    public ApiResult<List<WorkManagementModels.TaskView>> ordinaryTasks() {
        return ApiResult.ok(service.ordinaryTasks(AuthenticationContextHolder.require()));
    }

    @PostMapping("/tasks")
    public ApiResult<WorkManagementModels.TaskView> createOrdinaryTask(
            @Valid @RequestBody WorkManagementModels.CreateTaskRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.createOrdinaryTask(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResult<WorkManagementModels.TaskView> task(@PathVariable Long taskId) {
        return ApiResult.ok(service.task(AuthenticationContextHolder.require(), taskId));
    }

    @PutMapping("/tasks/{taskId}")
    public ApiResult<WorkManagementModels.TaskView> updateTask(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.UpdateTaskRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.updateTask(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    public ApiResult<WorkManagementModels.TaskView> transferTask(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.TransferTaskRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.transferTask(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/delay")
    public ApiResult<WorkManagementModels.TaskView> delayTask(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.DelayTaskRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.delayTask(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/submit-completion")
    public ApiResult<WorkManagementModels.TaskView> submitCompletion(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.SubmitCompletionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.submitCompletion(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/accept-completion")
    public ApiResult<WorkManagementModels.TaskView> acceptCompletion(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.TaskReasonRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.acceptCompletion(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/reopen")
    public ApiResult<WorkManagementModels.TaskView> reopenTask(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.TaskReasonRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.reopenTask(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/archive")
    public ApiResult<WorkManagementModels.TaskView> archiveTask(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.TaskReasonRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.archiveTask(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/delete")
    public ApiResult<Boolean> deleteTask(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.TaskReasonRequest body,
            HttpServletRequest request) {
        service.deleteTask(AuthenticationContextHolder.require(), taskId, body, TraceIdFilter.current(request));
        return ApiResult.ok(true);
    }

    @PostMapping("/tasks/{taskId}/comments")
    public ApiResult<WorkManagementModels.TaskView> addComment(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.AddCommentRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.addComment(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/progress")
    public ApiResult<WorkManagementModels.TaskView> recordProgress(
            @PathVariable Long taskId, @Valid @RequestBody WorkManagementModels.RecordProgressRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.recordProgress(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }
}
