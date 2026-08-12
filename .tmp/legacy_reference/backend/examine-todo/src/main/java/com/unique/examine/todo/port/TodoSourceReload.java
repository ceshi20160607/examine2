package com.unique.examine.todo.port;

import com.unique.examine.todo.domain.TodoSourceSnapshot;

public record TodoSourceReload(Status status, TodoSourceSnapshot snapshot) {
    public enum Status { LIVE, STALE, COMPLETED, MISSING, INELIGIBLE, DENIED }

    public TodoSourceReload {
        if (status == null || status == Status.LIVE && snapshot == null
                || status != Status.LIVE && snapshot != null) {
            throw new IllegalArgumentException("Todo source reload result is inconsistent");
        }
    }

    public static TodoSourceReload live(TodoSourceSnapshot snapshot) {
        return new TodoSourceReload(Status.LIVE, snapshot);
    }

    public static TodoSourceReload of(Status status) {
        return new TodoSourceReload(status, null);
    }
}
