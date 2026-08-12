package com.unique.examine.plat.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTaskOwnerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void prepareNormalizesSelfAssignedPreviewAndWritesNothing() {
        var fixture = fixture();

        var prepared = fixture.owner.prepare(prepare(
                "proposal-1", " Ｆｏｌｌｏｗ   up "));

        assertThat(fixture.tasks.writes).isZero();
        assertThat(prepared.preview().accountId()).isEqualTo(7);
        assertThat(prepared.preview().title()).isEqualTo("Follow up");
        assertThat(prepared.preview().status()).isEqualTo(
                PlatformTaskFacade.Status.OPEN);
        assertThat(prepared.preview().source()).isEqualTo(
                PlatformTaskFacade.Source.AGENT);
        assertThat(prepared.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(prepared.sealedCommand().ciphertext())
                .doesNotContain("Follow", "description");
    }

    @Test
    void executeCreatesOneTaskAndExactReplayReturnsTheSameOwnerRow() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare("proposal-1", "Follow up"));
        var request = execute("proposal-1", prepared.sealedCommand(), "idem-1");

        var first = fixture.owner.execute(request);
        var replay = fixture.owner.execute(request);

        assertThat(replay).isEqualTo(first);
        assertThat(fixture.tasks.writes).isOne();
        assertThat(first.accountId()).isEqualTo(7);
        assertThat(first.createdBy()).isEqualTo(7);
        assertThat(first.status()).isEqualTo(PlatformTaskFacade.Status.OPEN);
        assertThat(first.source()).isEqualTo(PlatformTaskFacade.Source.AGENT);
        assertThat(fixture.tasks.rows.values().iterator().next().idempotencyKey())
                .isEqualTo("idem-1");
    }

    @Test
    void changedReplayConflictsAndConcurrentReplayCreatesAtMostOneTask()
            throws Exception {
        var fixture = fixture();
        var first = fixture.owner.prepare(prepare("proposal-1", "First"));
        fixture.owner.execute(execute(
                "proposal-1", first.sealedCommand(), "shared-key"));
        var changed = fixture.owner.prepare(prepare("proposal-2", "Changed"));

        assertCode("PLATFORM_TASK_IDEMPOTENCY_CONFLICT", () ->
                fixture.owner.execute(execute(
                        "proposal-2", changed.sealedCommand(), "shared-key")));

        var concurrent = fixture();
        var prepared = concurrent.owner.prepare(prepare(
                "proposal-concurrent", "Concurrent"));
        var request = execute(
                "proposal-concurrent", prepared.sealedCommand(), "concurrent-key");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<java.util.concurrent.Callable<String>>();
            for (var index = 0; index < 16; index++) {
                calls.add(() -> concurrent.owner.execute(request).taskId());
            }
            var ids = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try { return future.get(); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }).toList();
            assertThat(ids).containsOnly(ids.getFirst());
        }
        assertThat(concurrent.tasks.writes).isOne();
    }

    @Test
    void executeRechecksLivePermissionsEpochAndAccount() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare("proposal-1", "Follow up"));
        var request = execute("proposal-1", prepared.sealedCommand(), "idem-1");

        fixture.access.permissions = Set.of("platform.ai.agent.use");
        assertCode("PLATFORM_TASK_PERMISSION_DENIED", () ->
                fixture.owner.execute(request));
        fixture.access.permissions = permissions();
        fixture.access.epoch = 4;
        assertCode("PLATFORM_TASK_AUTHORIZATION_STALE", () ->
                fixture.owner.execute(request));
        fixture.access.epoch = 3;
        fixture.access.active = false;
        assertCode("PLATFORM_TASK_ACCOUNT_UNAVAILABLE", () ->
                fixture.owner.execute(request));
        assertThat(fixture.tasks.writes).isZero();
    }

    @Test
    void tamperedHashCiphertextBindingAndExpiredCommandCreateNothing() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare("proposal-1", "Follow up"));
        var command = prepared.sealedCommand();
        var changedHash = new PlatformTaskFacade.SealedCommand(
                command.ciphertext(), command.encryptionKeyVersion(), "f".repeat(64));
        assertCode("PLATFORM_TASK_COMMAND_INVALID", () -> fixture.owner.execute(
                execute("proposal-1", changedHash, "hash")));

        var first = command.ciphertext().charAt(0) == 'A' ? 'B' : 'A';
        var changedCiphertext = new PlatformTaskFacade.SealedCommand(
                first + command.ciphertext().substring(1),
                command.encryptionKeyVersion(), command.commandSha256());
        assertCode("PLATFORM_TASK_COMMAND_INVALID", () -> fixture.owner.execute(
                execute("proposal-1", changedCiphertext, "cipher")));
        assertCode("PLATFORM_TASK_COMMAND_INVALID", () -> fixture.owner.execute(
                execute("proposal-2", command, "binding")));

        fixture.clock.now = prepared.expiresAt();
        assertCode("PLATFORM_TASK_COMMAND_EXPIRED", () -> fixture.owner.execute(
                execute("proposal-1", command, "expired")));
        assertThat(fixture.tasks.writes).isZero();
    }

    private static Fixture fixture() {
        var access = new Access();
        var tasks = new MemoryTasks();
        PlatformSecretResolverFacade secrets = request -> java.util.Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8(
                        "platform-task-owner-test-key-material"));
        var sealer = new PlatformTaskCommandSealer(
                secrets, "env://PLATFORM_TASK_TEST_KEY",
                new java.security.SecureRandom());
        var json = new ObjectMapper();
        var clock = new MutableClock(NOW);
        var sequence = new AtomicLong(100);
        var ids = new IdService() {
            @Override public long nextId() { return sequence.getAndIncrement(); }
        };
        var owner = new PlatformTaskOwner(
                access, tasks, sealer, new PlatformTaskCommandCodec(json),
                ids, clock, Duration.ofMinutes(15));
        return new Fixture(owner, access, tasks, clock);
    }

    private static PlatformTaskFacade.PrepareRequest prepare(
            String proposalId, String title) {
        return new PlatformTaskFacade.PrepareRequest(
                proposalId, 7, 3,
                new PlatformTaskFacade.TaskDraft(
                        title, " Personal description ", NOW.plusSeconds(3_600),
                        PlatformTaskFacade.Priority.HIGH),
                "request-1", "trace-1");
    }

    private static PlatformTaskFacade.ExecuteRequest execute(
            String proposalId,
            PlatformTaskFacade.SealedCommand command,
            String key) {
        return new PlatformTaskFacade.ExecuteRequest(
                proposalId, 7, 3, command, key, "request-2", "trace-2");
    }

    private static Set<String> permissions() {
        return Set.of("platform.ai.agent.use", "platform.task.create");
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private record Fixture(
            PlatformTaskOwner owner,
            Access access,
            MemoryTasks tasks,
            MutableClock clock) { }

    private static final class Access implements PlatformTaskAccess {
        private long epoch = 3;
        private Set<String> permissions = permissions();
        private boolean active = true;

        @Override
        public LiveAuthorization current(long accountId) {
            if (!active) throw new BusinessException(
                    "PLATFORM_TASK_ACCOUNT_UNAVAILABLE", "unavailable",
                    org.springframework.http.HttpStatus.FORBIDDEN);
            return new LiveAuthorization(epoch, permissions);
        }
    }

    private static final class MemoryTasks implements PlatformTaskStore {
        private final LinkedHashMap<String, PlatformTask> rows =
                new LinkedHashMap<>();
        private int writes;

        @Override
        public synchronized PlatformTask createOrReplay(PlatformTask candidate) {
            var key = candidate.accountId() + ":" + candidate.idempotencyKey();
            var current = rows.get(key);
            if (current != null) return current;
            rows.put(key, candidate);
            writes++;
            return candidate;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
