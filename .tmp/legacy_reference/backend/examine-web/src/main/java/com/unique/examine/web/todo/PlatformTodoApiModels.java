package com.unique.examine.web.todo;

import java.time.Instant;
import java.util.List;

public final class PlatformTodoApiModels {
    private PlatformTodoApiModels() {
    }

    public enum StateFilter { ALL, OPEN, CLOSED }

    public enum TypeFilter { ALL, TASK, TODAY, REMINDER, APPROVAL, FAILED }

    public enum Action { COMPLETE, REOPEN, CANCEL }

    public record TodoView(
            String id,
            String context,
            String source,
            String category,
            String title,
            String description,
            String priority,
            String state,
            String sourceStatus,
            String dueAt,
            String routeHint,
            List<Action> availableActions,
            String createdAt,
            String updatedAt,
            long version) {
        public TodoView {
            availableActions = List.copyOf(availableActions);
        }
    }

    public record Page(List<TodoView> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record Counts(long open, long closed, long total, long today,
                         long reminders, long approvals, long failures) {
        public Counts(long open, long closed, long total) {
            this(open, closed, total, 0, 0, 0, 0);
        }
    }

    public record ActionBody(Action action, long version) {
    }

    public record ActionResult(TodoView todo, boolean replayed) {
    }
}
