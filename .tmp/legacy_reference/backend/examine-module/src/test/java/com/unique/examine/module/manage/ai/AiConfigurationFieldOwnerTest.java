package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
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

class AiConfigurationFieldOwnerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void prepareResolvesAndSealsCurrentDraftWithoutWriting() {
        var fixture = fixture();

        var prepared = fixture.owner.prepare(prepare(
                "proposal-1", "age", "  Age   in years  ",
                AiConfigurationFieldFacade.FieldType.INTEGER));

        assertThat(fixture.store.writes).isZero();
        assertThat(prepared.preview().configRootId()).isEqualTo("31");
        assertThat(prepared.preview().moduleId()).isEqualTo("41");
        assertThat(prepared.preview().moduleCode()).isEqualTo("customers");
        assertThat(prepared.preview().expectedDraftRevision()).isEqualTo(5);
        assertThat(prepared.preview().nextDraftRevision()).isEqualTo(6);
        assertThat(prepared.preview().field().fieldName())
                .isEqualTo("Age in years");
        assertThat(prepared.preview().field().settings().precision()).isEqualTo(38);
        assertThat(prepared.preview().field().settings().scale()).isZero();
        assertThat(prepared.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(prepared.sealedCommand().ciphertext())
                .doesNotContain("customers", "Age", "policy");
    }

    @Test
    void executeReplaysExactlyAndChangedOrConcurrentReplayCreatesOneField()
            throws Exception {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare(
                "proposal-1", "age", "Age",
                AiConfigurationFieldFacade.FieldType.INTEGER));
        var request = execute("proposal-1", prepared, "idem-1");

        var first = fixture.owner.execute(request);
        var replay = fixture.owner.execute(request);

        assertThat(replay).isEqualTo(first);
        assertThat(fixture.store.writes).isOne();
        assertThat(fixture.context.revision).isEqualTo(6);

        var changed = fixture.owner.prepare(prepare(
                "proposal-2", "score", "Score",
                AiConfigurationFieldFacade.FieldType.DECIMAL));
        assertCode("AI_CONFIG_FIELD_IDEMPOTENCY_CONFLICT", () ->
                fixture.owner.execute(execute(
                        "proposal-2", changed, "idem-1")));

        var concurrent = fixture();
        var preparedConcurrent = concurrent.owner.prepare(prepare(
                "proposal-c", "age", "Age",
                AiConfigurationFieldFacade.FieldType.INTEGER));
        var concurrentRequest = execute(
                "proposal-c", preparedConcurrent, "idem-c");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<java.util.concurrent.Callable<String>>();
            for (var index = 0; index < 16; index++) {
                calls.add(() -> concurrent.owner.execute(
                        concurrentRequest).field().fieldId());
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
    void concurrentDraftEditMakesConfirmationStaleAndWritesNothing() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare(
                "proposal-1", "age", "Age",
                AiConfigurationFieldFacade.FieldType.INTEGER));
        fixture.context.revision = 6;

