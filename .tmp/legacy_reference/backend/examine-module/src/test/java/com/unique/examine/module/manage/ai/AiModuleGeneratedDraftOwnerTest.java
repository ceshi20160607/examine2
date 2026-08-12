package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.domain.ReportActor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModuleGeneratedDraftOwnerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void prepareIsZeroWriteAndExecuteCreatesOnceWithExactReplay() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var owner = owner(context, store, NOW);
        var source = AiModuleGeneratedDraftCommandCodecTest.reportRequest();

        var prepared = owner.prepare(source);
        assertThat(store.executions).isZero();
        assertThat(prepared.preview().report().name())
                .isEqualTo("Quarterly report");
        assertThat(prepared.sealedCommand().ciphertext())
                .doesNotContain("Quarterly report", "proposal-report");

        var execute = execute(source, prepared, "idem-report");
        var first = owner.execute(execute);
        var replay = owner.execute(execute);

        assertThat(first).isEqualTo(replay);
        assertThat(first.report().published()).isFalse();
        assertThat(store.executions).isEqualTo(1);
        assertThat(context.factChecks).isEqualTo(2);
    }

    @Test
    void printResultIsDisabledAndExpiredOrDeniedCommandsNeverWrite() {
        var context = new FakeContext();
        var store = new MemoryStore();
        var source = AiModuleGeneratedDraftCommandCodecTest.printRequest();
        var prepared = owner(context, store, NOW).prepare(source);
        var result = owner(context, store, NOW).execute(
                execute(source, prepared, "idem-print"));
        assertThat(result.printTemplate().status()).isEqualTo("DISABLED");

        var expiredStore = new MemoryStore();
        assertCode(() -> owner(new FakeContext(), expiredStore,
                NOW.plusSeconds(900)).execute(
                execute(source, prepared, "idem-expired")),
                "AI_MODULE_DRAFT_COMMAND_EXPIRED");
        assertThat(expiredStore.executions).isZero();

        var denied = new FakeContext();
        denied.denied = true;
        assertCode(() -> owner(denied, new MemoryStore(), NOW).execute(
                execute(source, prepared, "idem-denied")),
                "AI_MODULE_DRAFT_PERMISSION_DENIED");
    }

    private static AiModuleGeneratedDraftOwner owner(
            FakeContext context, MemoryStore store, Instant now) {
        return new AiModuleGeneratedDraftOwner(
                context, store,
                AiModuleGeneratedDraftCommandSealerTest.sealer(),
                new AiModuleGeneratedDraftCommandCodec(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    private static AiModuleGeneratedDraftFacade.ExecuteRequest execute(
            AiModuleGeneratedDraftFacade.PrepareRequest source,
            AiModuleGeneratedDraftFacade.PreparedDraft prepared,
            String idempotencyKey) {
        return new AiModuleGeneratedDraftFacade.ExecuteRequest(
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
            implements AiModuleGeneratedDraftOwner.Context {
        int factChecks;
        boolean denied;

        @Override
        public AiModuleGeneratedDraftContextReader.OwnerContext validate(
                AiModuleGeneratedDraftContextReader.Access access,
                AiModuleGeneratedDraftFacade.ReportDraft report,
                AiModuleGeneratedDraftFacade.PrintTemplateDraft printTemplate) {
            var context = authorize(access);
            validateFacts(context, access.operation(), report, printTemplate);
            return context;
        }

        @Override
        public AiModuleGeneratedDraftContextReader.OwnerContext authorize(
                AiModuleGeneratedDraftContextReader.Access access) {
            if (denied) {
                throw new BusinessException(
                        "AI_MODULE_DRAFT_PERMISSION_DENIED", "denied",
                        HttpStatus.FORBIDDEN);
            }
            return new AiModuleGeneratedDraftContextReader.OwnerContext(
                    new ConfigSession(
                            access.accountId(), access.systemId(),
                            access.memberId(), access.tenantId(),
                            access.effectivePermissions()),
                    new ReportActor(
                            access.systemId(), access.tenantId(),
                            access.memberId()));
        }

        @Override
        public void validateFacts(
                AiModuleGeneratedDraftContextReader.OwnerContext context,
                AiModuleGeneratedDraftFacade.Operation operation,
                AiModuleGeneratedDraftFacade.ReportDraft report,
                AiModuleGeneratedDraftFacade.PrintTemplateDraft printTemplate) {
            factChecks++;
        }
    }

    private static final class MemoryStore
            implements AiModuleGeneratedDraftOwner.ExecutionStore {
        private final Map<String, Entry> entries = new HashMap<>();
        int executions;

        @Override
        public AiModuleGeneratedDraftFacade.DraftReadback execute(
                AiModuleGeneratedDraftCommandCodec.Command command,
                AiModuleGeneratedDraftContextReader.OwnerContext context,
                Runnable liveFactCheck,
                String idempotencyKey,
                String requestId,
                String traceId) {
            var existing = entries.get(command.proposalId());
            if (existing != null) {
                if (!existing.idempotencyKey().equals(idempotencyKey)
                        || !existing.payloadHash().equals(command.payloadHash())) {
                    throw new BusinessException(
                            "AI_MODULE_DRAFT_IDEMPOTENCY_CONFLICT", "conflict",
                            HttpStatus.CONFLICT);
                }
                return existing.result();
            }
            liveFactCheck.run();
            executions++;
            AiModuleGeneratedDraftFacade.DraftReadback result;
            if (command.operation() == AiModuleGeneratedDraftFacade.Operation
                    .CONFIG_REPORT_DRAFT) {
                var draft = command.report();
                result = new AiModuleGeneratedDraftFacade.DraftReadback(
                        command.operation(),
                        new AiModuleGeneratedDraftFacade.ReportReadback(
                                "501", draft.code(), draft.name(),
                                draft.description(), draft.dataSourceId(),
                                draft.outputFieldCodes(), 1, 1,
                                NOW, NOW, false), null);
            } else {
                var draft = command.printTemplate();
                result = new AiModuleGeneratedDraftFacade.DraftReadback(
                        command.operation(), null,
                        new AiModuleGeneratedDraftFacade.PrintTemplateReadback(
                                "601", "31", draft.moduleCode(), draft.code(),
                                draft.name(), draft.paperSize(),
                                draft.orientation(), draft.title(),
                                draft.fieldCodes(), draft.footer(), "DISABLED",
                                0, NOW, false));
            }
            entries.put(command.proposalId(), new Entry(
                    idempotencyKey, command.payloadHash(), result));
            return result;
        }

        private record Entry(
                String idempotencyKey,
                String payloadHash,
                AiModuleGeneratedDraftFacade.DraftReadback result) { }
    }
}
