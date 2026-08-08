package com.unique.examine.todo.port;

import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoSourceSnapshot;

import java.util.List;

public interface TodoSourcePort {
    TodoItem.SourceType sourceType();

    List<TodoSourceSnapshot> loadOpen(TodoActor actor);

    TodoSourceReload reload(TodoActor actor, TodoSourceReference reference);
}
