package com.unique.examine.web.todo;

import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoIdentity;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoSourceSnapshot;
import com.unique.examine.todo.port.TodoActionPort;
import com.unique.examine.todo.port.TodoSourceActionCommand;
import com.unique.examine.todo.port.TodoSourceActionResult;
import com.unique.examine.todo.port.TodoSourcePort;
import com.unique.examine.todo.port.TodoSourceReference;
import com.unique.examine.todo.port.TodoSourceReload;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.service.WorkTaskService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Composes Work's public task service into Todo without owning task facts. */
@Component
public final class WorkTodoAdapter implements TodoSourcePort, TodoActionPort {
    private static final String ACTION_SCOPE = "COMPLETE";
    private static final int SOURCE_PAGE_SIZE = 100;
    private static final int PRIORITY = 100;

    private final WorkTaskService tasks;

    public WorkTodoAdapter(WorkTaskService tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("Work task service is required");
        }
        this.tasks = tasks;
    }

    @Override
    public TodoItem.SourceType sourceType() {
        return TodoItem.SourceType.WORK_TASK;
    }

    @Override
    public List<TodoSourceSnapshot> loadOpen(TodoActor actor) {
        if (!actor.has(WorkTaskService.ACCESS)) {
            return List.of();
        }
        var workActor = workActor(actor);
        var result = new ArrayList<TodoSourceSnapshot>();
        var page = 1;
        while (true) {
            var current = tasks.page(
                    workActor,
                    new WorkTaskQuery(
                            "", WorkTaskQuery.StatusFilter.OPEN,
                            WorkTaskQuery.RoleFilter.ASSIGNED_TO_ME,
                            page, SOURCE_PAGE_SIZE));
            current.items().stream()
                    .filter(task -> task.assigneeMemberId() == actor.memberId())
                    .map(task -> snapshot(actor, task))
                    .forEach(result::add);
            if ((long) page * SOURCE_PAGE_SIZE >= current.total()) {
                return List.copyOf(result);
            }
            page++;
        }
    }

    @Override
    public TodoSourceReload reload(
            TodoActor actor,
            TodoSourceReference reference
    ) {
        if (reference.sourceType() != sourceType()
                || !ACTION_SCOPE.equals(reference.actionScope())
                || reference.representedMemberId() != null) {
            return TodoSourceReload.of(TodoSourceReload.Status.INELIGIBLE);
        }
        if (!actor.has(WorkTaskService.ACCESS)) {
            return TodoSourceReload.of(TodoSourceReload.Status.DENIED);
        }
        var taskId = positiveId(reference.sourceId());
        if (taskId == null) {
            return TodoSourceReload.of(TodoSourceReload.Status.MISSING);
        }
        final WorkTask task;
        try {
            task = tasks.get(workActor(actor), taskId);
        } catch (WorkDomainException failure) {
            return TodoSourceReload.of(reloadFailure(failure));
        }
        if (task.assigneeMemberId() != actor.memberId()) {
            return TodoSourceReload.of(TodoSourceReload.Status.INELIGIBLE);
        }
        if (task.status() != WorkTask.Status.OPEN) {
            return TodoSourceReload.of(TodoSourceReload.Status.COMPLETED);
        }
        if (task.version() != reference.sourceVersion()) {
            return TodoSourceReload.of(TodoSourceReload.Status.STALE);
        }
        return TodoSourceReload.live(snapshot(actor, task));
    }

    @Override
    public TodoSourceActionResult execute(TodoSourceActionCommand command) {
        if (command.sourceType() != sourceType()
                || command.action() != TodoItem.ActionCode.COMPLETE
                || !ACTION_SCOPE.equals(command.actionScope())
                || command.representedMemberId() != null
                || !command.permissions().contains(WorkTaskService.ACCESS)) {
            return result(
                    TodoSourceActionResult.Code.DENIED,
                    command.expectedSourceVersion(),
                    "Work task completion is not available");
        }
        var taskId = positiveId(command.sourceId());
        if (taskId == null) {
            return result(
                    TodoSourceActionResult.Code.STALE,
                    command.expectedSourceVersion(),
                    "Work task is no longer available");
        }
        var actor = new WorkActor(
                command.systemId(), command.tenantId(),
                command.actorMemberId(), command.permissions());
        final WorkTask current;
        try {
            current = tasks.get(actor, taskId);
        } catch (WorkDomainException failure) {
            return actionFailure(failure, command.expectedSourceVersion());
        }
        if (current.version() != command.expectedSourceVersion()
                || current.status() != WorkTask.Status.OPEN) {
            return result(
                    TodoSourceActionResult.Code.STALE,
                    current.version(),
                    "Work task changed before completion");
        }
        if (current.assigneeMemberId() != command.actorMemberId()) {
            return result(
                    TodoSourceActionResult.Code.DENIED,
                    current.version(),
                    "Work task is no longer assigned to this member");
        }
        try {
            var completed = tasks.complete(actor, taskId);
            return result(
                    TodoSourceActionResult.Code.SUCCESS,
                    completed.version(),
                    "Work task completed");
        } catch (WorkDomainException failure) {
            return actionFailure(failure, current.version());
        }
    }

    private static TodoSourceSnapshot snapshot(
            TodoActor actor,
            WorkTask task
    ) {
        return new TodoSourceSnapshot(
                new TodoIdentity(
                        actor.systemId(), actor.tenantId(), actor.memberId(),
                        TodoItem.SourceType.WORK_TASK,
                        Long.toString(task.id()), ACTION_SCOPE),
                task.version(), TodoItem.Category.TASK, PRIORITY,
                task.title(), task.dueAt(),
                "/systems/" + actor.systemId()
                        + "/tasks?taskId=" + task.id(),
                Set.of(TodoItem.ActionCode.COMPLETE), null);
    }

    private static WorkActor workActor(TodoActor actor) {
        return new WorkActor(
                actor.systemId(), actor.tenantId(), actor.memberId(),
                actor.permissions());
    }

    private static Long positiveId(String value) {
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static TodoSourceReload.Status reloadFailure(
            WorkDomainException failure
    ) {
        return switch (failure.code()) {
            case "WORK_TASK_FORBIDDEN" -> TodoSourceReload.Status.DENIED;
            case "WORK_TASK_NOT_FOUND" -> TodoSourceReload.Status.MISSING;
            default -> TodoSourceReload.Status.STALE;
        };
    }

    private static TodoSourceActionResult actionFailure(
            WorkDomainException failure,
            long sourceVersion
    ) {
        var code = switch (failure.code()) {
            case "WORK_TASK_FORBIDDEN" -> TodoSourceActionResult.Code.DENIED;
            case "WORK_TASK_VERSION_CONFLICT" ->
                    TodoSourceActionResult.Code.CONFLICT;
            case "WORK_TASK_NOT_FOUND", "WORK_TASK_STATE_INVALID" ->
                    TodoSourceActionResult.Code.STALE;
            default -> TodoSourceActionResult.Code.FAILED;
        };
        return result(code, sourceVersion, switch (code) {
            case DENIED -> "Work task completion is no longer authorized";
            case CONFLICT -> "Work task was changed concurrently";
            case STALE -> "Work task is no longer actionable";
            default -> "Work task completion failed";
        });
    }

    private static TodoSourceActionResult result(
            TodoSourceActionResult.Code code,
            long sourceVersion,
            String message
    ) {
        return new TodoSourceActionResult(code, sourceVersion, message);
    }
}
