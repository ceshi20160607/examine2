package com.unique.examine.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.event.platform.PlatformMessageApiModels;
import com.unique.examine.event.platform.PlatformMessageService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.task.JdbcPlatformTaskStore;
import com.unique.examine.plat.task.PlatformTaskLifecycleService;
import com.unique.examine.plat.work.PlatformWorkApi;
import com.unique.examine.plat.work.PlatformWorkService;
import com.unique.examine.web.todo.PlatformTodoApiModels;
import com.unique.examine.web.todo.PlatformTodoController;
import com.unique.examine.web.todo.PlatformTodoStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PlatformWorkTodoMessageJourneyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("platform_work_message_test")
            .withUsername("test").withPassword("test");

    private JdbcTemplate jdbc;
    private PlatformWorkService work;
    private PlatformTodoController todos;
    private PlatformMessageService messages;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("DROP TABLE IF EXISTS un_platform_todo_action");
        jdbc.execute("DROP TABLE IF EXISTS un_platform_work_daily_report");
        jdbc.execute("DROP TABLE IF EXISTS un_platform_task");
        jdbc.execute("DROP TABLE IF EXISTS un_platform_work_project");
        jdbc.execute("DROP TABLE IF EXISTS un_platform_inbox_message");
        jdbc.execute("""
                CREATE TABLE un_platform_work_project(
                  id BIGINT NOT NULL PRIMARY KEY,owner_account_id BIGINT NOT NULL,
                  code VARCHAR(64) NOT NULL,name VARCHAR(200) NOT NULL,status VARCHAR(16) NOT NULL,
                  start_date DATE NULL,due_date DATE NULL,created_at DATETIME(6) NOT NULL,
                  created_by BIGINT NOT NULL,updated_at DATETIME(6) NOT NULL,
                  updated_by BIGINT NOT NULL,version BIGINT NOT NULL,
                  UNIQUE KEY uk_owner_code(owner_account_id,code)) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE un_platform_task(
                  id BIGINT NOT NULL PRIMARY KEY,account_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,
                  description VARCHAR(2000) NULL,due_at DATETIME(6) NULL,priority VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL,source VARCHAR(16) NOT NULL,task_kind VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
                  project_id BIGINT NULL,labels_json JSON NULL,authorization_epoch BIGINT NOT NULL,
                  payload_hash CHAR(64) NOT NULL,idempotency_key VARCHAR(128) NOT NULL,
                  request_id VARCHAR(128) NOT NULL,trace_id VARCHAR(128) NOT NULL,
                  created_at DATETIME(6) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(6) NOT NULL,
                  completed_at DATETIME(6) NULL,cancelled_at DATETIME(6) NULL,version BIGINT NOT NULL,
                  UNIQUE KEY uk_owner_key(account_id,idempotency_key)) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE un_platform_work_daily_report(
                  id BIGINT NOT NULL PRIMARY KEY,account_id BIGINT NOT NULL,report_date DATE NOT NULL,
                  status VARCHAR(16) NOT NULL,completed_text VARCHAR(4000) NOT NULL,plan_text VARCHAR(4000) NOT NULL,
                  risk_text VARCHAR(4000) NULL,project_id BIGINT NULL,created_at DATETIME(6) NOT NULL,
                  created_by BIGINT NOT NULL,updated_at DATETIME(6) NOT NULL,updated_by BIGINT NOT NULL,
                  submitted_at DATETIME(6) NULL,version BIGINT NOT NULL,
                  UNIQUE KEY uk_owner_date(account_id,report_date)) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE un_platform_todo_action(
                  account_id BIGINT NOT NULL,idempotency_key VARCHAR(128) NOT NULL,task_id BIGINT NOT NULL,
                  requested_action VARCHAR(16) NOT NULL,request_version BIGINT NOT NULL,
                  result_json JSON NOT NULL,completed_at DATETIME(6) NOT NULL,
                  PRIMARY KEY(account_id,idempotency_key)) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE un_platform_inbox_message(
                  id BIGINT NOT NULL PRIMARY KEY,recipient_account_id BIGINT NOT NULL,
                  template_code VARCHAR(100) NOT NULL,message_type VARCHAR(24) NOT NULL,
                  title VARCHAR(200) NOT NULL,body VARCHAR(4000) NOT NULL,target_type VARCHAR(32) NULL,
                  target_id VARCHAR(128) NULL,target_path VARCHAR(500) NULL,created_at DATETIME(6) NOT NULL,
                  read_at DATETIME(6) NULL,archived_at DATETIME(6) NULL,version BIGINT NOT NULL) ENGINE=InnoDB
                """);
        var mapper = new ObjectMapper();
        var nativeTasks = new JdbcPlatformTaskStore(jdbc);
        work = new PlatformWorkService(jdbc, nativeTasks, new IdService(), mapper);
        todos = new PlatformTodoController(new PlatformTaskLifecycleService(nativeTasks),
                new PlatformTodoStore(jdbc, mapper));
        messages = new PlatformMessageService(jdbc, new IdService());
    }

    @Test
    void projectTaskReportTodoAndPlatformMessageCompleteOneAccountScopedJourney() {
        var project = work.createProject(session(), new PlatformWorkApi.ProjectInput(
                "RELEASE_2026", "Platform release", LocalDate.now(), LocalDate.now().plusDays(10)));
        var task = work.createTask(session(), new PlatformWorkApi.TaskInput(
                PlatformWorkApi.TaskKind.PROJECT, project.id(), "Verify release", "Run the release gate",
                Instant.now().plusSeconds(86_400), PlatformTaskFacade.Priority.HIGH, List.of("release")),
                "create-work-task-1", "request-work-1", "trace-work-1");
        assertThat(work.tasks(session(), "PROJECT", "OPEN", 1, 20).items())
                .singleElement().satisfies(value -> {
                    assertThat(value.projectId()).isEqualTo(project.id());
                    assertThat(value.labels()).containsExactly("release");
                });

        var report = work.saveReport(session(), new PlatformWorkApi.ReportInput(
                LocalDate.now(), "Verified task", "Submit release", null, project.id(), null));
        assertThat(work.submitReport(session(), report.id(), report.version()).status())
                .isEqualTo(PlatformWorkApi.ReportStatus.SUBMITTED);

        var request = new MockHttpServletRequest();
        var todoPage = todos.page(PlatformTodoApiModels.StateFilter.OPEN,
                1, 20, session(), request).data();
        assertThat(todoPage.items()).extracting(PlatformTodoApiModels.TodoView::id)
                .contains(task.taskId());
        var completed = todos.action(Long.parseLong(task.taskId()),
                new PlatformTodoApiModels.ActionBody(PlatformTodoApiModels.Action.COMPLETE, 0),
                "complete-platform-work-1", session(), request).data();
        assertThat(completed.todo().sourceStatus()).isEqualTo("COMPLETED");
        assertThat(completed.todo().routeHint()).startsWith("/platform/work");

        var created = messages.send(7, "platform.task.completed", "TASK",
                "Task completed", "The platform work task was completed",
                new PlatformMessageApiModels.Target("PLATFORM_TASK", task.taskId(),
                        "/platform/work?task=" + task.taskId()));
        assertThat(messages.list(session(), "UNREAD", "TASK", null, "completed",
                null, null, 1, 20).items()).containsExactly(created);
        assertThat(messages.read(session(), created.id()).status()).isEqualTo("READ");
        assertThat(messages.archive(session(), created.id()).status()).isEqualTo("ARCHIVED");
    }

    @Test
    void platformOwnersRejectSystemContextForeignAccountsAndBusinessDeepLinks() {
        assertThatThrownBy(() -> work.projects(systemSession()))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.code()).isEqualTo("CONTEXT_PLATFORM_REQUIRED"));
        assertThatThrownBy(() -> messages.list(systemSession(), "ALL", "ALL", null,
                null, null, null, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.code()).isEqualTo("CONTEXT_PLATFORM_REQUIRED"));
        assertThatThrownBy(() -> messages.send(7, "unsafe.target", "TASK", "Unsafe", "Body",
                new PlatformMessageApiModels.Target("PLATFORM_TASK", "1", "/systems/99/workbench")))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PLATFORM_MESSAGE_REQUEST_INVALID"));
    }

    private static AuthenticatedSession session() {
        return new AuthenticatedSession(1, 7, ContextType.PLATFORM, null, null, null, 1,
                Set.of("platform.work.read", "platform.work.manage", "platform.task.read",
                        "platform.task.manage", "platform.message.read", "platform.message.manage"));
    }
    private static AuthenticatedSession systemSession() {
        return new AuthenticatedSession(2, 7, ContextType.SYSTEM, 99L, 88L, 77L, 1,
                session().permissions());
    }
}
