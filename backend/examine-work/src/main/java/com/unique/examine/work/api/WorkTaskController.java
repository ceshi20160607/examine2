package com.unique.examine.work.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.configuration.WorkConfiguration;
import com.unique.examine.work.configuration.WorkConfigurationService;
import com.unique.examine.work.service.WorkTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/work/tasks")
public class WorkTaskController {
    private final WorkTaskService service;
    private final WorkConfigurationService configurations;

    public WorkTaskController(WorkTaskService service) {
        this(service, null);
    }

    @Autowired
    public WorkTaskController(
            WorkTaskService service,
            WorkConfigurationService configurations) {
        this.service = service;
        this.configurations = configurations;
    }

    @GetMapping
    public ApiResponse<WorkTaskApiModels.TaskPage> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") WorkTaskQuery.StatusFilter status,
            @RequestParam(defaultValue = "PARTICIPATING") WorkTaskQuery.RoleFilter role,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Instant dueFrom,
            @RequestParam(required = false) Instant dueTo,
            @RequestParam(required = false) Instant dueBefore,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(required = false) Instant updatedFrom,
            @RequestParam(required = false) Instant updatedBefore,
            @RequestParam(required = false) Long assigneeMemberId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var result = service.page(
                actor,
                new WorkTaskQuery(
                        keyword, status, role, page, size,
                        projectId, dueFrom, dueTo, null, null,
                        dueBefore, createdFrom, createdBefore,
                        updatedFrom, updatedBefore, assigneeMemberId));
        return success(WorkTaskApiModels.TaskPage.from(
                result,
                task -> service.latestReminder(actor, task.id())
                        .orElse(null),
                task -> runtime(actor, task)), request);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<WorkTaskApiModels.TaskView>> create(
            @PathVariable long systemId,
            @RequestBody WorkTaskApiModels.CreateTask body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var prepared = configurations == null ? null
                : configurations.prepareCreate(actor, objectType(body.projectId()),
                body.customFields());
        var created = service.create(
                actor, body.title(), body.assigneeMemberId(),
                body.projectId(), body.description(), body.dueAt(),
                body.reminderAt());
        if (configurations != null) {
            configurations.saveValues(actor, objectType(created.projectId()),
                    created.id(), prepared);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(view(actor, created), request));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<WorkTaskApiModels.TaskView> get(
            @PathVariable long systemId,
            @PathVariable long taskId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var task = service.get(actor, taskId);
        return success(view(actor, task), request);
    }

    @PutMapping("/{taskId}")
    @Transactional
    public ApiResponse<WorkTaskApiModels.TaskView> update(
            @PathVariable long systemId,
            @PathVariable long taskId,
            @RequestBody WorkTaskApiModels.UpdateTask body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var prepared = configurations == null ? null
                : configurations.prepareUpdate(actor, objectType(body.projectId()),
                taskId, body.customFields());
        var task = service.updateMetadata(
                actor,
                taskId, body.title(), body.projectId(), body.description(),
                body.dueAt(), body.reminderValue(), body.reminderSpecified(),
                body.version());
        if (configurations != null) {
            configurations.saveValues(actor, objectType(task.projectId()),
                    task.id(), prepared);
        }
        return success(view(actor, task), request);
    }

    @PostMapping("/{taskId}:assign")
    public ApiResponse<WorkTaskApiModels.TaskView> assign(
            @PathVariable long systemId,
            @PathVariable long taskId,
            @RequestBody WorkTaskApiModels.AssignTask body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var task = service.assign(actor, taskId, body.assigneeMemberId());
        return success(view(actor, task), request);
    }

    @PostMapping("/{taskId}:complete")
    public ApiResponse<WorkTaskApiModels.TaskView> complete(
            @PathVariable long systemId,
            @PathVariable long taskId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var task = service.complete(actor, taskId);
        return success(view(actor, task), request);
    }

    @PostMapping("/{taskId}:reopen")
    public ApiResponse<WorkTaskApiModels.TaskView> reopen(
            @PathVariable long systemId,
            @PathVariable long taskId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var task = service.reopen(actor, taskId);
        return success(view(actor, task), request);
    }

    @PostMapping("/{taskId}/reminder:retry")
    public ApiResponse<WorkTaskApiModels.TaskView> retryReminder(
            @PathVariable long systemId,
            @PathVariable long taskId,
            @RequestBody WorkTaskApiModels.ReminderVersion body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = WorkRequestSession.require(session, systemId);
        var reminder = service.retryReminder(
                actor, taskId, body.version());
        var task = service.get(actor, taskId);
        return success(
                WorkTaskApiModels.TaskView.from(task, reminder), request);
    }

    private WorkTaskApiModels.TaskView view(
            com.unique.examine.work.domain.WorkActor actor,
            com.unique.examine.work.domain.WorkTask task
    ) {
        return WorkTaskApiModels.TaskView.from(
                task,
                service.latestReminder(actor, task.id()).orElse(null),
                runtime(actor, task));
    }

    private WorkConfigurationService.RuntimeView runtime(
            com.unique.examine.work.domain.WorkActor actor,
            com.unique.examine.work.domain.WorkTask task) {
        return configurations == null ? null : configurations.runtimeView(
                actor, objectType(task.projectId()), task.id());
    }

    private static WorkConfiguration.ObjectType objectType(Long projectId) {
        return projectId == null
                ? WorkConfiguration.ObjectType.ORDINARY_TASK
                : WorkConfiguration.ObjectType.PROJECT_TASK;
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