        assertCode("AI_CONFIG_DRAFT_STALE", () -> fixture.owner.execute(
                execute("proposal-1", prepared, "idem-stale")));
        assertThat(fixture.store.writes).isZero();
    }

    @Test
    void executeRechecksLiveEpochPermissionsAndModuleIdentity() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare(
                "proposal-1", "age", "Age",
                AiConfigurationFieldFacade.FieldType.INTEGER));
        fixture.context.epoch = 4;
        var refreshed = execute(
                "proposal-1", prepared.preview(), prepared.sealedCommand(),
                "idem-1", 4, permissions());
        var result = fixture.owner.execute(refreshed);
        assertThat(result.draftRevision()).isEqualTo(6);
        assertThat(fixture.store.lastAccess.authorizationEpoch()).isEqualTo(4);

        var revoked = fixture();
        var revokedPrepared = revoked.owner.prepare(prepare(
                "proposal-2", "score", "Score",
                AiConfigurationFieldFacade.FieldType.DECIMAL));
        revoked.context.epoch = 4;
        revoked.context.permissions = Set.of("system.admin.access");
        var currentRevoked = execute(
                "proposal-2", revokedPrepared.preview(),
                revokedPrepared.sealedCommand(), "idem-2", 4,
                Set.of("system.admin.access"));
        assertCode("AI_CONFIG_PERMISSION_DENIED", () ->
                revoked.owner.execute(currentRevoked));
        assertThat(revoked.store.writes).isZero();

        var staleAuthorization = fixture();
        var stalePrepared = staleAuthorization.owner.prepare(prepare(
                "proposal-3", "active", "Active",
                AiConfigurationFieldFacade.FieldType.BOOLEAN));
        staleAuthorization.context.epoch = 4;
        assertCode("AI_CONFIG_AUTHORIZATION_STALE", () ->
                staleAuthorization.owner.execute(execute(
                        "proposal-3", stalePrepared, "idem-3")));

        var changedModule = fixture();
        var changedPrepared = changedModule.owner.prepare(prepare(
                "proposal-4", "joined_at", "Joined at",
                AiConfigurationFieldFacade.FieldType.DATETIME));
        changedModule.context.moduleId = 42;
        assertCode("AI_CONFIG_DRAFT_STALE", () ->
                changedModule.owner.execute(execute(
                        "proposal-4", changedPrepared, "idem-4")));
        assertThat(changedModule.store.writes).isZero();
    }

    @Test
    void tamperedHashCiphertextBindingAndExpiredCommandWriteNothing() {
        var fixture = fixture();
        var prepared = fixture.owner.prepare(prepare(
                "proposal-1", "age", "Age",
                AiConfigurationFieldFacade.FieldType.INTEGER));
        var command = prepared.sealedCommand();
        var changedHash = new AiConfigurationFieldFacade.SealedCommand(
                command.ciphertext(), command.encryptionKeyVersion(),
                "f".repeat(64));
        assertCode("AI_CONFIG_FIELD_COMMAND_INVALID", () ->
                fixture.owner.execute(execute(
                        "proposal-1", prepared.preview(), changedHash, "hash")));

        var first = command.ciphertext().charAt(0) == 'A' ? 'B' : 'A';
        var changedCipher = new AiConfigurationFieldFacade.SealedCommand(
                first + command.ciphertext().substring(1),
                command.encryptionKeyVersion(), command.commandSha256());
        assertCode("AI_CONFIG_FIELD_COMMAND_INVALID", () ->
                fixture.owner.execute(execute(
                        "proposal-1", prepared.preview(), changedCipher, "cipher")));
        assertCode("AI_CONFIG_FIELD_COMMAND_INVALID", () ->
                fixture.owner.execute(execute(
                        "proposal-2", prepared, "binding")));

        fixture.clock.now = prepared.expiresAt();
        assertCode("AI_CONFIG_FIELD_COMMAND_EXPIRED", () ->
                fixture.owner.execute(execute(
                        "proposal-1", prepared, "expired")));
        assertThat(fixture.store.writes).isZero();
    }

    private static Fixture fixture() {
        var context = new Context();
        var store = new Store(context);
        var sealer = new AiConfigurationFieldCommandSealer(
                keys(), new java.security.SecureRandom());
        var clock = new MutableClock(NOW);
        var owner = new AiConfigurationFieldOwner(
                context, store, sealer,
                new AiConfigurationFieldCommandCodec(new ObjectMapper()),
                clock, Duration.ofMinutes(15));
        return new Fixture(owner, context, store, clock);
    }

    private static AiConfigurationFieldFacade.PrepareRequest prepare(
            String proposalId,
            String fieldCode,
            String fieldName,
            AiConfigurationFieldFacade.FieldType type) {
        return new AiConfigurationFieldFacade.PrepareRequest(
                proposalId, 7, 11, 21, 17, 3, permissions(),
                "customers", new AiConfigurationFieldFacade.FieldDraft(
                fieldCode, fieldName, type, false,
                AiConfigurationFieldFacade.ScalarSettings.empty()),
                "51", "61", 0, "prompt-v1", "request-1", "trace-1");
    }

    private static AiConfigurationFieldFacade.ExecuteRequest execute(
            String proposalId,
            AiConfigurationFieldFacade.PreparedField prepared,
            String key) {
        return execute(
                proposalId, prepared.preview(), prepared.sealedCommand(), key);
    }

    private static AiConfigurationFieldFacade.ExecuteRequest execute(
            String proposalId,
            AiConfigurationFieldFacade.FieldPreview preview,
            AiConfigurationFieldFacade.SealedCommand command,
            String key) {
        return execute(proposalId, preview, command, key, 3, permissions());
    }

    private static AiConfigurationFieldFacade.ExecuteRequest execute(
            String proposalId,
            AiConfigurationFieldFacade.FieldPreview preview,
            AiConfigurationFieldFacade.SealedCommand command,
            String key,
            long authorizationEpoch,
            Set<String> effectivePermissions) {
        return new AiConfigurationFieldFacade.ExecuteRequest(
                proposalId, 7, 11, 21, 17, authorizationEpoch,
                effectivePermissions,
                preview.configRootId(), preview.moduleId(), preview.moduleCode(),
                preview.expectedDraftRevision(), command, key,
                "request-2", "trace-2");
    }

    private static Set<String> permissions() {
        return Set.of("system.admin.access", "module.config.manage");
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
            AiConfigurationFieldOwner owner,
            Context context,
            Store store,
            MutableClock clock) { }

    private static final class Context
            implements AiConfigurationFieldOwner.ContextResolver {
        private long rootId = 31;
        private long moduleId = 41;
        private long revision = 5;
        private long epoch = 3;
        private Set<String> permissions = permissions();
        private final Set<String> fields = new java.util.HashSet<>();

        @Override
        public synchronized AiConfigurationFieldContextReader.Snapshot resolve(
                AiConfigurationFieldContextReader.Access access) {
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
            return new AiConfigurationFieldContextReader.Snapshot(
                    rootId, moduleId, "customers", revision,
                    10L * fields.size(), fields.contains(access.fieldCode()),
                    epoch, permissions);
        }
    }

    private static final class Store
            implements AiConfigurationFieldOwner.MutationStore {
        private final Context context;
        private final LinkedHashMap<String, Entry> rows = new LinkedHashMap<>();
        private int writes;
        private AiConfigurationFieldContextReader.Access lastAccess;

        private Store(Context context) {
            this.context = context;
        }

        @Override
        public synchronized AiConfigurationFieldFacade.FieldReadback execute(
                AiConfigurationFieldCommandCodec.Command command,
                AiConfigurationFieldContextReader.Access currentAccess,
                String key,
                String requestId,
                String traceId) {
            lastAccess = currentAccess;
            var current = rows.get(key);
            if (current != null) {
                if (!current.command.equals(command)) {
                    throw new BusinessException(
                            "AI_CONFIG_FIELD_IDEMPOTENCY_CONFLICT", "conflict",
                            org.springframework.http.HttpStatus.CONFLICT);
                }
                return current.result;
            }
            if (context.revision != command.expectedDraftRevision()) {
                throw new BusinessException(
                        "AI_CONFIG_DRAFT_STALE", "stale",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            var result = new AiConfigurationFieldFacade.FieldReadback(
                    Long.toString(command.configRootId()),
                    Long.toString(command.moduleId()), command.moduleCode(),
                    command.expectedDraftRevision() + 1,
                    new AiConfigurationFieldFacade.FieldView(
                            Long.toString(100 + writes),
                            command.field().fieldCode(), command.field().fieldName(),
                            command.field().fieldType(), command.field().required(),
                            command.field().settings(), command.sortOrder(), 0));
            rows.put(key, new Entry(command, result));
            context.fields.add(command.field().fieldCode());
            context.revision++;
            writes++;
            return result;
        }

        private record Entry(
                AiConfigurationFieldCommandCodec.Command command,
                AiConfigurationFieldFacade.FieldReadback result) { }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
