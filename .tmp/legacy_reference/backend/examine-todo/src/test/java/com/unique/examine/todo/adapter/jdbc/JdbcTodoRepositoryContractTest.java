package com.unique.examine.todo.adapter.jdbc;

import com.unique.examine.todo.domain.TodoQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTodoRepositoryContractTest {
    @Test
    void itemAndActionSqlFreezeScopeCasIdentityAndTerminalGuards() {
        assertThat(JdbcTodoRepository.FIND_IDENTITY)
                .contains("system_id=?", "tenant_id=?", "recipient_member_id=?")
                .contains("source_type=?", "source_id=?", "action_scope=?");
        assertThat(JdbcTodoRepository.UPDATE_ITEM)
                .contains("version=?", "status='OPEN'")
                .doesNotContain("source_type=", "source_id=", "action_scope=");
        assertThat(JdbcTodoRepository.FIND_ACTION)
                .contains("system_id=?", "tenant_id=?", "actor_member_id=?")
                .contains("caller_idempotency_key=?");
        assertThat(JdbcTodoRepository.COMPLETE_ACTION)
                .contains("version=?", "status='PROCESSING'");
    }

    @Test
    void pageSqlSupportsAllFiltersAndFrozenStableOrder() {
        var all = JdbcTodoRepository.pageStatements(10, 20, 30,
                new TodoQuery(TodoQuery.CategoryFilter.ALL, TodoQuery.StateFilter.ALL,
                        TodoQuery.TimeFilter.ALL, 1, 20, null, null));
        assertThat(all.pageSql())
                .contains("system_id=? AND tenant_id=? AND recipient_member_id=?")
                .doesNotContain("status=?", "category=?", "due_at<?")
                .contains("priority ASC", "due_at IS NULL ASC", "created_at ASC,id ASC")
                .contains("LIMIT ? OFFSET ?");
        assertThat(all.pageArguments()).containsExactly(10L, 20L, 30L, 20, 0L);

        var overdue = JdbcTodoRepository.pageStatements(10, 20, 30,
                new TodoQuery(TodoQuery.CategoryFilter.TASK, TodoQuery.StateFilter.OPEN,
                        TodoQuery.TimeFilter.OVERDUE, 2, 10,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-02T00:00:00Z")));
        assertThat(overdue.pageSql()).contains("status=?", "category=?", "due_at<?");
        assertThat(overdue.pageArguments()).hasSize(8);
        assertThat(overdue.countSql()).doesNotContain("ORDER BY", "LIMIT");

        var cc = JdbcTodoRepository.pageStatements(10, 20, 30,
                new TodoQuery(TodoQuery.CategoryFilter.CC, TodoQuery.StateFilter.OPEN,
                        TodoQuery.TimeFilter.ALL, 1, 20, null, null));
        assertThat(cc.pageSql()).contains("category=?");
        assertThat(cc.pageArguments()).contains("CC");
    }
}
