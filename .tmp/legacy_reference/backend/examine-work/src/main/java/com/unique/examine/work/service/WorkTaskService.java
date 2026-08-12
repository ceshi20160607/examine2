package com.unique.examine.work.service;

import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskPage;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.domain.WorkTaskReminder;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.port.WorkProjectRepository;
import com.unique.examine.work.port.WorkTaskRepository;
import com.unique.examine.work.port.WorkTaskReminderRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

@Transactional
public class WorkTaskService {
    public static final String CREATE = "work.task.create";
    public static final String MANAGE = "work.task.manage";
    public static final String ACCESS = "work.task.access";

    private final WorkTaskRepository repository;
    private final WorkProjectRepository projects;
    private final WorkTaskReminderRepository reminders;
    private final WorkMemberDirectory members;
    private final Clock clock;

    public WorkTaskService(WorkTaskRepository repository, WorkMemberDirectory members, Clock clock) {
        this(repository, null, null, members, clock);
    }

    public WorkTaskService(
            WorkTaskRepository repository,
            WorkProjectRepository projects,
            WorkMemberDirectory members,
            Clock clock
    ) {
        this(repository, projects, null, members, clock);
    }

    public WorkTaskService(
            WorkTaskRepository repository,
            WorkProjectRepository projects,
            WorkTaskReminderRepository reminders,
            WorkMemberDirectory members,
            Clock clock
    ) {
        this.repository = required(repository, "repository");
        this.projects = projects;
        this.reminders = reminders;
        this.members = required(members, "member directory");
        this.clock = required(clock, "clock");
    }

    public WorkTask create(WorkActor actor, String title, long assigneeMemberId) {
        return create(actor, title, assigneeMemberId, null, null, null);
    }

    public WorkTask create(
            WorkActor actor,
            String title,
            long assigneeMemberId,
            Long projectId,
            String description,
            Instant dueAt
    ) {
        return create(
                actor, title, assigneeMemberId, projectId, description,
                dueAt, null);
    }

