package com.unique.examine.todo.config;

import com.unique.examine.core.id.IdService;
import com.unique.examine.todo.adapter.jdbc.JdbcTodoRepository;
import com.unique.examine.todo.port.TodoActionPort;
import com.unique.examine.todo.port.TodoRepository;
import com.unique.examine.todo.port.TodoSourcePort;
import com.unique.examine.todo.service.TodoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class TodoJdbcConfiguration {
    @Bean
    TodoRepository todoRepository(JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcTodoRepository(jdbcTemplate, idService);
    }

    @Bean
    TodoService todoService(
            TodoRepository repository,
            List<TodoSourcePort> sources,
            List<TodoActionPort> actions
    ) {
        return new TodoService(repository, sources, actions, Clock.systemUTC());
    }
}
