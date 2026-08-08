package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformOperationsQueryOwnerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void personalTasksAreReadLiveBoundedAndOnlyForTheCurrentAccount() {
        var access = new Access("platform.task.read");
        var tasks = new Tasks(List.of(task("12", NOW),
                task("11", NOW.minusSeconds(60))));
        var owner = owner(access, tasks, null, null, null);

        var result = (PlatformOperationsQueryFacade.PersonalTasksResult)
                owner.query(request(
                        PlatformOperationsQueryFacade.QueryKind.PERSONAL_TASKS, 2));

        assertThat(result.tasks()).extracting(
                PlatformOperationsQueryFacade.PersonalTask::taskId)
                .containsExactly("12", "11");
        assertThat(tasks.accountId).isEqualTo(7);
        assertThat(tasks.limit).isEqualTo(2);
        assertThat(access.calls).isOne();
    }

    @Test
    void everyKindRechecksTheLiveEpochAndItsExactPermission() {
        var access = new Access("platform.ai.agent.use");
        var quotaAccounts = new java.util.ArrayList<Long>();
        PlatformOperationsQueryFacade.AiQuotaProvider quota = accountId -> {
            quotaAccounts.add(accountId);
            return quota();
        };
        var owner = owner(access, new Tasks(List.of()), quota,
                PlatformOperationsQueryOwnerTest::health,
                (accountId, limit) -> activity(1));

        assertThat(owner.query(request(
                PlatformOperationsQueryFacade.QueryKind.AI_QUOTA, 1)))
                .isEqualTo(quota());
        assertThat(quotaAccounts).containsExactly(7L);

        access.permissions = Set.of("platform.audit.view");
        assertThat(owner.query(request(
                PlatformOperationsQueryFacade.QueryKind.SERVICE_HEALTH, 1)))
                .isEqualTo(health());
        assertThat(owner.query(request(
                PlatformOperationsQueryFacade.QueryKind.AGENT_ACTIVITY, 1)))
                .isEqualTo(activity(1));

        access.permissions = Set.of("platform.task.read");
        assertCode("PLATFORM_OPERATIONS_PERMISSION_DENIED", () -> owner.query(
                request(PlatformOperationsQueryFacade.QueryKind.AI_QUOTA, 1)));
        access.epoch = 4;
        assertCode("PLATFORM_OPERATIONS_AUTHORIZATION_STALE", () -> owner.query(
                request(PlatformOperationsQueryFacade.QueryKind.PERSONAL_TASKS, 1)));
        access.epoch = 3;
        access.active = false;
        assertCode("PLATFORM_TASK_ACCOUNT_UNAVAILABLE", () -> owner.query(
                request(PlatformOperationsQueryFacade.QueryKind.PERSONAL_TASKS, 1)));
    }

    @Test
    void activityProviderReceivesOnlyCurrentAccountAndStrictLimit() {
        var access = new Access("platform.audit.view");
        var seen = new long[2];
        var owner = owner(access, new Tasks(List.of()), null, null,
                (accountId, limit) -> {
                    seen[0] = accountId;
                    seen[1] = limit;
                    return activity(limit);
                });

        var result = (PlatformOperationsQueryFacade.AgentActivityResult)
                owner.query(request(
                        PlatformOperationsQueryFacade.QueryKind.AGENT_ACTIVITY, 3));

        assertThat(seen).containsExactly(7, 3);
        assertThat(result.activities()).hasSize(3);
    }

    @Test
    void providerOrStoreCannotExceedTheRequestedLimit() {
        var taskOwner = owner(new Access("platform.task.read"),
                new Tasks(List.of(task("12", NOW), task("11", NOW.minusSeconds(1)))),
                null, null, null);
        assertThatThrownBy(() -> taskOwner.query(request(
                PlatformOperationsQueryFacade.QueryKind.PERSONAL_TASKS, 1)))
                .isInstanceOf(IllegalStateException.class);

        var activityOwner = owner(new Access("platform.audit.view"),
                new Tasks(List.of()), null, null,
                (accountId, limit) -> activity(2));
        assertThatThrownBy(() -> activityOwner.query(request(
                PlatformOperationsQueryFacade.QueryKind.AGENT_ACTIVITY, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingComposableSourceFailsClosedAfterAuthorization() {
        var owner = owner(new Access("platform.audit.view"),
                new Tasks(List.of()), null, null, null);

        assertCode("PLATFORM_OPERATIONS_SOURCE_UNAVAILABLE", () -> owner.query(
                request(PlatformOperationsQueryFacade.QueryKind.SERVICE_HEALTH, 1)));
    }

    private static PlatformOperationsQueryOwner owner(
            Access access,
            Tasks tasks,
            PlatformOperationsQueryFacade.AiQuotaProvider quota,
            PlatformOperationsQueryFacade.ServiceHealthProvider health,
            PlatformOperationsQueryFacade.AgentActivityProvider activity) {
        return new PlatformOperationsQueryOwner(
                access, tasks, quota, health, activity);
    }

    private static PlatformOperationsQueryFacade.Request request(
            PlatformOperationsQueryFacade.QueryKind kind, int limit) {
        return new PlatformOperationsQueryFacade.Request(7, 3, kind, limit);
    }

    private static PlatformOperationsQueryFacade.PersonalTask task(
            String id, Instant createdAt) {
        return new PlatformOperationsQueryFacade.PersonalTask(
                id, "Follow up " + id, null,
                PlatformTaskFacade.Priority.NORMAL,
                PlatformTaskFacade.Status.OPEN,
                PlatformTaskFacade.Source.AGENT, createdAt);
    }

    private static PlatformOperationsQueryFacade.AiQuotaResult quota() {
        return new PlatformOperationsQueryFacade.AiQuotaResult(
                NOW, NOW.plusSeconds(86_400),
                100, 10, 90, 10_000, 1_000, 500, 8_500,
                4, 1, 3);
    }

    private static PlatformOperationsQueryFacade.ServiceHealthResult health() {
        return new PlatformOperationsQueryFacade.ServiceHealthResult(
                NOW, Arrays.stream(
                        PlatformOperationsQueryFacade.ServiceName.values())
                .map(service -> new PlatformOperationsQueryFacade.ServiceHealth(
                        service, PlatformOperationsQueryFacade.HealthState.UP))
                .toList());
    }

    private static PlatformOperationsQueryFacade.AgentActivityResult activity(
            int size) {
        var values = new java.util.ArrayList<
                PlatformOperationsQueryFacade.AgentActivity>();
        for (var index = 0; index < size; index++) {
            values.add(new PlatformOperationsQueryFacade.AgentActivity(
                    "TURN_COMPLETED", NOW.minusSeconds(index),
                    "PLATFORM_OPERATIONS_QUERY", "OK",
                    "request-" + index, "trace-" + index));
        }
        return new PlatformOperationsQueryFacade.AgentActivityResult(values);
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private static final class Access implements PlatformTaskAccess {
        private long epoch = 3;
        private Set<String> permissions;
        private boolean active = true;
        private int calls;

        private Access(String... permissions) {
            this.permissions = Set.of(permissions);
        }

        @Override
        public LiveAuthorization current(long accountId) {
            calls++;
            assertThat(accountId).isEqualTo(7);
            if (!active) throw new BusinessException(
                    "PLATFORM_TASK_ACCOUNT_UNAVAILABLE", "unavailable",
                    org.springframework.http.HttpStatus.FORBIDDEN);
            return new LiveAuthorization(epoch, permissions);
        }
    }

    private static final class Tasks implements PlatformTaskQueryStore {
        private final List<PlatformOperationsQueryFacade.PersonalTask> result;
        private long accountId;
        private int limit;

        private Tasks(List<PlatformOperationsQueryFacade.PersonalTask> result) {
            this.result = result;
        }

        @Override
        public List<PlatformOperationsQueryFacade.PersonalTask> findOwnTasks(
                long accountId, int limit) {
            this.accountId = accountId;
            this.limit = limit;
            return result;
        }
    }
}
