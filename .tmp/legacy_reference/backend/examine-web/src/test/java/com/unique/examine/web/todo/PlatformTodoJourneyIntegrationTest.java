package com.unique.examine.web.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.task.JdbcPlatformTaskStore;
import com.unique.examine.plat.task.PlatformTaskLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PlatformTodoJourneyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("platform_todo_test")
            .withUsername("test")
            .withPassword("test");

    private JdbcTemplate jdbc;
    private PlatformTodoController controller;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS un_platform_todo_action");
        jdbc.execute("DROP TABLE IF EXISTS un_platform_task");
        jdbc.execute("""
                CREATE TABLE un_platform_task(
                  id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
                  account_id BIGINT UNSIGNED NOT NULL,
                  title VARCHAR(200) NOT NULL,
                  description VARCHAR(2000) NULL,
                  due_at DATETIME(6) NULL,
                  priority VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  source VARCHAR(16) NOT NULL,
                  authorization_epoch BIGINT UNSIGNED NOT NULL,
                  payload_hash CHAR(64) NOT NULL,
                  idempotency_key VARCHAR(128) NOT NULL,
                  request_id VARCHAR(128) NOT NULL,
                  trace_id VARCHAR(128) NOT NULL,
                  created_at DATETIME(6) NOT NULL,
                  created_by BIGINT UNSIGNED NOT NULL,
                  updated_at DATETIME(6) NOT NULL,
                  completed_at DATETIME(6) NULL,
                  cancelled_at DATETIME(6) NULL,
                  version BIGINT UNSIGNED NOT NULL,
                  UNIQUE KEY uk_platform_task_owner_key(account_id,idempotency_key)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE un_platform_todo_action(
                  account_id BIGINT UNSIGNED NOT NULL,
                  idempotency_key VARCHAR(128) NOT NULL,
                  task_id BIGINT UNSIGNED NOT NULL,
                  requested_action VARCHAR(16) NOT NULL,
                  request_version BIGINT UNSIGNED NOT NULL,
                  result_json JSON NOT NULL,
                  completed_at DATETIME(6) NOT NULL,
                  PRIMARY KEY(account_id,idempotency_key)
                ) ENGINE=InnoDB
                """);
        jdbc.update("""
                INSERT INTO un_platform_task(
                  id,account_id,title,description,due_at,priority,status,source,
                  authorization_epoch,payload_hash,idempotency_key,request_id,
                  trace_id,created_at,created_by,updated_at,completed_at,
                  cancelled_at,version)
                VALUES(101,7,'Review release','Verify the release gate',NULL,
                  'HIGH','OPEN','AGENT',1,REPEAT('a',64),'seed-key','request-1',
                  'trace-1',UTC_TIMESTAMP(6),7,UTC_TIMESTAMP(6),NULL,NULL,0)
                """);
        var nativeTasks = new PlatformTaskLifecycleService(
                new JdbcPlatformTaskStore(jdbc));
        controller = new PlatformTodoController(nativeTasks,
                new PlatformTodoStore(jdbc, new ObjectMapper()));
    }

    @Test
    void listsCountsDetailsAndExecutesIdempotentNativeAction() {
        var request = new MockHttpServletRequest();
        request.setAttribute("requestId", "request-2");
        request.setAttribute("traceId", "trace-2");

        var page = controller.page(PlatformTodoApiModels.StateFilter.OPEN,
                1, 20, session(), request).data();
        assertThat(page.total()).isOne();
        assertThat(page.items().getFirst().availableActions())
                .containsExactly(
                        PlatformTodoApiModels.Action.COMPLETE,
                        PlatformTodoApiModels.Action.CANCEL);
        assertThat(controller.detail(101, session(), request).data().context())
                .isEqualTo("PLATFORM");

        var completed = controller.action(101,
                new PlatformTodoApiModels.ActionBody(
                        PlatformTodoApiModels.Action.COMPLETE, 0),
                "complete-101", session(), request).data();
        var replay = controller.action(101,
                new PlatformTodoApiModels.ActionBody(
                        PlatformTodoApiModels.Action.COMPLETE, 0),
                "complete-101", session(), request).data();

        assertThat(completed.replayed()).isFalse();
        assertThat(completed.todo().sourceStatus()).isEqualTo("COMPLETED");
        assertThat(completed.todo().availableActions())
                .containsExactly(PlatformTodoApiModels.Action.REOPEN);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.todo()).isEqualTo(completed.todo());
        assertThat(controller.counts(session(), request).data())
                .isEqualTo(new PlatformTodoApiModels.Counts(0, 1, 1));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_platform_todo_action", Long.class))
                .isOne();
    }

    private static AuthenticatedSession session() {
        return new AuthenticatedSession(
                1, 7, ContextType.PLATFORM, null, null, null, 1,
                Set.of("platform.task.read", "platform.task.manage"));
    }
}
