package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Exact safe native transport for self-owned platform tasks. */
public final class PlatformTaskApi {
    private PlatformTaskApi() {
    }

    public record Page(
            List<TaskView> items,
            int page,
            int size,
            long total
    ) {
        public Page {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (page < 1 || page > 10_000 || size < 1 || size > 100
                    || total < 0 || items.size() > size || items.size() > total) {
                throw new IllegalArgumentException("Platform task page is invalid");
            }
        }
    }

    public record TaskView(
            String taskId,
            String title,
            String description,
            Instant dueAt,
            PlatformTaskFacade.Priority priority,
            PlatformTaskFacade.Status status,
            PlatformTaskFacade.Source source,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            Instant cancelledAt,
            long version
    ) {
        public TaskView {
            taskId = positiveDecimal(taskId, "taskId");
            title = text(title, "title", 200, false);
            description = text(description, "description", 2_000, true);
            priority = Objects.requireNonNull(priority, "priority");
            status = Objects.requireNonNull(status, "status");
            source = Objects.requireNonNull(source, "source");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (version < 0 || updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("Task lifecycle is invalid");
            }
            var valid = switch (status) {
                case OPEN -> completedAt == null && cancelledAt == null;
                case COMPLETED -> completedAt != null && cancelledAt == null;
                case CANCELLED -> completedAt == null && cancelledAt != null;
            };
            if (!valid || terminalInvalid(completedAt, createdAt, updatedAt)
                    || terminalInvalid(cancelledAt, createdAt, updatedAt)) {
                throw new IllegalArgumentException("Task status facts are invalid");
            }
        }
    }

    /** Version zero is valid for the first transition from a newly created task. */
    public record VersionCommand(Long version) {
        public VersionCommand {
            if (version == null || version < 0) {
                throw new IllegalArgumentException("version is invalid");
            }
        }
    }

    private static boolean terminalInvalid(
            Instant value, Instant createdAt, Instant updatedAt) {
        return value != null
                && (value.isBefore(createdAt) || value.isAfter(updatedAt));
    }

    private static String text(
            String value, String name, int maximum, boolean nullable) {
        if (nullable && value == null) return null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }
}
