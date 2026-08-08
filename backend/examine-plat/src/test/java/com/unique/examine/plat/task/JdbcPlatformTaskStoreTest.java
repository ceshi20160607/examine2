package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPlatformTaskStoreTest {

    @Test
    void nativeLifecycleSqlIsSelfScopedOrderedConditionalAndSafe() {
        assertThat(normalize(JdbcPlatformTaskStore.FIND_OWN_PAGE_SQL))
                .startsWith("select id,title,description,due_at,priority,status,source,")
                .contains("created_at,updated_at,completed_at,cancelled_at,version")
                .contains("where account_id=? and (?='all' or status=?)")
                .contains("order by updated_at desc,id desc limit ? offset ?")
                .doesNotContain(
                        "authorization_epoch", "payload_hash", "idempotency_key",
                        "request_id", "trace_id", "created_by", "account_id,",
                        "system_id", "tenant_id", "member_id");
        assertThat(normalize(JdbcPlatformTaskStore.FIND_OWN_BY_ID_SQL))
                .contains("where account_id=? and id=?")
                .doesNotContain(
                        "authorization_epoch", "payload_hash", "idempotency_key",
                        "request_id", "trace_id", "created_by", "account_id,");
        assertThat(normalize(JdbcPlatformTaskStore.TRANSITION_SQL))
                .contains("set status=?,updated_at=?,completed_at=?,cancelled_at=?, version=version+1")
                .contains("where account_id=? and id=? and version=? and status in (?,?)")
                .doesNotContain("system_id", "tenant_id", "member_id");
    }

    @Test
    void transitionBindsOwnerVersionSourceStateAndExactTerminalFacts() {
        var at = Instant.parse("2026-08-05T03:00:00.123456Z");
        var jdbc = new RecordingJdbcTemplate(null);
        var store = new JdbcPlatformTaskStore(jdbc);

        assertThat(store.transition(
                7, 11, 0, Set.of(PlatformTaskFacade.Status.OPEN),
                PlatformTaskFacade.Status.COMPLETED, at)).isOne();

        assertThat(normalize(jdbc.updateSql))
                .isEqualTo(normalize(JdbcPlatformTaskStore.TRANSITION_SQL));
        assertThat(jdbc.updateArguments).containsExactly(
                "COMPLETED", Timestamp.from(at), Timestamp.from(at), null,
                7L, 11L, 0L, "OPEN", "OPEN");

        assertThat(store.transition(
                7, 11, 1,
                Set.of(PlatformTaskFacade.Status.COMPLETED,
                        PlatformTaskFacade.Status.CANCELLED),
                PlatformTaskFacade.Status.OPEN, at.plusSeconds(1))).isOne();
        assertThat(jdbc.updateArguments).containsExactly(
                "OPEN", Timestamp.from(at.plusSeconds(1)), null, null,
                7L, 11L, 1L, "COMPLETED", "CANCELLED");
    }

    @Test
    void readsOnlyBoundedSelfScopedSafeTaskColumns() {
        var now = Instant.parse("2026-08-04T01:00:00Z");
        var task = new PlatformOperationsQueryFacade.PersonalTask(
                "11", "Follow up", now.plusSeconds(3_600),
                PlatformTaskFacade.Priority.HIGH,
                PlatformTaskFacade.Status.OPEN,
                PlatformTaskFacade.Source.AGENT, now);
        var jdbc = new RecordingJdbcTemplate(task);
        var store = new JdbcPlatformTaskStore(jdbc);

        assertThat(store.findOwnTasks(7, 5)).containsExactly(task);
        assertThat(normalize(jdbc.querySql))
                .startsWith("select id,title,due_at,priority,status,source,created_at")
                .contains("from un_platform_task where account_id=?")
                .contains("order by created_at desc,id desc limit ?")
                .doesNotContain("description", "authorization_epoch", "payload_hash",
                        "idempotency_key", "request_id", "trace_id", "created_by",
                        "system_id", "tenant_id", "member_id", "module", "field", "record");
        assertThat(jdbc.queryArguments).containsExactly(7L, 5);
    }

    @Test
    void writesAndReadsTheExactPlatformOnlyIdempotentContract() {
        var now = Instant.parse("2026-08-04T01:00:00Z");
        var due = now.plusSeconds(3_600);
        var task = new PlatformTask(
                11, 7, "Follow up", "Description", due,
                PlatformTaskFacade.Priority.HIGH,
                PlatformTaskFacade.Status.OPEN,
                PlatformTaskFacade.Source.AGENT, 3, "a".repeat(64),
                "idem-1", "request-1", "trace-1", now, 7);
        var jdbc = new RecordingJdbcTemplate(task);
        var store = new JdbcPlatformTaskStore(jdbc);

        assertThat(store.createOrReplay(task)).isEqualTo(task);
        assertThat(normalize(jdbc.updateSql))
                .contains("insert into un_platform_task")
                .contains("account_id", "authorization_epoch", "payload_hash")
                .contains("updated_at", "completed_at", "cancelled_at", "version")
                .contains("on duplicate key update id=id")
                .doesNotContain("system_id", "tenant_id", "member_id",
                        "module", "field", "record");
        assertThat(normalize(jdbc.querySql))
                .contains("where account_id=? and idempotency_key=?");
        assertThat(jdbc.updateArguments).containsExactly(
                11L, 7L, "Follow up", "Description", Timestamp.from(due),
                "HIGH", "OPEN", "AGENT", 3L, "a".repeat(64), "idem-1",
                "request-1", "trace-1", Timestamp.from(now), 7L,
                Timestamp.from(now), null, null, 0L);
        assertThat(jdbc.queryArguments).containsExactly(7L, "idem-1");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final Object stored;
        private String updateSql;
        private Object[] updateArguments;
        private String querySql;
        private Object[] queryArguments;

        private RecordingJdbcTemplate(Object stored) {
            this.stored = stored;
        }

        @Override
        public int update(String sql, Object... arguments) {
            updateSql = sql;
            updateArguments = arguments;
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
                String sql, RowMapper<T> mapper, Object... arguments) {
            querySql = sql;
            queryArguments = arguments;
            return (List<T>) List.of(stored);
        }
    }
}
