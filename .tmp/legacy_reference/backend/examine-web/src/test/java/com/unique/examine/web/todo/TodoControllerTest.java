package com.unique.examine.web.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.todo.adapter.memory.InMemoryTodoRepository;
import com.unique.examine.todo.service.TodoService;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodoControllerTest {
    @Test
    void refreshPageDetailCompleteReplayCountsAndIsolationStayOnPublicPorts()
            throws Exception {
        var now = Instant.parse("2026-08-01T08:00:00Z");
        var tasks = new WorkTaskService(
                new InMemoryWorkTaskRepository(),
                (systemId, tenantId, memberId) -> true,
                Clock.fixed(now, ZoneOffset.UTC));
        tasks.create(
                new WorkActor(
                        10, 20, 100,
                        Set.of(WorkTaskService.CREATE)),
                "HTTP Todo task", 101);
        var work = new WorkTodoAdapter(tasks);
        var todos = new TodoService(
                new InMemoryTodoRepository(),
                java.util.List.of(work), java.util.List.of(work),
                Clock.fixed(now, ZoneOffset.UTC));
        var json = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        var mvc = mvc(todos, json);
        var member = session(20);

        var refreshBody = mvc.perform(post(
                        "/api/v1/systems/10/todos:refresh")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-59")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-59"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var refresh = json.readTree(refreshBody).path("data");
        assertThat(refresh.path("discovered").asInt()).isEqualTo(1);
        assertThat(refresh.path("created").asInt()).isEqualTo(1);

        var pageBody = mvc.perform(get("/api/v1/systems/10/todos")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var page = json.readTree(pageBody).path("data");
        var item = page.path("items").get(0);
        var todoId = item.path("id").asLong();
        assertThat(item.path("systemId").asText()).isEqualTo("10");
        assertThat(item.path("tenantId").asText()).isEqualTo("20");
        assertThat(item.path("recipientMemberId").asText()).isEqualTo("101");
        assertThat(item.path("sourceType").asText()).isEqualTo("WORK_TASK");
        assertThat(item.path("sourceId").isTextual()).isTrue();
        assertThat(item.path("sourceVersion").asLong()).isEqualTo(1);
        assertThat(item.path("actionScope").asText()).isEqualTo("COMPLETE");
        assertThat(item.path("status").asText()).isEqualTo("OPEN");
        assertThat(item.has("state")).isFalse();
        assertThat(item.path("version").asLong()).isEqualTo(1);

        var countsBody = mvc.perform(get(
                        "/api/v1/systems/10/todos/counts")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var counts = json.readTree(countsBody).path("data");
        assertThat(counts.path("openCount").asLong()).isEqualTo(1);
        assertThat(counts.path("taskCount").asLong()).isEqualTo(1);

        var detailBody = mvc.perform(get(
                        "/api/v1/systems/10/todos/{id}", todoId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detailBody).path("data").path("status").asText())
                .isEqualTo("LIVE");

        var actionBody = mvc.perform(post(
                        "/api/v1/systems/10/todos/{id}:action", todoId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-59")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-59")
                        .header("Idempotency-Key", "todo-complete-59")
                        .contentType("application/json")
                        .content("""
                                {"version":1,"action":"COMPLETE"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var action = json.readTree(actionBody).path("data");
        assertThat(action.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(action.path("replayed").asBoolean()).isFalse();
        assertThat(action.path("todo").path("status").asText())
                .isEqualTo("CLOSED");
        assertThat(action.path("sourceResult").path("code").asText())
                .isEqualTo("SUCCESS");

        var replayBody = mvc.perform(post(
                        "/api/v1/systems/10/todos/{id}:action", todoId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member)
                        .header("Idempotency-Key", "todo-complete-59")
                        .contentType("application/json")
                        .content("""
                                {"version":1,"action":"COMPLETE"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replayBody).path("data")
                .path("replayed").asBoolean()).isTrue();

        var conflictBody = mvc.perform(post(
                        "/api/v1/systems/10/todos/{id}:action", todoId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, member)
                        .header("Idempotency-Key", "todo-complete-stale")
                        .contentType("application/json")
                        .content("""
                                {"version":1,"action":"COMPLETE"}
                                """))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(conflictBody).path("code").asText())
                .isEqualTo("TODO_VERSION_CONFLICT");

        var otherTenantBody = mvc.perform(get("/api/v1/systems/10/todos")
                        .requestAttr(
                                RequestSession.REQUEST_ATTRIBUTE,
                                session(21)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(otherTenantBody).path("data")
                .path("items")).isEmpty();
    }

    private static MockMvc mvc(TodoService service, ObjectMapper json) {
        return MockMvcBuilders.standaloneSetup(new TodoController(service))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    private static TestSession session(long tenantId) {
        return new TestSession(
                10, tenantId, 101,
                Set.of(WorkTaskService.ACCESS));
    }

    private record TestSession(
            long requestedSystemId,
            long requestedTenantId,
            long requestedMemberId,
            Set<String> requestedPermissions
    ) implements RequestSession {
        @Override
        public long sessionId() {
            return 1;
        }

        @Override
        public long accountId() {
            return requestedMemberId;
        }

        @Override
        public ContextType contextType() {
            return ContextType.SYSTEM;
        }

        @Override
        public Long systemId() {
            return requestedSystemId;
        }

        @Override
        public Long tenantId() {
            return requestedTenantId;
        }

        @Override
        public Long memberId() {
            return requestedMemberId;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }

        @Override
        public Set<String> permissions() {
            return requestedPermissions;
        }
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(),
                    "message", error.getMessage()));
        }
    }
}
