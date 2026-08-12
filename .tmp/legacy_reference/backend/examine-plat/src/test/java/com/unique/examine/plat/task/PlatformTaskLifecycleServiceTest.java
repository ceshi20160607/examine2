package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTaskLifecycleServiceTest {
    private static final Instant CREATED = Instant.parse("2026-08-05T01:00:00Z");
    private static final Instant NOW_WITH_NANOS = Instant.parse(
            "2026-08-05T03:00:00.123456789Z");

    @Test
    void listsOnlyOwnSafeTasksWithStatusPagingAndUpdatedOrder() {
        var store = new MemoryStore();
        store.put(7, task("11", PlatformTaskFacade.Status.OPEN,
                CREATED.plusSeconds(20), 0));
        store.put(7, task("12", PlatformTaskFacade.Status.COMPLETED,
                CREATED.plusSeconds(30), 2));
        store.put(7, task("13", PlatformTaskFacade.Status.OPEN,
                CREATED.plusSeconds(30), 1));
        store.put(8, task("99", PlatformTaskFacade.Status.OPEN,
                CREATED.plusSeconds(60), 0));
        var service = service(store);

        var all = service.list(session(7, Set.of(
                PlatformTaskLifecycleService.READ)), null, null, null);
        assertThat(all.page()).isOne();
        assertThat(all.size()).isEqualTo(20);
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.items()).extracting(PlatformTaskApi.TaskView::taskId)
                .containsExactly("13", "12", "11");

        var open = service.list(session(7, Set.of(
                PlatformTaskLifecycleService.READ)), "OPEN", 1, 1);
        assertThat(open.total()).isEqualTo(2);
        assertThat(open.items()).extracting(PlatformTaskApi.TaskView::taskId)
                .containsExactly("13");
        assertThat(PlatformTaskApi.TaskView.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "taskId", "title", "description", "dueAt", "priority",
                        "status", "source", "createdAt", "updatedAt",
                        "completedAt", "cancelledAt", "version");
    }

    @Test
    void completesReopensCancelsAndReopensWithOneVersionPerTransition() {
        var store = new MemoryStore();
        store.put(7, task("11", PlatformTaskFacade.Status.OPEN, CREATED, 0));
        var service = service(store);
        var caller = session(7, Set.of(PlatformTaskLifecycleService.MANAGE));

        var completed = service.complete(caller, "11", 0);
        assertThat(completed.status()).isEqualTo(
                PlatformTaskFacade.Status.COMPLETED);
        assertThat(completed.version()).isOne();
        assertThat(completed.completedAt()).isEqualTo(
                Instant.parse("2026-08-05T03:00:00.123456Z"));
        assertThat(completed.cancelledAt()).isNull();

        var reopened = service.reopen(caller, "11", 1);
        assertThat(reopened.status()).isEqualTo(PlatformTaskFacade.Status.OPEN);
        assertThat(reopened.version()).isEqualTo(2);
        assertThat(reopened.updatedAt()).isAfter(completed.updatedAt());
        assertThat(reopened.completedAt()).isNull();
        assertThat(reopened.cancelledAt()).isNull();

        var cancelled = service.cancel(caller, "11", 2);
        assertThat(cancelled.status()).isEqualTo(
                PlatformTaskFacade.Status.CANCELLED);
        assertThat(cancelled.version()).isEqualTo(3);
        assertThat(cancelled.cancelledAt()).isEqualTo(cancelled.updatedAt());
        assertThat(cancelled.completedAt()).isNull();

        var reopenedAgain = service.reopen(caller, "11", 3);
        assertThat(reopenedAgain.status()).isEqualTo(
                PlatformTaskFacade.Status.OPEN);
        assertThat(reopenedAgain.version()).isEqualTo(4);
        assertThat(reopenedAgain.completedAt()).isNull();
        assertThat(reopenedAgain.cancelledAt()).isNull();
        assertThat(reopenedAgain.dueAt()).isBefore(reopenedAgain.updatedAt());
    }

    @Test
    void staleVersionWinsBeforeInvalidStateAndForeignOwnerIsHidden() {
        var store = new MemoryStore();
        store.put(7, task("11", PlatformTaskFacade.Status.OPEN, CREATED, 0));
        var service = service(store);
        var owner = session(7, Set.of(PlatformTaskLifecycleService.MANAGE));
        var foreign = session(8, Set.of(PlatformTaskLifecycleService.MANAGE));

        service.complete(owner, "11", 0);
        assertCode("PLATFORM_TASK_VERSION_CONFLICT",
                () -> service.complete(owner, "11", 0));
        assertCode("PLATFORM_TASK_STATE_INVALID",
                () -> service.complete(owner, "11", 1));
        assertCode("PLATFORM_TASK_NOT_FOUND",
                () -> service.reopen(foreign, "11", 1));
        assertThat(store.findOwnById(7, 11).orElseThrow().version()).isOne();
    }

    @Test
    void requiresPlatformReadAndManagePermissionsAndValidatesQueryBounds() {
        var store = new MemoryStore();
        store.put(7, task("11", PlatformTaskFacade.Status.OPEN, CREATED, 0));
        var service = service(store);

        assertCode("PERMISSION_DENIED", () -> service.list(
                session(7, Set.of()), null, null, null));
        assertCode("PERMISSION_DENIED", () -> service.complete(
                session(7, Set.of(PlatformTaskLifecycleService.READ)),
                "11", 0));
        assertCode("CONTEXT_PLATFORM_REQUIRED", () -> service.list(
                systemSession(7, Set.of(PlatformTaskLifecycleService.READ)),
                null, null, null));
        assertCode("PLATFORM_TASK_REQUEST_INVALID", () -> service.list(
                session(7, Set.of(PlatformTaskLifecycleService.READ)),
                "open", null, null));
        assertCode("PLATFORM_TASK_REQUEST_INVALID", () -> service.list(
                session(7, Set.of(PlatformTaskLifecycleService.READ)),
                "ALL", 10_001, 20));
        assertCode("PLATFORM_TASK_REQUEST_INVALID", () -> service.list(
                session(7, Set.of(PlatformTaskLifecycleService.READ)),
                "ALL", 1, 101));
        assertThatThrownBy(() -> new PlatformTaskApi.VersionCommand(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PlatformTaskApi.VersionCommand(0L).version()).isZero();
    }

    private static PlatformTaskLifecycleService service(MemoryStore store) {
        return new PlatformTaskLifecycleService(
                store, Clock.fixed(NOW_WITH_NANOS, ZoneOffset.UTC));
    }

    private static PlatformTaskApi.TaskView task(
            String id,
            PlatformTaskFacade.Status status,
            Instant updatedAt,
            long version) {
        var completedAt = status == PlatformTaskFacade.Status.COMPLETED
                ? updatedAt : null;
        var cancelledAt = status == PlatformTaskFacade.Status.CANCELLED
                ? updatedAt : null;
        return new PlatformTaskApi.TaskView(
                id, "Task " + id, "Safe description",
                CREATED.plusSeconds(3_600), PlatformTaskFacade.Priority.HIGH,
                status, PlatformTaskFacade.Source.AGENT, CREATED, updatedAt,
                completedAt, cancelledAt, version);
    }

    private static AuthenticatedSession session(
            long accountId, Set<String> permissions) {
        return new AuthenticatedSession(
                1, accountId, ContextType.PLATFORM,
                null, null, null, 3, permissions);
    }

    private static AuthenticatedSession systemSession(
            long accountId, Set<String> permissions) {
        return new AuthenticatedSession(
                1, accountId, ContextType.SYSTEM,
                10L, 20L, 30L, 3, permissions);
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private static final class MemoryStore
            implements PlatformTaskLifecycleStore {
        private final Map<String, PlatformTaskApi.TaskView> rows =
                new HashMap<>();

        void put(long accountId, PlatformTaskApi.TaskView task) {
            rows.put(key(accountId, Long.parseLong(task.taskId())), task);
        }

        @Override
        public long countOwn(long accountId, PlatformTaskStatusFilter status) {
            return own(accountId, status).size();
        }

        @Override
        public List<PlatformTaskApi.TaskView> findOwnPage(
                long accountId,
                PlatformTaskStatusFilter status,
                int offset,
                int size) {
            return own(accountId, status).stream()
                    .skip(offset).limit(size).toList();
        }

        @Override
        public Optional<PlatformTaskApi.TaskView> findOwnById(
                long accountId, long taskId) {
            return Optional.ofNullable(rows.get(key(accountId, taskId)));
        }

        @Override
        public int transition(
                long accountId,
                long taskId,
                long expectedVersion,
                Set<PlatformTaskFacade.Status> expectedStatuses,
                PlatformTaskFacade.Status targetStatus,
                Instant transitionAt) {
            var key = key(accountId, taskId);
            var current = rows.get(key);
            if (current == null || current.version() != expectedVersion
                    || !expectedStatuses.contains(current.status())) {
                return 0;
            }
            rows.put(key, new PlatformTaskApi.TaskView(
                    current.taskId(), current.title(), current.description(),
                    current.dueAt(), current.priority(), targetStatus,
                    current.source(), current.createdAt(), transitionAt,
                    targetStatus == PlatformTaskFacade.Status.COMPLETED
                            ? transitionAt : null,
                    targetStatus == PlatformTaskFacade.Status.CANCELLED
                            ? transitionAt : null,
                    current.version() + 1));
            return 1;
        }

        private List<PlatformTaskApi.TaskView> own(
                long accountId, PlatformTaskStatusFilter status) {
            var prefix = accountId + ":";
            var values = new ArrayList<PlatformTaskApi.TaskView>();
            rows.forEach((key, value) -> {
                if (key.startsWith(prefix)
                        && (status == PlatformTaskStatusFilter.ALL
                        || value.status().name().equals(status.name()))) {
                    values.add(value);
                }
            });
            values.sort(Comparator
                    .comparing(PlatformTaskApi.TaskView::updatedAt).reversed()
                    .thenComparing(
                            value -> Long.parseLong(value.taskId()),
                            Comparator.reverseOrder()));
            return values;
        }

        private static String key(long accountId, long taskId) {
            return accountId + ":" + taskId;
        }
    }
}
