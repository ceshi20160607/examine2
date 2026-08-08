package com.unique.examine.work.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskReminder;
import com.unique.examine.work.domain.WorkTaskPage;
import com.unique.examine.work.domain.WorkDomainException;

import java.util.List;
import java.time.Instant;
import java.util.function.Function;

public final class WorkTaskApiModels {
    private WorkTaskApiModels() {
    }

    public record CreateTask(
            String title,
            long assigneeMemberId,
            Long projectId,
            String description,
            Instant dueAt,
            Instant reminderAt,
            JsonNode customFields
    ) {
        public CreateTask(String title, long assigneeMemberId) {
            this(title, assigneeMemberId, null, null, null, null, null);
        }

        public CreateTask(
                String title,
                long assigneeMemberId,
                Long projectId,
                String description,
                Instant dueAt
        ) {
            this(title, assigneeMemberId, projectId, description, dueAt, null, null);
        }

        public CreateTask(
                String title,
                long assigneeMemberId,
                Long projectId,
                String description,
                Instant dueAt,
                Instant reminderAt
        ) {
            this(title, assigneeMemberId, projectId, description, dueAt,
                    reminderAt, null);
        }
    }

    public record UpdateTask(
            String title,
            Long projectId,
            String description,
            Instant dueAt,
            long version,
            JsonNode reminderAt,
            JsonNode customFields
    ) {
        public UpdateTask(
                String title,
                Long projectId,
                String description,
                Instant dueAt,
                long version
        ) {
            this(title, projectId, description, dueAt, version, null, null);
        }

        public UpdateTask(
                String title,
                Long projectId,
                String description,
                Instant dueAt,
                long version,
                JsonNode reminderAt
        ) {
            this(title, projectId, description, dueAt, version, reminderAt, null);
        }

        boolean reminderSpecified() {
            return reminderAt != null;
        }

        Instant reminderValue() {
            if (reminderAt == null || reminderAt.isNull()) {
                return null;
            }
            if (!reminderAt.isTextual()) {
                throw invalidReminder();
            }
            try {
                return Instant.parse(reminderAt.textValue());
            } catch (java.time.format.DateTimeParseException failure) {
                throw invalidReminder();
            }
        }
    }

    public record ReminderVersion(long version) {
    }

    public record AssignTask(long assigneeMemberId) {
    }

    public record TaskPage(
            List<TaskView> items,
            int page,
            int size,
            long total
    ) {
        public TaskPage {
            items = List.copyOf(items);
        }

        static TaskPage from(WorkTaskPage page) {
            return from(page, ignored -> null);
        }

        static TaskPage from(
                WorkTaskPage page,
                Function<WorkTask, WorkTaskReminder> reminders
        ) {
            return from(page, reminders, ignored -> null);
        }

        static TaskPage from(
                WorkTaskPage page,
                Function<WorkTask, WorkTaskReminder> reminders,
                Function<WorkTask,
                        com.unique.examine.work.configuration.WorkConfigurationService.RuntimeView>
                        runtime
        ) {
            return new TaskPage(
                    page.items().stream()
                            .map(task -> TaskView.from(
                                    task, reminders.apply(task), runtime.apply(task)))
                            .toList(),
                    page.page(),
                    page.size(),
                    page.total());
        }
    }

    public record TaskView(
            String id,
            String systemId,
            String tenantId,
            String creatorMemberId,
            String assigneeMemberId,
            String title,
            String status,
            String createdAt,
            String updatedAt,
            long version,
            String projectId,
            String description,
            String dueAt,
            String reminderAt,
            ReminderView reminder,
            com.unique.examine.work.configuration.WorkConfigurationService.RuntimeView
                    runtime
    ) {
        static TaskView from(WorkTask task) {
            return from(task, null);
        }

        static TaskView from(
                WorkTask task, WorkTaskReminder reminder
        ) {
            return from(task, reminder, null);
        }

        static TaskView from(
                WorkTask task, WorkTaskReminder reminder,
                com.unique.examine.work.configuration.WorkConfigurationService.RuntimeView
                        runtime
        ) {
            return new TaskView(
                    Long.toString(task.id()),
                    Long.toString(task.systemId()),
                    Long.toString(task.tenantId()),
                    Long.toString(task.creatorMemberId()),
                    Long.toString(task.assigneeMemberId()),
                    task.title(),
                    task.status().name(),
                    task.createdAt().toString(),
                    task.updatedAt().toString(),
                    task.version(),
                    task.projectId() == null
                            ? null : Long.toString(task.projectId()),
                    task.description(),
                    task.dueAt() == null ? null : task.dueAt().toString(),
                    task.reminderAt() == null
                            ? null : task.reminderAt().toString(),
                    ReminderView.from(reminder), runtime);
        }
    }

    public record ReminderView(
            int generation,
            String scheduledAt,
            String status,
            int attemptCount,
            String sentAt,
            String failureCode,
            long version
    ) {
        static ReminderView from(WorkTaskReminder value) {
            if (value == null) {
                return null;
            }
            return new ReminderView(
                    value.generation(), value.scheduledAt().toString(),
                    value.status().name(), value.attemptCount(),
                    value.sentAt() == null ? null : value.sentAt().toString(),
                    value.failureCode(), value.version());
        }
    }

    private static WorkDomainException invalidReminder() {
        return new WorkDomainException(
                "WORK_TASK_REMINDER_INVALID",
                "reminderAt must be an ISO-8601 instant or null");
    }
}
