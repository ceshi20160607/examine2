package com.unique.examine.todo.service;

import com.unique.examine.todo.domain.TodoItem;

public record TodoDetailResult(Code code, TodoItem item) {
    public enum Code { LIVE, STALE, DENIED }
    public TodoDetailResult {
        if (code == null || item == null) {
            throw new IllegalArgumentException("Todo detail result is incomplete");
        }
    }
}
