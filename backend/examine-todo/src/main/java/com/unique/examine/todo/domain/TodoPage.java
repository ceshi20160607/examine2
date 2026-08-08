package com.unique.examine.todo.domain;

import java.util.List;

public record TodoPage(List<TodoItem> items, int page, int size, long total) {
    public TodoPage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("Todo page metadata is invalid");
        }
    }
}
