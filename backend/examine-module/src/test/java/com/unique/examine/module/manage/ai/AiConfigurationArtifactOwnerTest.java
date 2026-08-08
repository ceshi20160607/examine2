package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactOwnerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void preparesSelectionAndPagePreviewsWithZeroWrites() {
        var fixture = fixture();

        var selection = fixture.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest());
        var page = fixture.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.pageRequest());

        assertThat(fixture.store.writes).isZero();
        assertThat(selection.preview().operation()).isEqualTo(
                AiConfigurationArtifactFacade.Operation
                        .CONFIG_SELECTION_FIELD_DRAFT);
        assertThat(selection.preview().selectionField().sortOrder()).isEqualTo(20);
        assertThat(selection.preview().nextDraftRevision()).isEqualTo(6);
        assertThat(page.preview().pageLayout().pageId()).isEqualTo("71");
        assertThat(page.preview().pageLayout().pageVersion()).isEqualTo(2);
        assertThat(selection.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(selection.sealedCommand().ciphertext())
                .doesNotContain("Priority", "High", "session-1");
    }

    @Test
    void exactAndConcurrentReplayWriteOneArtifactAndChangedReplayConflicts()
            throws Exception {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest());
        var request = execute(prepared, "idem-1", 3, permissions());

        var first = fixture.owner.execute(request);
        var replay = fixture.owner.execute(request);
        assertThat(replay).isEqualTo(first);
        assertThat(fixture.store.writes).isOne();

        var changed = fixture.owner.prepare(pageRequest(
                "proposal-2", "turn-2"));
        assertCode("AI_CONFIG_ARTIFACT_IDEMPOTENCY_CONFLICT", () ->
                fixture.owner.execute(execute(
                        changed, "idem-1", 3, permissions())));

        var concurrent = fixture();
        var concurrentPrepared = concurrent.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest());
        var concurrentRequest = execute(
                concurrentPrepared, "idem-concurrent", 3, permissions());
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<java.util.concurrent.Callable<String>>();
            for (var index = 0; index < 16; index++) {
                calls.add(() -> concurrent.owner.execute(
                        concurrentRequest).selectionField().field().fieldId());
            }
            var ids = executor.invokeAll(calls).stream().map(future -> {
                try { return future.get(); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            }).toList();
            assertThat(ids).containsOnly(ids.getFirst());
        }
        assertThat(concurrent.store.writes).isOne();
    }

    @Test
    void executeUsesCurrentAuthorizationAndRejectsRevocationOrStaleDraft() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.pageRequest());
        fixture.context.epoch = 4;
        var refreshed = Set.of(
                "system.admin.access", "module.config.manage", "new.permission");
        fixture.context.permissions = refreshed;
        var result = fixture.owner.execute(execute(prepared, "idem-page", 4, refreshed));
        assertThat(result.pageLayout().version()).isEqualTo(3);
        assertThat(fixture.store.lastAccess.authorizationEpoch()).isEqualTo(4);

        var revoked = fixture();
        var revokedPrepared = revoked.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest());
        revoked.context.epoch = 4;
        revoked.context.permissions = Set.of("system.admin.access");
        assertCode("AI_CONFIG_PERMISSION_DENIED", () -> revoked.owner.execute(
                execute(revokedPrepared, "idem-revoked", 4,
                        Set.of("system.admin.access"))));
        assertThat(revoked.store.writes).isZero();

        var stale = fixture();
        var stalePrepared = stale.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest());
        stale.context.revision = 6;
        assertCode("AI_CONFIG_DRAFT_STALE", () -> stale.owner.execute(
                execute(stalePrepared, "idem-stale", 3, permissions())));
        assertThat(stale.store.writes).isZero();
    }

    @Test
    void conflictsTamperWrongBindingAndExpiryAlwaysWriteNothing() {
        var conflict = fixture();
        conflict.context.fieldConflict = true;
        assertCode("AI_CONFIG_FIELD_CODE_CONFLICT", () -> conflict.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest()));
        conflict.context.fieldConflict = false;
        conflict.context.dictionaryConflict = true;
        assertCode("AI_CONFIG_DICTIONARY_CODE_CONFLICT", () ->
                conflict.owner.prepare(
                        AiConfigurationArtifactCommandCodecTest.selectionRequest()));

        var fixture = fixture();
        var prepared = fixture.owner.prepare(
                AiConfigurationArtifactCommandCodecTest.selectionRequest());
        var command = prepared.sealedCommand();
        var hash = new AiConfigurationArtifactFacade.SealedCommand(
                command.ciphertext(), command.encryptionKeyVersion(), "f".repeat(64));
        assertCode("AI_CONFIG_ARTIFACT_COMMAND_INVALID", () ->
                fixture.owner.execute(execute(
                        prepared.preview(), hash, "proposal-1", "session-1",
                        "turn-1", "hash", 3, permissions())));
        assertCode("AI_CONFIG_ARTIFACT_COMMAND_INVALID", () ->
                fixture.owner.execute(execute(
                        prepared.preview(), command, "proposal-1", "session-2",
                        "turn-1", "binding", 3, permissions())));
        fixture.clock.now = prepared.expiresAt();
        assertCode("AI_CONFIG_ARTIFACT_COMMAND_EXPIRED", () ->
                fixture.owner.execute(execute(prepared, "expired", 3, permissions())));
        assertThat(fixture.store.writes).isZero();
    }

    private static Fixture fixture() {
        var context = new Context();
        var store = new Store(context);
        var clock = new MutableClock(NOW);
        var owner = new AiConfigurationArtifactOwner(
                context, store,
                new AiConfigurationArtifactCommandSealer(
                        keys(), new java.security.SecureRandom()),
                new AiConfigurationArtifactCommandCodec(new ObjectMapper()),
                clock, Duration.ofMinutes(15));
        return new Fixture(owner, context, store, clock);
    }

    private static AiConfigurationArtifactFacade.PrepareRequest pageRequest(
            String proposalId, String turnId) {
        var value = AiConfigurationArtifactCommandCodecTest.pageRequest();
        return new AiConfigurationArtifactFacade.PrepareRequest(
                proposalId, value.sessionId(), turnId,
                value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.authorizationEpoch(),
                value.effectivePermissions(), value.moduleCode(), value.operation(),
                value.selectionField(), value.pageLayout(), value.policyVersionId(),
                value.providerId(), value.providerVersion(), value.promptVersion(),
                value.requestId(), value.traceId());
    }

    private static AiConfigurationArtifactFacade.ExecuteRequest execute(
            AiConfigurationArtifactFacade.PreparedArtifact prepared,
            String key, long epoch, Set<String> permissions) {
        var proposal = prepared.preview().operation()
                == AiConfigurationArtifactFacade.Operation
                .CONFIG_SELECTION_FIELD_DRAFT ? "proposal-1" : "proposal-2";
        var turn = prepared.preview().operation()
                == AiConfigurationArtifactFacade.Operation
                .CONFIG_SELECTION_FIELD_DRAFT ? "turn-1" : "turn-2";
        return execute(prepared.preview(), prepared.sealedCommand(), proposal,
                "session-1", turn, key, epoch, permissions);
    }

    private static AiConfigurationArtifactFacade.ExecuteRequest execute(
            AiConfigurationArtifactFacade.ArtifactPreview preview,
            AiConfigurationArtifactFacade.SealedCommand command,
            String proposalId, String sessionId, String turnId, String key,
            long epoch, Set<String> permissions) {
        return new AiConfigurationArtifactFacade.ExecuteRequest(
                proposalId, sessionId, turnId, 7, 11, 21, 17, epoch,
                permissions, preview.configRootId(), preview.moduleId(),
                preview.moduleCode(), preview.expectedDraftRevision(), command,
                key, "request-2", "trace-2");
    }

    private static Set<String> permissions() {
        return AiConfigurationArtifactCommandCodecTest.permissions();
    }

    private static SensitiveKeyProvider keys() {
        return () -> Optional.of(new SensitiveKeyProvider.KeyRing(
                "v1", "h1", Map.of("v1", "k".repeat(32).getBytes()),
                Map.of(), List.of()));
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private record Fixture(
            AiConfigurationArtifactOwner owner,
            Context context,
            Store store,
            MutableClock clock) { }

    private static final class Context
            implements AiConfigurationArtifactOwner.ContextResolver {
        private long revision = 5;
        private long epoch = 3;
        private long pageVersion = 2;
        private Set<String> permissions = permissions();
        private boolean fieldConflict;
        private boolean dictionaryConflict;
        private final Set<String> fields = Set.of("name", "priority");

        @Override
        public synchronized AiConfigurationArtifactContextReader.CommonSnapshot
        common(AiConfigurationArtifactContextReader.Access access) {
            if (access.authorizationEpoch() != epoch
                    || !access.effectivePermissions().equals(permissions)) {
                throw new BusinessException(
                        "AI_CONFIG_AUTHORIZATION_STALE", "stale",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            if (!permissions.containsAll(permissions())) {
                throw new BusinessException(
                        "AI_CONFIG_PERMISSION_DENIED", "denied",
                        org.springframework.http.HttpStatus.FORBIDDEN);
            }
            return new AiConfigurationArtifactContextReader.CommonSnapshot(
                    31, 41, "customers", revision, epoch, permissions);
        }

        @Override
        public synchronized AiConfigurationArtifactContextReader.SelectionSnapshot
        selection(AiConfigurationArtifactContextReader.Access access,
                  String fieldCode, String dictionaryCode) {
            return new AiConfigurationArtifactContextReader.SelectionSnapshot(
                    common(access), 20, fieldConflict, dictionaryConflict);
        }

        @Override
        public synchronized AiConfigurationArtifactContextReader.PageSnapshot page(
                AiConfigurationArtifactContextReader.Access access,
                String pageCode, Set<String> fieldCodes) {
            if (!fields.containsAll(fieldCodes)) {
                throw new BusinessException(
                        "AI_CONFIG_PAGE_FIELD_UNAVAILABLE", "field",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            return new AiConfigurationArtifactContextReader.PageSnapshot(
                    common(access), 71, "form",
                    AiConfigurationArtifactFacade.PageType.FORM,
                    pageVersion, fieldCodes);
        }
    }

    private static final class Store
            implements AiConfigurationArtifactOwner.MutationStore {
        private final Context context;
        private final LinkedHashMap<String, Entry> rows = new LinkedHashMap<>();
        private int writes;
        private AiConfigurationArtifactContextReader.Access lastAccess;

        private Store(Context context) { this.context = context; }

        @Override
        public synchronized AiConfigurationArtifactFacade.ArtifactReadback execute(
                AiConfigurationArtifactCommandCodec.Command command,
                AiConfigurationArtifactContextReader.Access currentAccess,
                String key, String requestId, String traceId) {
            lastAccess = currentAccess;
            var existing = rows.get(key);
            if (existing != null) {
                if (!existing.command.equals(command)) {
                    throw new BusinessException(
                            "AI_CONFIG_ARTIFACT_IDEMPOTENCY_CONFLICT", "conflict",
                            org.springframework.http.HttpStatus.CONFLICT);
                }
                return existing.result;
            }
            if (context.revision != command.expectedDraftRevision()) {
                throw new BusinessException(
                        "AI_CONFIG_DRAFT_STALE", "stale",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            var result = result(command);
            rows.put(key, new Entry(command, result));
            context.revision++;
            if (command.pageLayout() != null) context.pageVersion++;
            writes++;
            return result;
        }

        private AiConfigurationArtifactFacade.ArtifactReadback result(
                AiConfigurationArtifactCommandCodec.Command command) {
            if (command.selectionField() != null) {
                var draft = command.selectionField().draft();
                var options = new ArrayList<AiConfigurationArtifactFacade.OptionView>();
                for (var index = 0; index < draft.options().size(); index++) {
                    var value = draft.options().get(index);
                    options.add(new AiConfigurationArtifactFacade.OptionView(
                            Long.toString(91 + index), value.code(), value.label(),
                            value.semanticKey(), value.color(), value.defaultOption(),
                            index * 10, 0));
                }
                return new AiConfigurationArtifactFacade.ArtifactReadback(
                        command.operation(), "31", "41", "customers", 6,
                        new AiConfigurationArtifactFacade.SelectionFieldReadback(
                                new AiConfigurationArtifactFacade.DictionaryView(
                                        "81", draft.dictionaryCode(),
                                        draft.dictionaryName(), 0), options,
                                new AiConfigurationArtifactFacade.SelectionFieldView(
                                        "101", draft.fieldCode(), draft.fieldName(),
                                        draft.fieldType(), draft.required(), "81",
                                        command.selectionField().sortOrder(),
                                        draft.maxSelections(), 0)), null);
            }
            return new AiConfigurationArtifactFacade.ArtifactReadback(
                    command.operation(), "31", "41", "customers", 6, null,
                    new AiConfigurationArtifactFacade.PageLayoutReadback(
                            "71", "form", AiConfigurationArtifactFacade.PageType.FORM,
                            command.pageLayout().pageVersion() + 1,
                            command.pageLayout().layout()));
        }

        private record Entry(
                AiConfigurationArtifactCommandCodec.Command command,
                AiConfigurationArtifactFacade.ArtifactReadback result) { }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
