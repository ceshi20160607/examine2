package com.unique.examine.web.todo;

import com.unique.examine.todo.domain.TodoActionLog;
import com.unique.examine.todo.domain.TodoCounts;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoPage;
import com.unique.examine.todo.service.TodoActionOutcome;
import com.unique.examine.todo.service.TodoDetailResult;
import com.unique.examine.todo.service.TodoRefreshResult;

import java.util.List;

public final class TodoApiModels {
    private TodoApiModels() {
    }

    public record ActionBody(
            long version,
            TodoItem.ActionCode action,
            String comment,
            String reason
    ) {
    }

    public record Item(
            String id,
            String systemId,
            String tenantId,
            String recipientMemberId,
            String sourceType,
            String sourceId,
            String actionScope,
            long sourceVersion,
            String category,
            int priority,
            String title,
            String dueAt,
            String routeHint,
            List<String> availableActions,
            String representedMemberId,
            String status,
            String closeReason,
            String createdAt,
            String updatedAt,
            String closedAt,
            long version
    ) {
        static Item from(TodoItem value) {
            return new Item(
                    Long.toString(value.id()),
                    Long.toString(value.identity().systemId()),
                    Long.toString(value.identity().tenantId()),
                    Long.toString(value.identity().recipientMemberId()),
                    value.identity().sourceType().name(),
                    value.identity().sourceId(),
                    value.identity().actionScope(), value.sourceVersion(),
                    value.category().name(), value.priority(), value.title(),
                    instant(value.dueAt()), value.routeHint(),
                    value.availableActions().stream()
                            .map(Enum::name).sorted().toList(),
                    value.representedMemberId() == null
                            ? null
                            : Long.toString(value.representedMemberId()),
                    value.state().name(),
                    value.closeReason() == null
                            ? null : value.closeReason().name(),
                    value.createdAt().toString(), value.updatedAt().toString(),
                    instant(value.closedAt()), value.version());
        }
    }

    public record Page(
            List<Item> items,
            int page,
            int size,
            long total
    ) {
        static Page from(TodoPage value) {
            return new Page(
                    value.items().stream().map(Item::from).toList(),
                    value.page(), value.size(), value.total());
        }
    }

    public record Counts(
            long openCount,
            long taskCount,
            long approvalCount,
            long todayCount,
            long overdueCount
    ) {
        static Counts from(TodoCounts value) {
            return new Counts(
                    value.open(), value.task(), value.approval(),
                    value.today(), value.overdue());
        }
    }

    public record Refresh(
            int discovered,
            int created,
            int updated,
            int closed
    ) {
        static Refresh from(TodoRefreshResult value) {
            return new Refresh(
                    value.discovered(), value.created(),
                    value.updated(), value.closed());
        }
    }

    public record Detail(String status, Item todo) {
        static Detail from(TodoDetailResult value) {
            return new Detail(value.code().name(), Item.from(value.item()));
        }
    }

    public record SourceResult(
            String code,
            String message,
            long sourceVersion
    ) {
        static SourceResult from(TodoActionLog value) {
            return new SourceResult(
                    value.resultCode() == null
                            ? TodoActionLog.ResultCode.IN_PROGRESS.name()
                            : value.resultCode().name(),
                    value.resultMessage(), value.sourceVersion());
        }
    }

    public record ActionResult(
            String status,
            boolean replayed,
            Item todo,
            SourceResult sourceResult
    ) {
        static ActionResult from(TodoActionOutcome value) {
            return new ActionResult(
                    value.code().name(), value.replayed(),
                    Item.from(value.item()), SourceResult.from(value.log()));
        }
    }

    private static String instant(java.time.Instant value) {
        return value == null ? null : value.toString();
    }
}
