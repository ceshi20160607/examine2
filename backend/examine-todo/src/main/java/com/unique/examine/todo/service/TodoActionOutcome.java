package com.unique.examine.todo.service;

import com.unique.examine.todo.domain.TodoActionLog;
import com.unique.examine.todo.domain.TodoItem;

public record TodoActionOutcome(
        TodoActionLog.ResultCode code,
        TodoActionLog log,
        TodoItem item,
        boolean replayed
) {
    public TodoActionOutcome {
        if (code == null || log == null || item == null) {
            throw new IllegalArgumentException("Todo action outcome is incomplete");
        }
    }
}
