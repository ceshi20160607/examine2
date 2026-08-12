package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigMutationSupport;
import com.unique.examine.module.manage.service.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Idempotent first-mutation gate for both bounded artifacts. */
@Component
public class AiConfigurationArtifactMutationStore {
    private final Idempotency idempotency;
    private final Writer writer;
    private final ContextResolver contexts;

    @Autowired
    public AiConfigurationArtifactMutationStore(
            ConfigMutationSupport mutations,
            AiConfigurationArtifactDraftWriter writer,
            AiConfigurationArtifactContextReader contexts) {
        this(mutations::idempotent, writer::write,
                new ContextResolver() {
                    @Override
                    public AiConfigurationArtifactContextReader.SelectionSnapshot
                    selection(
                            AiConfigurationArtifactContextReader.Access access,
                            String fieldCode, String dictionaryCode) {
                        return contexts.selection(
                                access, fieldCode, dictionaryCode);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.PageSnapshot page(
                            AiConfigurationArtifactContextReader.Access access,
                            String pageCode, Set<String> fieldCodes) {
                        return contexts.page(access, pageCode, fieldCodes);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.FilterScenarioSnapshot
                    filterScenario(
                            AiConfigurationArtifactContextReader.Access access,
                            String pageCode) {
                        return contexts.filterScenario(access, pageCode);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.FieldPermissionSnapshot
                    fieldPermission(
                            AiConfigurationArtifactContextReader.Access access,
                            String fieldCode) {
                        return contexts.fieldPermission(access, fieldCode);
                    }
                });
    }

    AiConfigurationArtifactMutationStore(
            Idempotency idempotency, Writer writer, ContextResolver contexts) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Transactional
    public AiConfigurationArtifactFacade.ArtifactReadback execute(
            AiConfigurationArtifactCommandCodec.Command command,
            AiConfigurationArtifactContextReader.Access currentAccess,
            String idempotencyKey,
            String requestId,
            String traceId) {
        assertAccess(command, currentAccess);
        try {
            return idempotency.run(
                    scope(command), idempotencyKey, command,
                    AiConfigurationArtifactFacade.ArtifactReadback.class,
                    () -> create(command, currentAccess, requestId, traceId));
        } catch (BusinessException failure) {
            if ("IDEMPOTENCY_CONFLICT".equals(failure.code())) {
                throw new BusinessException(
                        "AI_CONFIG_ARTIFACT_IDEMPOTENCY_CONFLICT",
                        "The idempotency key belongs to another artifact command",
                        HttpStatus.CONFLICT);
            }
            if ("CONFIG_VERSION_CONFLICT".equals(failure.code())) {
                throw stale();
            }
            throw failure;
        }
    }

    private AiConfigurationArtifactFacade.ArtifactReadback create(
            AiConfigurationArtifactCommandCodec.Command command,
            AiConfigurationArtifactContextReader.Access currentAccess,
            String requestId,
            String traceId) {
        AiConfigurationArtifactContextReader.CommonSnapshot common = null;
        switch (command.operation()) {
            case CONFIG_SELECTION_FIELD_DRAFT -> {
                var draft = command.selectionField().draft();
                var current = contexts.selection(
                        currentAccess, draft.fieldCode(), draft.dictionaryCode());
                common = current.common();
                assertCommon(command, common);
                if (current.sortOrder() != command.selectionField().sortOrder()) {
                    throw stale();
                }
                if (current.fieldCodeExists()) {
                    throw conflict(
                            "AI_CONFIG_FIELD_CODE_CONFLICT",
                            "The configuration field code already exists");
                }
                if (current.dictionaryCodeExists()) {
                    throw conflict(
                            "AI_CONFIG_DICTIONARY_CODE_CONFLICT",
                            "The configuration dictionary code already exists");
                }
            }
            case CONFIG_PAGE_LAYOUT_DRAFT -> {
                var preview = command.pageLayout();
                var current = contexts.page(
                        currentAccess, preview.pageCode(), fields(preview.layout()));
                common = current.common();
                assertCommon(command, common);
                if (current.pageId() != Long.parseLong(preview.pageId())
                        || !current.pageCode().equals(preview.pageCode())
                        || current.pageType() != preview.pageType()
                        || current.pageVersion() != preview.pageVersion()
                        || !current.fieldCodes().equals(fields(preview.layout()))) {
                    throw stale();
                }
            }
            case CONFIG_FILTER_SCENARIO_DRAFT -> {
                var preview = command.filterScenario();
                var current = contexts.filterScenario(
                        currentAccess, preview.pageCode());
                common = current.common();
                assertCommon(command, common);
                if (!current.page().id().equals(preview.pageId())
                        || !current.page().code().equals(preview.pageCode())
                        || Long.parseLong(current.page().version())
                        != preview.pageVersion()) {
                    throw stale();
                }
            }
            case CONFIG_FIELD_PERMISSION_STAGE_DRAFT -> {
                var preview = command.fieldPermissionStage();
                var current = contexts.fieldPermission(
                        currentAccess, preview.fieldCode());
                common = current.common();
                assertCommon(command, common);
                var field = current.field();
                if (!field.id().equals(preview.fieldId())
                        || !field.code().equals(preview.fieldCode())
                        || !field.name().equals(preview.fieldName())
                        || Long.parseLong(field.version()) != preview.fieldVersion()
                        || AiConfigurationArtifactCanonicalizer.mode(
                        field.readPermissionMode())
                        != preview.expectedReadPermissionMode()
                        || AiConfigurationArtifactCanonicalizer.mode(
                        field.writePermissionMode())
                        != preview.expectedWritePermissionMode()) {
                    throw stale();
                }
            }
        }
        var session = new ConfigSession(
                command.accountId(), command.systemId(), command.memberId(),
                command.tenantId(), Objects.requireNonNull(common, "common")
                .effectivePermissions());
        return writer.write(
                command, session, new RequestContext(requestId, traceId));
    }

    private static void assertCommon(
            AiConfigurationArtifactCommandCodec.Command command,
            AiConfigurationArtifactContextReader.CommonSnapshot current) {
        if (current.configRootId() != command.configRootId()
                || current.moduleId() != command.moduleId()
                || !current.moduleCode().equals(command.moduleCode())
                || current.draftRevision() != command.expectedDraftRevision()) {
            throw stale();
        }
    }

    private static void assertAccess(
            AiConfigurationArtifactCommandCodec.Command command,
            AiConfigurationArtifactContextReader.Access access) {
        Objects.requireNonNull(access, "currentAccess");
        if (access.accountId() != command.accountId()
                || access.systemId() != command.systemId()
                || access.tenantId() != command.tenantId()
                || access.memberId() != command.memberId()
                || !access.moduleCode().equals(command.moduleCode())) {
            throw AiConfigurationArtifactCommandCodec.invalid();
        }
    }

    private static Set<String> fields(
            AiConfigurationArtifactFacade.PageLayout layout) {
        var result = new LinkedHashSet<String>();
        layout.sections().forEach(
                section -> result.addAll(section.fieldCodes()));
        return Set.copyOf(result);
    }

    private static String scope(
            AiConfigurationArtifactCommandCodec.Command command) {
        return "ai-config-artifact:"
                + AiConfigurationArtifactCommandSealer.sha256(
                command.accountId() + ":" + command.systemId() + ":"
                        + command.tenantId() + ":" + command.memberId() + ":"
                        + command.configRootId() + ":" + command.moduleId());
    }

    private static BusinessException stale() {
        return conflict(
                "AI_CONFIG_DRAFT_STALE",
                "The configuration draft changed before confirmation");
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    @FunctionalInterface
    interface Idempotency {
        <T> T run(
                String scopeKey,
                String key,
                Object request,
                Class<T> responseType,
                ConfigMutationSupport.Mutation<T> mutation);
    }

    @FunctionalInterface
    interface Writer {
        AiConfigurationArtifactFacade.ArtifactReadback write(
                AiConfigurationArtifactCommandCodec.Command command,
                ConfigSession session,
                RequestContext request);
    }

    interface ContextResolver {
        AiConfigurationArtifactContextReader.SelectionSnapshot selection(
                AiConfigurationArtifactContextReader.Access access,
                String fieldCode,
                String dictionaryCode);

        AiConfigurationArtifactContextReader.PageSnapshot page(
                AiConfigurationArtifactContextReader.Access access,
                String pageCode,
                Set<String> fieldCodes);

        default AiConfigurationArtifactContextReader.FilterScenarioSnapshot
        filterScenario(
                AiConfigurationArtifactContextReader.Access access,
                String pageCode) {
            throw new UnsupportedOperationException();
        }

        default AiConfigurationArtifactContextReader.FieldPermissionSnapshot
        fieldPermission(
                AiConfigurationArtifactContextReader.Access access,
                String fieldCode) {
            throw new UnsupportedOperationException();
        }
    }
}
