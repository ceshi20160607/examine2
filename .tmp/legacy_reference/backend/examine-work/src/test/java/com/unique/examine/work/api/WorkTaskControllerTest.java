package com.unique.examine.work.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskReminderRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskPage;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.port.WorkTaskRepository;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkTaskControllerTest {
    private ObjectMapper json;
    private MockMvc mvc;
    private WorkTaskService service;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        service = service(new InMemoryWorkTaskRepository());
        mvc = mvc(service);
    }

    @Test
    void authenticatedHttpJourneyCreatesAssignsListsCompletesAndReopens() throws Exception {
        var creator = session(10, 20, 100,
                WorkRequestSession.ACCESS, WorkTaskService.CREATE);
        var createdBody = mvc.perform(post("/api/v1/systems/10/work/tasks")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, creator)
                        .contentType("application/json")
                        .content("""
                                {"title":"Prepare release","assigneeMemberId":101}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var createdJson = json.readTree(createdBody);
        assertThat(createdJson.path("data").path("id").isTextual()).isTrue();
        assertThat(createdJson.path("data").path("systemId").asText()).isEqualTo("10");
        assertThat(createdJson.path("data").path("tenantId").asText()).isEqualTo("20");
        assertThat(createdJson.path("data").path("creatorMemberId").asText()).isEqualTo("100");
        assertThat(createdJson.path("data").path("assigneeMemberId").asText()).isEqualTo("101");
        assertThat(createdJson.path("requestId").asText()).isEmpty();
        assertThat(createdJson.path("traceId").asText()).isEmpty();
        long taskId = Long.parseLong(createdJson.path("data").path("id").asText());

        mvc.perform(post("/api/v1/systems/10/work/tasks/{id}:assign", taskId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, creator)
                        .contentType("application/json")
                        .content("""
                                {"assigneeMemberId":102}
                                """))
                .andExpect(status().isOk());

        var listBody = mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 102, WorkRequestSession.ACCESS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(listBody).path("data").path("items")).hasSize(1);
        assertThat(json.readTree(listBody).path("data").path("page").asInt()).isEqualTo(1);
        assertThat(json.readTree(listBody).path("data").path("size").asInt()).isEqualTo(20);
        assertThat(json.readTree(listBody).path("data").path("total").asLong()).isEqualTo(1);

        mvc.perform(post("/api/v1/systems/10/work/tasks/{id}:complete", taskId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 102, WorkRequestSession.ACCESS)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/systems/10/work/tasks/{id}:reopen", taskId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, creator))
                .andExpect(status().isOk());
    }

    @Test
    void accessAndTaskPermissionsFailClosed() throws Exception {
        mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20, 100)))
                .andExpect(status().isForbidden());
        var task = service.create(actor(100, WorkTaskService.CREATE), "Owned", 101);
        mvc.perform(post("/api/v1/systems/10/work/tasks/{id}:assign", task.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, WorkRequestSession.ACCESS))
                        .contentType("application/json")
                        .content("""
                                {"assigneeMemberId":102}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemMismatchAndTenantIsolationAreRejected() throws Exception {
        var task = service.create(actor(100, WorkTaskService.CREATE), "Tenant 20", 101);
        mvc.perform(get("/api/v1/systems/11/work/tasks")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100, WorkRequestSession.ACCESS)))
                .andExpect(status().isForbidden());

        var otherTenant = session(10, 21, 101, WorkRequestSession.ACCESS);
        var listBody = mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, otherTenant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(listBody).path("data").path("items")).isEmpty();
        assertThat(json.readTree(listBody).path("data").path("total").asLong()).isZero();
        mvc.perform(post("/api/v1/systems/10/work/tasks/{id}:complete", task.id())
                        .requestAttr(
                                RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 21, 101, WorkRequestSession.ACCESS)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pageEndpointFiltersAndOnlyAllowsManagersToRequestAllTenantTasks() throws Exception {
        var literal = service.create(
                actor(100, WorkTaskService.CREATE),
                "Review 100% _literal_",
                101);
        service.create(actor(102, WorkTaskService.CREATE), "Other open task", 102);
        service.complete(actor(101), literal.id());

        var filteredBody = mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .param("keyword", "100% _literal_")
                        .param("status", "COMPLETED")
                        .param("role", "CREATED_BY_ME")
                        .param("page", "1")
                        .param("size", "10")
                        .requestAttr(
                                RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100, WorkRequestSession.ACCESS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var filtered = json.readTree(filteredBody).path("data");
        assertThat(filtered.path("total").asLong()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("id").asText())
                .isEqualTo(Long.toString(literal.id()));

        mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .param("role", "ALL")
                        .requestAttr(
                                RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100, WorkRequestSession.ACCESS)))
                .andExpect(status().isForbidden());

        var managedBody = mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .param("role", "ALL")
                        .requestAttr(
                                RequestSession.REQUEST_ATTRIBUTE,
                                session(
                                        10,
                                        20,
                                        100,
                                        WorkRequestSession.ACCESS,
                                        WorkTaskService.MANAGE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(managedBody).path("data").path("total").asLong())
                .isEqualTo(2);
    }

    @Test
    void pageEndpointValidatesKeywordAndPageBounds() throws Exception {
        var reader = session(10, 20, 100, WorkRequestSession.ACCESS);
        mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .param("keyword", "x".repeat(101))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, reader))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .param("page", "0")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, reader))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/systems/10/work/tasks")
                        .param("size", "101")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, reader))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void optimisticVersionConflictReturnsHttp409() throws Exception {
        var conflictRepository = new ConflictOnUpdateRepository();
        var conflictService = service(conflictRepository);
        var conflictMvc = mvc(conflictService);
        var task = conflictService.create(actor(100, WorkTaskService.CREATE), "Conflict", 101);

        mvc = conflictMvc;
        mvc.perform(post("/api/v1/systems/10/work/tasks/{id}:complete", task.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, WorkRequestSession.ACCESS)))
                .andExpect(status().isConflict());
    }

    @Test
    void standaloneMetadataRoundTripKeepsLegacyNullProjectFacts() throws Exception {
        var creator = session(10, 20, 100,
                WorkRequestSession.ACCESS, WorkTaskService.CREATE);
        var createdBody = mvc.perform(post("/api/v1/systems/10/work/tasks")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, creator)
                        .contentType("application/json")
                        .content("""
                                {"title":"Legacy","assigneeMemberId":101}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var created = json.readTree(createdBody).path("data");
        var taskId = created.path("id").asLong();
        assertThat(created.path("projectId").isNull()).isTrue();
        assertThat(created.path("description").isNull()).isTrue();
        assertThat(created.path("dueAt").isNull()).isTrue();

        var updatedBody = mvc.perform(put(
                        "/api/v1/systems/10/work/tasks/{id}", taskId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, creator)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"Scheduled",
                                  "description":"Do it",
                                  "dueAt":"2026-08-01T09:30:00Z",
                                  "version":1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var updated = json.readTree(updatedBody).path("data");
        assertThat(updated.path("id").asText())
                .isEqualTo(Long.toString(taskId));
        assertThat(updated.path("description").asText()).isEqualTo("Do it");
        assertThat(updated.path("dueAt").asText())
                .isEqualTo("2026-08-01T09:30:00Z");
        assertThat(updated.path("projectId").isNull()).isTrue();
        assertThat(updated.path("version").asLong()).isEqualTo(2L);

        var readBody = mvc.perform(get(
                        "/api/v1/systems/10/work/tasks/{id}", taskId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, creator))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(readBody).path("data").path("id").asText())
                .isEqualTo(Long.toString(taskId));
    }

    @Test
    void reminderPayloadDistinguishesOmittedPreserveFromExplicitNullCancel()
            throws Exception {
        var tasks = new InMemoryWorkTaskRepository();
        var reminders = new InMemoryWorkTaskReminderRepository();
        var now = Instant.parse("2026-08-01T08:00:00Z");
        var reminderMvc = mvc(reminderService(tasks, reminders, now));
        var creator = session(10, 20, 100,
                WorkRequestSession.ACCESS, WorkTaskService.CREATE);

        var createdBody = reminderMvc.perform(
                        post("/api/v1/systems/10/work/tasks")
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        creator)
                                .contentType("application/json")
                                .content("""
                                        {
                                          "title":"Timed",
                                          "assigneeMemberId":101,
                                          "dueAt":"2026-08-01T12:00:00Z",
                                          "reminderAt":"2026-08-01T09:00:00Z"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var created = json.readTree(createdBody).path("data");
        var taskId = created.path("id").asLong();
        assertThat(created.path("reminderAt").asText())
                .isEqualTo("2026-08-01T09:00:00Z");
        assertThat(created.path("reminder").path("generation").asInt())
                .isEqualTo(1);
        assertThat(created.path("reminder").path("status").asText())
                .isEqualTo("PENDING");
        assertThat(created.path("reminder").has("lease")).isFalse();
        assertThat(created.path("reminder").has("failureMessage")).isFalse();

        var preservedBody = reminderMvc.perform(
                        put("/api/v1/systems/10/work/tasks/{id}", taskId)
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        creator)
                                .contentType("application/json")
                                .content("""
                                        {
                                          "title":"Timed renamed",
                                          "dueAt":"2026-08-01T12:00:00Z",
                                          "version":1
                                        }
                                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var preserved = json.readTree(preservedBody).path("data");
        assertThat(preserved.path("reminderAt").asText())
                .isEqualTo("2026-08-01T09:00:00Z");
        assertThat(preserved.path("reminder").path("generation").asInt())
                .isEqualTo(1);

        var cancelledBody = reminderMvc.perform(
                        put("/api/v1/systems/10/work/tasks/{id}", taskId)
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        session(10, 20, 101,
                                                WorkRequestSession.ACCESS))
                                .contentType("application/json")
                                .content("""
                                        {
                                          "title":"Timed renamed",
                                          "dueAt":"2026-08-01T12:00:00Z",
                                          "reminderAt":null,
                                          "version":2
                                        }
                                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var cancelled = json.readTree(cancelledBody).path("data");
        assertThat(cancelled.path("reminderAt").isNull()).isTrue();
        assertThat(cancelled.path("reminder").path("status").asText())
                .isEqualTo("CANCELLED");

        var invalidBody = reminderMvc.perform(
                        post("/api/v1/systems/10/work/tasks")
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        creator)
                                .contentType("application/json")
                                .content("""
                                        {
                                          "title":"Invalid",
                                          "assigneeMemberId":101,
                                          "dueAt":"2026-08-01T10:00:00Z",
                                          "reminderAt":"2026-08-01T10:00:01Z"
                                        }
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(invalidBody).path("code").asText())
                .isEqualTo("WORK_TASK_REMINDER_INVALID");
    }

    @Test
    void managerRetryEndpointUsesReminderVersionWithoutAdvancingTaskVersion()
            throws Exception {
        var tasks = new InMemoryWorkTaskRepository();
        var reminders = new InMemoryWorkTaskReminderRepository();
        var createdAt = Instant.parse("2026-08-01T08:00:00Z");
        var initial = reminderService(tasks, reminders, createdAt);
        var task = initial.create(
                actor(100, WorkTaskService.CREATE),
                "Retry HTTP", 101, null, null,
                createdAt.plusSeconds(7_200), createdAt.plusSeconds(60));
        var claimedAt = createdAt.plusSeconds(61);
        var token = "b".repeat(64);
        var failed = reminders.claimDue(
                        "api-test", token, claimedAt,
                        claimedAt.plusSeconds(60), 10)
                .getFirst()
                .fail(token, "DELIVERY_FAILED", "sensitive detail",
                        claimedAt.plusSeconds(1));
        failed = reminders.save(failed);
        var retryMvc = mvc(reminderService(
                tasks, reminders, claimedAt.plusSeconds(2)));
        var manager = session(
                10, 20, 102,
                WorkRequestSession.ACCESS, WorkTaskService.MANAGE);

        var retriedBody = retryMvc.perform(post(
                        "/api/v1/systems/10/work/tasks/{id}/reminder:retry",
                        task.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, manager)
                        .contentType("application/json")
                        .content("{\"version\":" + failed.version() + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var retried = json.readTree(retriedBody).path("data");
        assertThat(retried.path("version").asLong())
                .isEqualTo(task.version());
        assertThat(retried.path("reminder").path("status").asText())
                .isEqualTo("PENDING");
        assertThat(retried.path("reminder").path("attemptCount").asInt())
                .isEqualTo(1);
        assertThat(retried.path("reminder").has("failureMessage")).isFalse();

        var retriedVersion = retried.path("reminder").path("version").asLong();
        var repeatedBody = retryMvc.perform(post(
                        "/api/v1/systems/10/work/tasks/{id}/reminder:retry",
                        task.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, manager)
                        .contentType("application/json")
                        .content("{\"version\":" + retriedVersion + "}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repeatedBody).path("code").asText())
                .isEqualTo("WORK_TASK_REMINDER_STATE_INVALID");
    }

    private WorkTaskService service(WorkTaskRepository repository) {
        return new WorkTaskService(repository,
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC));
    }

    private WorkTaskService reminderService(
            InMemoryWorkTaskRepository tasks,
            InMemoryWorkTaskReminderRepository reminders,
            Instant now
    ) {
        return new WorkTaskService(
                tasks, null, reminders,
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private MockMvc mvc(WorkTaskService taskService) {
        var converter = new MappingJackson2HttpMessageConverter(json);
        return MockMvcBuilders.standaloneSetup(new WorkTaskController(taskService))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(converter)
                .build();
    }

    private static WorkActor actor(long memberId, String... permissions) {
        return new WorkActor(10, 20, memberId, Set.of(permissions));
    }

    private static TestSession session(
            long systemId,
            long tenantId,
            long memberId,
            String... permissions
    ) {
        return new TestSession(systemId, tenantId, memberId, Set.of(permissions));
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

    private static final class ConflictOnUpdateRepository implements WorkTaskRepository {
        private final InMemoryWorkTaskRepository delegate = new InMemoryWorkTaskRepository();

        @Override
        public long nextId() {
            return delegate.nextId();
        }

        @Override
        public Optional<WorkTask> findById(long systemId, long tenantId, long id) {
            return delegate.findById(systemId, tenantId, id);
        }

        @Override
        public List<WorkTask> findAll(long systemId, long tenantId) {
            return delegate.findAll(systemId, tenantId);
        }

        @Override
        public List<WorkTask> findParticipating(long systemId, long tenantId, long memberId) {
            return delegate.findParticipating(systemId, tenantId, memberId);
        }

        @Override
        public WorkTaskPage findPage(
                long systemId,
                long tenantId,
                long memberId,
                WorkTaskQuery query
        ) {
            return delegate.findPage(systemId, tenantId, memberId, query);
        }

        @Override
        public com.unique.examine.work.domain.WorkTaskMetricFacts metrics(
                long systemId,
                long tenantId,
                long memberId,
                boolean tenantWide,
                Instant fromInclusive,
                Instant toExclusive,
                Instant now
        ) {
            return delegate.metrics(
                    systemId, tenantId, memberId, tenantWide,
                    fromInclusive, toExclusive, now);
        }

        @Override
        public WorkTask save(WorkTask task) {
            if (task.version() > 1) {
                throw new WorkDomainException("WORK_TASK_VERSION_CONFLICT", "Task version is stale");
            }
            return delegate.save(task);
        }
    }
}
