package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigMutationSupport;
import com.unique.examine.module.manage.service.RequestContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationFieldMutationStoreTest {

    @Test
    void exactReplayReturnsOneDraftCreateWithSafeFixedEnvelope() {
        var idempotency = new Idempotency();
        var creator = new Creator();
        var context = new Context();
        var store = new AiConfigurationFieldMutationStore(
                idempotency, creator, context, new ObjectMapper());
        var command = command(AiConfigurationFieldFacade.FieldType.DECIMAL);

        var first = store.execute(command, access(), "idem-1", "request-1", "trace-1");
        var replay = store.execute(command, access(), "idem-1", "request-2", "trace-2");

        assertThat(replay).isEqualTo(first);
        assertThat(creator.calls).isOne();
        assertThat(creator.request.dictionaryId()).isNull();
        assertThat(creator.request.targetModuleId()).isNull();
        assertThat(creator.request.hidden()).isFalse();
        assertThat(creator.request.readonly()).isFalse();
        assertThat(creator.request.searchable()).isFalse();
        assertThat(creator.request.filterable()).isFalse();
        assertThat(creator.request.showInList()).isTrue();
        assertThat(creator.request.showInDetail()).isTrue();
        assertThat(creator.request.indexMode()).isEqualTo(ConfigTypes.IndexMode.NONE);
        assertThat(creator.request.status()).isEqualTo(
                ConfigTypes.DesiredStatus.ENABLED);
        assertThat(creator.request.properties().fieldNames())
                .toIterable().containsExactlyInAnyOrder("precision", "scale");
        assertThat(first.draftRevision()).isEqualTo(6);
    }

    @Test
    void changedReplayConflictsBeforeDraftReadAndStaleRevisionCreatesNothing() {
        var idempotency = new Idempotency();
        var creator = new Creator();
        var context = new Context();
        var store = new AiConfigurationFieldMutationStore(
                idempotency, creator, context, new ObjectMapper());
        var command = command(AiConfigurationFieldFacade.FieldType.INTEGER);
        store.execute(command, access(), "idem-1", "request-1", "trace-1");
        var reads = context.calls;

        var changed = changed(command);
        assertCode("AI_CONFIG_FIELD_IDEMPOTENCY_CONFLICT", () ->
                store.execute(changed, changedAccess(), "idem-1", "request-2", "trace-2"));
        assertThat(context.calls).isEqualTo(reads);

        context.revision = 6;
        assertCode("AI_CONFIG_DRAFT_STALE", () ->
                store.execute(changed, changedAccess(), "idem-2", "request-2", "trace-2"));
        assertThat(creator.calls).isOne();
    }

    @Test
    void mapsOnlyTheSevenScalarTypesOntoExistingDraftFieldTypes() {
        var expected = java.util.Map.of(
                AiConfigurationFieldFacade.FieldType.TEXT, ConfigTypes.FieldType.TEXT,
                AiConfigurationFieldFacade.FieldType.LONG_TEXT, ConfigTypes.FieldType.TEXTAREA,
                AiConfigurationFieldFacade.FieldType.INTEGER, ConfigTypes.FieldType.NUMBER,
                AiConfigurationFieldFacade.FieldType.DECIMAL, ConfigTypes.FieldType.NUMBER,
                AiConfigurationFieldFacade.FieldType.BOOLEAN, ConfigTypes.FieldType.SWITCH,
                AiConfigurationFieldFacade.FieldType.DATE, ConfigTypes.FieldType.DATE,
                AiConfigurationFieldFacade.FieldType.DATETIME, ConfigTypes.FieldType.DATETIME);
        for (var entry : expected.entrySet()) {
            var creator = new Creator();
            var store = new AiConfigurationFieldMutationStore(
                    new Idempotency(), creator, new Context(), new ObjectMapper());
            store.execute(command(entry.getKey()),
                    access(), "idem-" + entry.getKey(), "request-1", "trace-1");
            assertThat(creator.request.type()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void firstMutationRechecksAndUsesTheCurrentAuthorizationSnapshot() {
        var creator = new Creator();
        var context = new Context();
        context.epoch = 4;
        context.permissions = Set.of(
                "system.admin.access", "module.config.manage", "new.permission");
        var currentAccess = new AiConfigurationFieldContextReader.Access(
                7, 11, 21, 17, 4, context.permissions,
                "customers", "field_code");
        var store = new AiConfigurationFieldMutationStore(
                new Idempotency(), creator, context, new ObjectMapper());

        store.execute(command(AiConfigurationFieldFacade.FieldType.TEXT),
                currentAccess, "idem-live", "request-1", "trace-1");

        assertThat(context.lastAccess).isEqualTo(currentAccess);
        assertThat(creator.session.permissions()).isEqualTo(context.permissions);
    }

    private static AiConfigurationFieldCommandCodec.Command command(
            AiConfigurationFieldFacade.FieldType type) {
        var settings = switch (type) {
            case INTEGER -> new AiConfigurationFieldFacade.ScalarSettings(
                    null, null, null, null, null, null,
                    38, 0, null, null);
            case DECIMAL -> new AiConfigurationFieldFacade.ScalarSettings(
                    null, null, null, null, null, null,
                    18, 2, null, null);
            default -> AiConfigurationFieldFacade.ScalarSettings.empty();
        };
        return new AiConfigurationFieldCommandCodec.Command(
                "proposal-1", 7, 11, 21, 17, 3, permissions(),
                31, 41, "customers", 5, 10,
                new AiConfigurationFieldFacade.FieldDraft(
                        "field_code", "Field name", type, false, settings),
                "51", "61", 0, "prompt-v1",
                Instant.parse("2026-08-04T01:15:00Z"),
                "request-1", "trace-1");
    }

    private static AiConfigurationFieldCommandCodec.Command changed(
            AiConfigurationFieldCommandCodec.Command value) {
        return new AiConfigurationFieldCommandCodec.Command(
                "proposal-2", value.accountId(), value.systemId(),
                value.tenantId(), value.memberId(), value.authorizationEpoch(),
                value.effectivePermissions(), value.configRootId(), value.moduleId(),
                value.moduleCode(), value.expectedDraftRevision(), value.sortOrder(),
                new AiConfigurationFieldFacade.FieldDraft(
                        "other_code", "Other", AiConfigurationFieldFacade.FieldType.TEXT,
                        false, AiConfigurationFieldFacade.ScalarSettings.empty()),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.promptVersion(), value.expiresAt(),
                value.requestId(), value.traceId());
    }

    private static Set<String> permissions() {
        return Set.of("system.admin.access", "module.config.manage");
    }

    private static AiConfigurationFieldContextReader.Access access() {
        return new AiConfigurationFieldContextReader.Access(
                7, 11, 21, 17, 3, permissions(),
                "customers", "field_code");
    }

    private static AiConfigurationFieldContextReader.Access changedAccess() {
        return new AiConfigurationFieldContextReader.Access(
                7, 11, 21, 17, 3, permissions(),
                "customers", "other_code");
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private static final class Context
            implements AiConfigurationFieldMutationStore.ContextResolver {
        private long revision = 5;
        private long epoch = 3;
        private Set<String> permissions = permissions();
        private int calls;
        private AiConfigurationFieldContextReader.Access lastAccess;

        @Override
        public AiConfigurationFieldContextReader.Snapshot resolve(
                AiConfigurationFieldContextReader.Access access) {
            calls++;
            lastAccess = access;
            return new AiConfigurationFieldContextReader.Snapshot(
                    31, 41, "customers", revision, 10,
                    false, epoch, permissions);
        }
    }

    private static final class Creator
            implements AiConfigurationFieldMutationStore.DraftCreator {
        private ConfigRequests.CreateField request;
        private ConfigSession session;
        private int calls;

        @Override
        public ConfigViews.Field create(
                ConfigSession session,
                long moduleId,
                ConfigRequests.CreateField request,
                String idempotencyKey,
                RequestContext context) {
            this.session = session;
            this.request = request;
            calls++;
            return new ConfigViews.Field(
                    "101", Long.toString(moduleId), null, null,
                    request.code(), request.name(), request.type(),
                    request.sortOrder(), request.required(), request.hidden(),
                    request.readonly(), request.searchable(), request.filterable(),
                    request.showInList(), request.showInDetail(), request.indexMode(),
                    request.status(), request.properties(),
                    ConfigTypes.FieldPermissionMode.INHERIT, ConfigTypes.FieldPermissionMode.INHERIT,
                    "0", "6");
        }
    }

    private static final class Idempotency
            implements AiConfigurationFieldMutationStore.Idempotency {
        private final LinkedHashMap<String, Entry> rows = new LinkedHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public synchronized <T> T run(
                String scopeKey,
                String key,
                Object request,
                Class<T> responseType,
                ConfigMutationSupport.Mutation<T> mutation) {
            var composite = scopeKey + ":" + key;
            var current = rows.get(composite);
            if (current != null) {
                if (!current.request.equals(request)) {
                    throw new BusinessException(
                            "IDEMPOTENCY_CONFLICT", "conflict",
                            org.springframework.http.HttpStatus.CONFLICT);
                }
                return (T) current.result;
            }
            var result = mutation.run();
            rows.put(composite, new Entry(request, result));
            return result;
        }

        private record Entry(Object request, Object result) { }
    }
}
