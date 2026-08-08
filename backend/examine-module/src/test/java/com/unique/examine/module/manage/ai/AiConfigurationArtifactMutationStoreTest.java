package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigMutationSupport;
import com.unique.examine.module.manage.service.RequestContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactMutationStoreTest {

    @Test
    void exactSelectionReplayWritesOneAtomicArtifactWithCurrentPermissions() {
        var idempotency = new Idempotency();
        var writer = new Writer();
        var context = new Context();
        context.epoch = 4;
        context.permissions = Set.of(
                "system.admin.access", "module.config.manage", "new.permission");
        var store = new AiConfigurationArtifactMutationStore(
                idempotency, writer, context);
        var command = selectionCommand();
        var access = access(4, context.permissions);

        var first = store.execute(
                command, access, "idem-1", "request-1", "trace-1");
        var replay = store.execute(
                command, access, "idem-1", "request-2", "trace-2");

        assertThat(replay).isEqualTo(first);
        assertThat(writer.calls).isOne();
        assertThat(writer.session.permissions()).isEqualTo(context.permissions);
        assertThat(context.selectionCalls).isOne();
    }

    @Test
    void changedReplayConflictsBeforeContextAndPageVersionStaleWritesNothing() {
        var idempotency = new Idempotency();
        var writer = new Writer();
        var context = new Context();
        var store = new AiConfigurationArtifactMutationStore(
                idempotency, writer, context);
        var selection = selectionCommand();
        store.execute(selection, access(), "idem-1", "request-1", "trace-1");
        var calls = context.selectionCalls;

        assertCode("AI_CONFIG_ARTIFACT_IDEMPOTENCY_CONFLICT", () ->
                store.execute(pageCommand(), access(), "idem-1",
                        "request-2", "trace-2"));
        assertThat(context.selectionCalls).isEqualTo(calls);
        assertThat(context.pageCalls).isZero();

        context.pageVersion = 3;
        assertCode("AI_CONFIG_DRAFT_STALE", () ->
                store.execute(pageCommand(), access(), "idem-page",
                        "request-2", "trace-2"));
        assertThat(writer.calls).isOne();
    }

    @Test
    void failedAtomicWriterLeavesNoReplayRecordAndCanRetry() {
        var idempotency = new Idempotency();
        var writer = new Writer();
        writer.failNext = true;
        var context = new Context();
        var store = new AiConfigurationArtifactMutationStore(
                idempotency, writer, context);
        var command = selectionCommand();

        assertThatThrownBy(() -> store.execute(
                command, access(), "idem-rollback", "request-1", "trace-1"))
                .isInstanceOf(IllegalStateException.class);
        var result = store.execute(
                command, access(), "idem-rollback", "request-2", "trace-2");

        assertThat(result.selectionField()).isNotNull();
        assertThat(writer.calls).isEqualTo(2);
        assertThat(idempotency.rows).hasSize(1);
    }

    private static AiConfigurationArtifactCommandCodec.Command selectionCommand() {
        var codec = new AiConfigurationArtifactCommandCodec(new ObjectMapper());
        return AiConfigurationArtifactCommandCodecTest.selectionCommand(codec);
    }

    private static AiConfigurationArtifactCommandCodec.Command pageCommand() {
        var codec = new AiConfigurationArtifactCommandCodec(new ObjectMapper());
        return AiConfigurationArtifactCommandCodecTest.pageCommand(codec);
    }

    private static AiConfigurationArtifactContextReader.Access access() {
        return access(3, AiConfigurationArtifactCommandCodecTest.permissions());
    }

    private static AiConfigurationArtifactContextReader.Access access(
            long epoch, Set<String> permissions) {
        return new AiConfigurationArtifactContextReader.Access(
                7, 11, 21, 17, epoch, permissions, "customers");
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private static final class Context
            implements AiConfigurationArtifactMutationStore.ContextResolver {
        private long revision = 5;
        private long epoch = 3;
        private long pageVersion = 2;
        private Set<String> permissions =
                AiConfigurationArtifactCommandCodecTest.permissions();
        private int selectionCalls;
        private int pageCalls;

        private AiConfigurationArtifactContextReader.CommonSnapshot common() {
            return new AiConfigurationArtifactContextReader.CommonSnapshot(
                    31, 41, "customers", revision, epoch, permissions);
        }

        @Override
        public AiConfigurationArtifactContextReader.SelectionSnapshot selection(
                AiConfigurationArtifactContextReader.Access access,
                String fieldCode, String dictionaryCode) {
            selectionCalls++;
            return new AiConfigurationArtifactContextReader.SelectionSnapshot(
                    common(), 20, false, false);
        }

        @Override
        public AiConfigurationArtifactContextReader.PageSnapshot page(
                AiConfigurationArtifactContextReader.Access access,
                String pageCode, Set<String> fieldCodes) {
            pageCalls++;
            return new AiConfigurationArtifactContextReader.PageSnapshot(
                    common(), 71, "form",
                    AiConfigurationArtifactFacade.PageType.FORM,
                    pageVersion, fieldCodes);
        }
    }

    private static final class Writer
            implements AiConfigurationArtifactMutationStore.Writer {
        private int calls;
        private boolean failNext;
        private ConfigSession session;

        @Override
        public AiConfigurationArtifactFacade.ArtifactReadback write(
                AiConfigurationArtifactCommandCodec.Command command,
                ConfigSession session,
                RequestContext request) {
            this.session = session;
            calls++;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("atomic rollback");
            }
            if (command.selectionField() != null) {
                var draft = command.selectionField().draft();
                return new AiConfigurationArtifactFacade.ArtifactReadback(
                        command.operation(), "31", "41", "customers", 6,
                        new AiConfigurationArtifactFacade.SelectionFieldReadback(
                                new AiConfigurationArtifactFacade.DictionaryView(
                                        "81", draft.dictionaryCode(),
                                        draft.dictionaryName(), 0),
                                java.util.List.of(
                                        option("91", draft.options().get(0), 0),
                                        option("92", draft.options().get(1), 10)),
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
                            3, command.pageLayout().layout()));
        }

        private static AiConfigurationArtifactFacade.OptionView option(
                String id, AiConfigurationArtifactFacade.OptionDraft value,
                int sortOrder) {
            return new AiConfigurationArtifactFacade.OptionView(
                    id, value.code(), value.label(), value.semanticKey(),
                    value.color(), value.defaultOption(), sortOrder, 0);
        }
    }

    private static final class Idempotency
            implements AiConfigurationArtifactMutationStore.Idempotency {
        private final LinkedHashMap<String, Entry> rows = new LinkedHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public synchronized <T> T run(
                String scopeKey, String key, Object request,
                Class<T> responseType,
                ConfigMutationSupport.Mutation<T> mutation) {
            var composite = scopeKey + ":" + key;
            var existing = rows.get(composite);
            if (existing != null) {
                if (!existing.request.equals(request)) {
                    throw new BusinessException(
                            "IDEMPOTENCY_CONFLICT", "conflict",
                            org.springframework.http.HttpStatus.CONFLICT);
                }
                return (T) existing.result;
            }
            var result = mutation.run();
            rows.put(composite, new Entry(request, result));
            return result;
        }

        private record Entry(Object request, Object result) { }
    }
}