    public WorkTask create(
            WorkActor actor,
            String title,
            long assigneeMemberId,
            Long projectId,
            String description,
            Instant dueAt,
            Instant reminderAt
    ) {
        requirePermission(actor, CREATE);
        requireActiveMember(actor, assigneeMemberId);
        if (projectId != null) {
            var project = visibleProject(actor, projectId);
            requireActiveProject(project.status());
            requireActiveProjectMember(actor, projectId, assigneeMemberId);
        }
        var now = Instant.now(clock);
        if (reminderAt != null) {
            WorkTask.requireReminderSchedule(reminderAt, dueAt, now);
            requireReminderRepository();
        }
        var task = repository.save(new WorkTask(repository.nextId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), assigneeMemberId, title, WorkTask.Status.OPEN,
                now, now, 1, projectId, description, dueAt, reminderAt));
        if (reminderAt != null) {
            reminders.schedule(
                    task.systemId(), task.tenantId(), task.id(),
                    reminderAt, now);
        }
        return task;
    }

    @Transactional(readOnly = true)
    public List<WorkTask> list(WorkActor actor) {
        return actor.has(MANAGE)
                ? repository.findAll(actor.systemId(), actor.tenantId())
                : repository.findParticipating(actor.systemId(), actor.tenantId(), actor.memberId());
    }

    @Transactional(readOnly = true)
    public WorkTaskPage page(WorkActor actor, WorkTaskQuery query) {
        requirePermission(actor, ACCESS);
        if (query == null) {
            throw new IllegalArgumentException("query is required");
        }
        if (query.role() == WorkTaskQuery.RoleFilter.ALL && !actor.has(MANAGE)) {
            throw error(
                    "WORK_TASK_FORBIDDEN",
                    "The ALL task role requires " + MANAGE);
        }
        if (query.projectId() != null) {
            visibleProject(actor, query.projectId());
        }
        return repository.findPage(
                actor.systemId(),
                actor.tenantId(),
                actor.memberId(),
                query);
    }

    public WorkTask assign(WorkActor actor, long taskId, long assigneeMemberId) {
        var task = scopedTask(actor, taskId);
        if (task.status() != WorkTask.Status.OPEN) {
            throw error("WORK_TASK_STATE_INVALID", "Only open tasks can be assigned");
        }
        if (actor.memberId() != task.creatorMemberId() && !actor.has(MANAGE)) {
            throw error("WORK_TASK_FORBIDDEN", "Only the creator or a task manager can assign this task");
        }
        requireActiveMember(actor, assigneeMemberId);
        if (task.projectId() != null) {
            var project = visibleProject(actor, task.projectId());
            requireActiveProject(project.status());
            requireActiveProjectMember(
                    actor, task.projectId(), assigneeMemberId);
        }
        if (task.assigneeMemberId() == assigneeMemberId) {
            return task;
        }
        return repository.save(task.assign(assigneeMemberId, Instant.now(clock)));
    }

    public WorkTask complete(WorkActor actor, long taskId) {
        var task = scopedTask(actor, taskId);
        if (task.status() != WorkTask.Status.OPEN) {
            throw error("WORK_TASK_STATE_INVALID", "Only open tasks can be completed");
        }
        if (actor.memberId() != task.assigneeMemberId() && !actor.has(MANAGE)) {
            throw error("WORK_TASK_FORBIDDEN", "Only the assignee or a task manager can complete this task");
        }
        var now = Instant.now(clock);
        var completed = repository.save(task.complete(now));
        if (task.reminderAt() != null && reminders != null) {
            reminders.cancelLatestLive(
                    task.systemId(), task.tenantId(), task.id(), now);
        }
        return completed;
    }

    public WorkTask reopen(WorkActor actor, long taskId) {
        var task = scopedTask(actor, taskId);
        if (task.status() != WorkTask.Status.COMPLETED) {
            throw error("WORK_TASK_STATE_INVALID", "Only completed tasks can be reopened");
        }
        if (actor.memberId() != task.creatorMemberId() && !actor.has(MANAGE)) {
            throw error("WORK_TASK_FORBIDDEN", "Only the creator or a task manager can reopen this task");
        }
        return repository.save(task.reopen(Instant.now(clock)));
    }

    public WorkTask get(WorkActor actor, long taskId) {
        var task = scopedTask(actor, taskId);
        if (actor.memberId() != task.creatorMemberId()
                && actor.memberId() != task.assigneeMemberId()
                && !actor.has(MANAGE)
                && task.projectId() == null) {
            throw error("WORK_TASK_NOT_FOUND", "Task was not found");
        }
        return task;
    }

    public WorkTask updateMetadata(
            WorkActor actor,
            long taskId,
            String title,
            Long projectId,
            String description,
            Instant dueAt,
            long expectedVersion
    ) {
        return updateMetadata(
                actor, taskId, title, projectId, description, dueAt,
                null, false, expectedVersion);
    }

    public WorkTask updateMetadata(
            WorkActor actor,
            long taskId,
            String title,
            Long projectId,
            String description,
            Instant dueAt,
            Instant reminderAt,
            boolean reminderSpecified,
            long expectedVersion
    ) {
        var task = scopedTask(actor, taskId);
        if (expectedVersion <= 0 || task.version() != expectedVersion) {
            throw error("WORK_TASK_VERSION_CONFLICT", "Task version is stale");
        }
        var nextReminderAt = reminderSpecified
                ? reminderAt : task.reminderAt();
        if (nextReminderAt != null
                && task.status() != WorkTask.Status.OPEN) {
            throw error("WORK_TASK_STATE_INVALID",
                    "Only an open task can have a reminder");
        }
        var now = Instant.now(clock);
        var candidate = task.reviseMetadata(
                title, description, projectId, dueAt,
                nextReminderAt, now);
        var otherMetadataChanged = !candidate.title().equals(task.title())
                || !Objects.equals(candidate.projectId(), task.projectId())
                || !Objects.equals(candidate.description(), task.description())
                || !Objects.equals(candidate.dueAt(), task.dueAt());
        var metadataManager = actor.memberId() == task.creatorMemberId()
                || actor.has(MANAGE);
        var reminderManager = metadataManager
                || actor.memberId() == task.assigneeMemberId();
        if (!metadataManager
                && (!reminderSpecified || !reminderManager
                || otherMetadataChanged)) {
            throw error("WORK_TASK_FORBIDDEN",
                    "Only the creator or a task manager can update task metadata");
        }
        if (projectId != null) {
            var project = visibleProject(actor, projectId);
            if (!projectId.equals(task.projectId())) {
                requireActiveProject(project.status());
                requireActiveProjectMember(
                        actor, projectId, task.assigneeMemberId());
            }
        }
        var reminderChanged = !Objects.equals(
                task.reminderAt(), nextReminderAt);
        if (reminderChanged) {
            requireReminderRepository();
        }
        var saved = repository.save(candidate);
        if (reminderChanged && nextReminderAt == null) {
            reminders.cancelLatestLive(
                    task.systemId(), task.tenantId(), task.id(), now);
        } else if (reminderChanged) {
            reminders.schedule(
                    task.systemId(), task.tenantId(), task.id(),
                    nextReminderAt, now);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<WorkTaskReminder> latestReminder(
            WorkActor actor, long taskId
    ) {
        var task = get(actor, taskId);
        if (reminders == null) {
            return Optional.empty();
        }
        return reminders.findLatest(
                task.systemId(), task.tenantId(), task.id());
    }

    public WorkTaskReminder retryReminder(
            WorkActor actor,
            long taskId,
            long expectedReminderVersion
    ) {
        requirePermission(actor, MANAGE);
        var task = scopedTask(actor, taskId);
        requireReminderRepository();
        var reminder = reminders.findLatest(
                        task.systemId(), task.tenantId(), task.id())
                .orElseThrow(() -> error(
                        "WORK_TASK_REMINDER_STATE_INVALID",
                        "Task has no reminder generation to retry"));
        if (expectedReminderVersion <= 0
                || reminder.version() != expectedReminderVersion) {
            throw error("WORK_TASK_REMINDER_VERSION_CONFLICT",
                    "Reminder version is stale");
        }
        return reminders.save(reminder.retry(Instant.now(clock)));
    }

    private WorkTask scopedTask(WorkActor actor, long taskId) {
        var task = repository.findById(actor.systemId(), actor.tenantId(), taskId)
                .orElseThrow(() -> error("WORK_TASK_NOT_FOUND", "Task was not found"));
        if (task.projectId() != null) {
            visibleProject(actor, task.projectId());
        }
        return task;
    }

    private com.unique.examine.work.domain.WorkProject visibleProject(
            WorkActor actor, long projectId
    ) {
        if (projects == null) {
            throw error("WORK_PROJECT_NOT_FOUND", "Project was not found");
        }
        return projects.findVisibleById(
                        actor.systemId(), actor.tenantId(), projectId,
                        actor.memberId(), actor.has(WorkProjectService.MANAGE))
                .orElseThrow(() -> error(
                        "WORK_PROJECT_NOT_FOUND", "Project was not found"));
    }

    private void requireActiveProjectMember(
            WorkActor actor, long projectId, long memberId
    ) {
        var projectMember = projects.findMember(
                        actor.systemId(), actor.tenantId(),
                        projectId, memberId)
                .orElse(null);
        if (projectMember == null
                || projectMember.status()
                != com.unique.examine.work.domain.WorkProjectMember.Status.ACTIVE) {
            throw error("WORK_PROJECT_ASSIGNEE_INVALID",
                    "Assignee must be an active member of the project");
        }
    }

    private static void requireActiveProject(
            com.unique.examine.work.domain.WorkProject.Status status
    ) {
        if (status != com.unique.examine.work.domain.WorkProject.Status.ACTIVE) {
            throw error("WORK_PROJECT_STATE_INVALID",
                    "New task assignment into an archived project is rejected");
        }
    }

    private void requireActiveMember(WorkActor actor, long memberId) {
        if (memberId <= 0 || !members.isActiveMember(actor.systemId(), actor.tenantId(), memberId)) {
            throw error("WORK_ASSIGNEE_INVALID", "Assignee is not an active member of this tenant");
        }
    }

    private void requireReminderRepository() {
        if (reminders == null) {
            throw new IllegalStateException(
                    "Work task reminder repository is required");
        }
    }

    private static void requirePermission(WorkActor actor, String permission) {
        if (!actor.has(permission)) {
            throw error("WORK_TASK_FORBIDDEN", "Missing permission: " + permission);
        }
    }

    private static WorkDomainException error(String code, String message) {
        return new WorkDomainException(code, message);
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
