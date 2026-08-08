package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.domain.WorkActor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkDraftAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void prepareIsReadOnlyOpaqueAndExecuteCreatesExactlyOnceWithExactReplay() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var adapter = adapter(context, store, NOW);

        var prepared = adapter.prepare(WorkAiDraftCommandCodecTest.taskRequest());

        assertThat(store.executions).isZero();
        assertThat(context.factChecks).isEqualTo(1);
        assertThat(prepared.preview().task().title())
                .isEqualTo("Create release task");
        assertThat(prepared.sealedCommand().ciphertext())
                .doesNotContain("Create release task", "proposal-1");
        assertThat(prepared.expiresAt()).isEqualTo(NOW.plusSeconds(900));

        var request = execute(
                WorkAiDraftCommandCodecTest.taskRequest(), prepared,
                "idempotency-1");
        var first = adapter.execute(request);
        var replay = adapter.execute(request);

        assertThat(first).isEqualTo(replay);
        assertThat(first.task().status()).isEqualTo("OPEN");
        assertThat(store.executions).isEqualTo(1);
        assertThat(context.factChecks).isEqualTo(2);
        assertThat(context.authorizations).isEqualTo(2);
    }

    @Test
    void commandExpiryUsesDatabaseMicrosecondPrecisionForExactReplay() {
        var now = Instant.parse("2026-08-04T08:00:00.123456789Z");

        var prepared = adapter(new FakeContext(), new MemoryStore(), now)
                .prepare(WorkAiDraftCommandCodecTest.taskRequest());

        assertThat(prepared.expiresAt())
                .isEqualTo(Instant.parse("2026-08-04T08:15:00.123456Z"));
    }

    @Test
    void livePermissionRevocationWinsBeforeSealedAuthorizationSnapshotCheck() {
        var context = new FakeContext();
        var adapter = adapter(context, new MemoryStore(), NOW);
        var prepared = adapter.prepare(WorkAiDraftCommandCodecTest.taskRequest());
        context.denied = true;
        var original = WorkAiDraftCommandCodecTest.taskRequest();
        var changed = new AiWorkDraftFacade.ExecuteRequest(
                original.proposalId(), original.sessionId(), original.turnId(),
                original.accountId(), original.systemId(), original.tenantId(),
                original.memberId(), original.authorizationEpoch() + 1,
                Set.of("ai.agent.use", "work.task.access"),
                original.operation(), prepared.sealedCommand(),
                "idempotency-1", "execute-1", "execute-trace-1");

        assertCode(() -> adapter.execute(changed),
                "AI_WORK_PERMISSION_DENIED");
    }

    @Test
    void authorizationSnapshotChangesAreStaleAndTamperNeverReachesStore() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var adapter = adapter(context, store, NOW);
        var source = WorkAiDraftCommandCodecTest.taskRequest();
        var prepared = adapter.prepare(source);
        var changedEpoch = new AiWorkDraftFacade.ExecuteRequest(
                source.proposalId(), source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), source.authorizationEpoch() + 1,
                source.effectivePermissions(), source.operation(),
                prepared.sealedCommand(), "idempotency-1",
                "execute-1", "execute-trace-1");
        assertCode(() -> adapter.execute(changedEpoch),
                "AI_WORK_AUTHORIZATION_STALE");

        var badHash = new AiWorkDraftFacade.SealedCommand(
                prepared.sealedCommand().ciphertext(),
                prepared.sealedCommand().encryptionKeyVersion(),
                "0".repeat(64));
        var tampered = new AiWorkDraftFacade.ExecuteRequest(
                source.proposalId(), source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), source.authorizationEpoch(),
                source.effectivePermissions(), source.operation(), badHash,
                "idempotency-1", "execute-1", "execute-trace-1");
        assertCode(() -> adapter.execute(tampered),
                "AI_WORK_DRAFT_COMMAND_INVALID");
        assertThat(store.executions).isZero();
    }

    @Test
    void expiredCommandFailsBeforeAnyOwnerWrite() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var prepared = adapter(context, store, NOW)
                .prepare(WorkAiDraftCommandCodecTest.taskRequest());
        var expiredAdapter = adapter(
                context, store, NOW.plus(Duration.ofMinutes(15)));

        assertCode(() -> expiredAdapter.execute(execute(
                        WorkAiDraftCommandCodecTest.taskRequest(), prepared,
                        "idempotency-1")),
                "AI_WORK_DRAFT_COMMAND_EXPIRED");
        assertThat(store.executions).isZero();
    }

    @Test
    void reportOperationReturnsOnlyDraftReportReadback() {
        var adapter = adapter(new FakeContext(), new MemoryStore(), NOW);
        var source = WorkAiDraftCommandCodecTest.reportRequest();
        var prepared = adapter.prepare(source);

        var result = adapter.execute(execute(
                source, prepared, "idempotency-report"));

        assertThat(result.task()).isNull();
        assertThat(result.report().status()).isEqualTo("DRAFT");
        assertThat(result.report().authorMemberId()).isEqualTo("17");
    }

    private static AiWorkDraftAdapter adapter(
            FakeContext context, MemoryStore store, Instant now) {
        return new AiWorkDraftAdapter(
                context, store, WorkAiDraftCommandSealerTest.sealer(),
                new WorkAiDraftCommandCodec(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    private static AiWorkDraftFacade.ExecuteRequest execute(
            AiWorkDraftFacade.PrepareRequest source,
            AiWorkDraftFacade.PreparedDraft prepared,
            String idempotencyKey) {
        return new AiWorkDraftFacade.ExecuteRequest(
                source.proposalId(), source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), source.authorizationEpoch(),
                source.effectivePermissions(), source.operation(),
                prepared.sealedCommand(), idempotencyKey,
                "execute-1", "execute-trace-1");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }

    private static final class FakeContext
            implements AiWorkDraftAdapter.Context {
        int authorizations;
        int factChecks;
        boolean denied;

        @Override
        public WorkActor validate(
                WorkAiDraftContextReader.Access access,
                AiWorkDraftFacade.TaskDraft task,
                AiWorkDraftFacade.DailyReportDraft report) {
            var actor = authorize(access);
            validateFacts(actor, access.operation(), task, report);
            authorizations--;
            return actor;
        }

        @Override
        public WorkActor authorize(WorkAiDraftContextReader.Access access) {
            authorizations++;
            if (denied) {
                throw new BusinessException(
                        "AI_WORK_PERMISSION_DENIED", "denied",
                        HttpStatus.FORBIDDEN);
            }
            return new WorkActor(access.systemId(), access.tenantId(),
                    access.memberId(), access.effectivePermissions());
        }

        @Override
        public void validateFacts(
                WorkActor actor, AiWorkDraftFacade.Operation operation,
                AiWorkDraftFacade.TaskDraft task,
                AiWorkDraftFacade.DailyReportDraft report) {
            factChecks++;
        }
    }

    private static final class MemoryStore
            implements AiWorkDraftAdapter.ExecutionStore {
        private final Map<String, Entry> entries = new HashMap<>();
        int executions;

        @Override
        public AiWorkDraftFacade.DraftReadback execute(
                WorkAiDraftCommandCodec.Command command,
                WorkActor actor, Runnable liveFactCheck,
                String idempotencyKey, String requestId, String traceId) {
            var current = entries.get(command.proposalId());
            if (current != null) {
                if (!current.idempotencyKey.equals(idempotencyKey)
                        || !current.payloadHash.equals(command.payloadHash())) {
                    throw new BusinessException(
                            "AI_WORK_DRAFT_IDEMPOTENCY_CONFLICT", "conflict",
                            HttpStatus.CONFLICT);
                }
                return current.result;
            }
            liveFactCheck.run();
            executions++;
            var time = Instant.parse("2026-08-04T08:00:01Z");
            AiWorkDraftFacade.DraftReadback result;
            if (command.operation()
                    == AiWorkDraftFacade.Operation.WORK_TASK_DRAFT) {
                var task = command.task();
                result = new AiWorkDraftFacade.DraftReadback(
                        command.operation(),
                        new AiWorkDraftFacade.TaskReadback(
                                "501", 1, task.title(), task.description(),
                                "OPEN", task.assigneeMemberId(),
                                task.projectId(), task.dueAt(), time, time),
                        null);
            } else {
                var report = command.report();
                result = new AiWorkDraftFacade.DraftReadback(
                        command.operation(), null,
                        new AiWorkDraftFacade.DailyReportReadback(
                                "601", 1, Long.toString(actor.memberId()),
                                report.workDate(), report.completedWork(),
                                report.plannedWork(), report.blockers(),
                                "DRAFT", time, time));
            }
            entries.put(command.proposalId(), new Entry(
                    idempotencyKey, command.payloadHash(), result));
            return result;
        }

        private record Entry(
                String idempotencyKey, String payloadHash,
                AiWorkDraftFacade.DraftReadback result) { }
    }
}
