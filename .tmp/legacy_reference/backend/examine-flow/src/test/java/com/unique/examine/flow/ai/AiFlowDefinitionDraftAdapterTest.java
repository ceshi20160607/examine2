package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFlowDefinitionDraftAdapterTest {
    private static final Instant NOW = Instant.parse(
            "2026-08-04T08:00:00.123456789Z");

    @Test
    void prepareWritesNothingAndExecuteCreatesOnceWithExactReplay() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var adapter = adapter(context, store, NOW);

        var prepared = adapter.prepare(
                FlowAiDefinitionDraftCommandTest.request());
        assertThat(store.executions).isZero();
        assertThat(prepared.preview().draft().name())
                .isEqualTo("Expense approval");
        assertThat(prepared.sealedCommand().ciphertext())
                .doesNotContain("Expense approval", "proposal-1");
        assertThat(prepared.expiresAt()).isEqualTo(
                NOW.plusSeconds(900).truncatedTo(ChronoUnit.MICROS));

        var execute = execute(prepared, 7,
                Set.of("flow.definition.manage"));
        var first = adapter.execute(execute);
        var replay = adapter.execute(execute);
        assertThat(replay).isEqualTo(first);
        assertThat(first.published()).isFalse();
        assertThat(first.definitionId()).isEqualTo("501");
        assertThat(store.executions).isEqualTo(1);
        assertThat(context.factChecks).isEqualTo(3);
    }

    @Test
    void livePermissionRevocationWinsAndAuthorizationChangeIsStale() {
        var context = new FakeContext();
        var prepared = adapter(context, new MemoryStore(), NOW)
                .prepare(FlowAiDefinitionDraftCommandTest.request());
        context.denied = true;
        assertCode(() -> adapter(context, new MemoryStore(), NOW).execute(
                        execute(prepared, 8, Set.of())),
                "AI_FLOW_PERMISSION_DENIED");

        context.denied = false;
        assertCode(() -> adapter(context, new MemoryStore(), NOW).execute(
                        execute(prepared, 8,
                                Set.of("flow.definition.manage"))),
                "AI_FLOW_AUTHORIZATION_STALE");
    }

    @Test
    void hashTamperAndExpiryNeverReachOwnerStore() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var prepared = adapter(context, store, NOW)
                .prepare(FlowAiDefinitionDraftCommandTest.request());
        var source = execute(prepared, 7,
                Set.of("flow.definition.manage"));
        var badHash = new AiFlowDefinitionDraftFacade.SealedCommand(
                source.sealedCommand().ciphertext(),
                source.sealedCommand().encryptionKeyVersion(),
                "0".repeat(64));
        var tampered = new AiFlowDefinitionDraftFacade.ExecuteRequest(
                source.proposalId(), source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), source.authorizationEpoch(),
                source.effectivePermissions(), source.operation(), badHash,
                source.idempotencyKey(), source.requestId(), source.traceId());
        assertCode(() -> adapter(context, store, NOW).execute(tampered),
                "AI_FLOW_DRAFT_COMMAND_INVALID");
        assertCode(() -> adapter(
                context, store, NOW.plusSeconds(900)).execute(source),
                "AI_FLOW_DRAFT_COMMAND_EXPIRED");
        assertThat(store.executions).isZero();
    }

    private static AiFlowDefinitionDraftAdapter adapter(
            FakeContext context, MemoryStore store, Instant now) {
        return new AiFlowDefinitionDraftAdapter(
                context, store, FlowAiDefinitionDraftCommandTest.sealer(),
                new FlowAiDefinitionDraftCommandCodec(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    private static AiFlowDefinitionDraftFacade.ExecuteRequest execute(
            AiFlowDefinitionDraftFacade.PreparedDraft prepared,
            long epoch, Set<String> permissions) {
        var source = FlowAiDefinitionDraftCommandTest.request();
        return new AiFlowDefinitionDraftFacade.ExecuteRequest(
                source.proposalId(), source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), epoch, permissions, source.operation(),
                prepared.sealedCommand(), "idempotency-1",
                "execute-request", "execute-trace");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }

    private static final class FakeContext
            implements AiFlowDefinitionDraftAdapter.Context {
        int factChecks;
        boolean denied;

        @Override
        public FlowSession validate(
                FlowAiDefinitionDraftContextReader.Access access,
                AiFlowDefinitionDraftFacade.Draft draft) {
            var session = authorize(access);
            validateFacts(session, draft);
            return session;
        }

        @Override
        public FlowSession authorize(
                FlowAiDefinitionDraftContextReader.Access access) {
            if (denied) {
                throw new BusinessException(
                        "AI_FLOW_PERMISSION_DENIED", "denied",
                        HttpStatus.FORBIDDEN);
            }
            return new FlowSession(
                    access.accountId(), access.systemId(), access.tenantId(),
                    access.memberId(), access.effectivePermissions());
        }

        @Override
        public void validateFacts(
                FlowSession session,
                AiFlowDefinitionDraftFacade.Draft draft) {
            factChecks++;
        }
    }

    private static final class MemoryStore
            implements AiFlowDefinitionDraftAdapter.ExecutionStore {
        private final Map<String, Entry> values = new HashMap<>();
        int executions;

        @Override
        public AiFlowDefinitionDraftFacade.DefinitionReadback execute(
                FlowAiDefinitionDraftCommandCodec.Command command,
                FlowSession session, Runnable liveFactCheck,
                String idempotencyKey, String requestId, String traceId) {
            var existing = values.get(command.proposalId());
            if (existing != null) return existing.result;
            liveFactCheck.run();
            executions++;
            var result = new AiFlowDefinitionDraftFacade.DefinitionReadback(
                    command.operation(), "501", command.draft().name(),
                    command.draft().approverMemberIds(), 0, NOW, false);
            values.put(command.proposalId(), new Entry(
                    idempotencyKey, command.payloadHash(), result));
            return result;
        }

        private record Entry(
                String key, String hash,
                AiFlowDefinitionDraftFacade.DefinitionReadback result) { }
    }
}
