package com.unique.examine.todo.port;

import com.unique.examine.todo.domain.TodoItem;

public interface TodoActionPort {
    TodoItem.SourceType sourceType();

    TodoSourceActionResult execute(TodoSourceActionCommand command);
}
