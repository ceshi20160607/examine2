package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigDraftService;
import com.unique.examine.module.manage.service.ConfigMutationSupport;
import com.unique.examine.module.manage.service.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Adapts one sealed scalar command onto the existing draft mutation pipeline. */
@Component
public class AiConfigurationFieldMutationStore {
    private final Idempotency idempotency;
    private final DraftCreator drafts;
    private final ContextResolver contexts;
    private final ObjectMapper json;

    @Autowired
    public AiConfigurationFieldMutationStore(
            ConfigMutationSupport mutations,
            ConfigDraftService drafts,
            AiConfigurationFieldContextReader contexts,
            ObjectMapper json) {
        this(mutations::idempotent, drafts::createField, contexts::resolve, json);
    }

    AiConfigurationFieldMutationStore(
            Idempotency idempotency,
            DraftCreator drafts,
            ContextResolver contexts,
            ObjectMapper json) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Transactional
    public AiConfigurationFieldFacade.FieldReadback execute(
            AiConfigurationFieldCommandCodec.Command command,
            AiConfigurationFieldContextReader.Access currentAccess,
            String idempotencyKey,
            String requestId,
            String traceId) {
        assertAccess(command, currentAccess);
        try {
            return idempotency.run(
                    scope(command), idempotencyKey, command,
                    AiConfigurationFieldFacade.FieldReadback.class,
                    () -> create(command, currentAccess, idempotencyKey,
                            requestId, traceId));
        } catch (BusinessException failure) {
            if ("IDEMPOTENCY_CONFLICT".equals(failure.code())) {
                throw new BusinessException(
                        "AI_CONFIG_FIELD_IDEMPOTENCY_CONFLICT",
                        "The idempotency key belongs to another field command",
                        HttpStatus.CONFLICT);
            }
            throw failure;
        }
    }

    private AiConfigurationFieldFacade.FieldReadback create(
            AiConfigurationFieldCommandCodec.Command command,
            AiConfigurationFieldContextReader.Access currentAccess,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var current = contexts.resolve(currentAccess);
        if (current.configRootId() != command.configRootId()
                || current.moduleId() != command.moduleId()
                || !current.moduleCode().equals(command.moduleCode())
                || current.draftRevision() != command.expectedDraftRevision()) {
            throw stale();
        }
        if (current.fieldCodeExists()) {
            throw new BusinessException(
                    "AI_CONFIG_FIELD_CODE_CONFLICT",
                    "The configuration field code already exists",
                    HttpStatus.CONFLICT);
        }
        var session = new ConfigSession(
                command.accountId(), command.systemId(), command.memberId(),
                command.tenantId(), current.effectivePermissions());
        var created = drafts.create(
                session, command.moduleId(), request(command), idempotencyKey,
                new RequestContext(requestId, traceId));
        assertCreated(command, created);
        return new AiConfigurationFieldFacade.FieldReadback(
                Long.toString(command.configRootId()),
                Long.toString(command.moduleId()), command.moduleCode(),
                command.expectedDraftRevision() + 1,
                new AiConfigurationFieldFacade.FieldView(
                        created.id(), created.code(), created.name(),
                        command.field().fieldType(), created.required(),
                        command.field().settings(), created.sortOrder(),
                        Long.parseLong(created.version())));
    }

    private ConfigRequests.CreateField request(
            AiConfigurationFieldCommandCodec.Command command) {
        var field = command.field();
        return new ConfigRequests.CreateField(
                null, null, field.fieldCode(), field.fieldName(),
                configType(field.fieldType()), command.sortOrder(),
                field.required(), false, false, false, false,
                true, true, ConfigTypes.IndexMode.NONE,
                ConfigTypes.DesiredStatus.ENABLED,
                properties(field.settings()),
                null, null,
                Long.toString(command.expectedDraftRevision()));
    }

    private ObjectNode properties(
            AiConfigurationFieldFacade.ScalarSettings value) {
        var result = json.createObjectNode();
        put(result, "minLength", value.minLength());
        put(result, "maxLength", value.maxLength());
        put(result, "trim", value.trim());
        put(result, "rows", value.rows());
        put(result, "minimum", value.minimum());
        put(result, "maximum", value.maximum());
        put(result, "precision", value.precision());
        put(result, "scale", value.scale());
        put(result, "format", value.format());
        put(result, "timezone", value.timezone());
        return result;
    }

    private static void put(ObjectNode value, String name, Object item) {
        if (item == null) return;
        if (item instanceof Integer number) value.put(name, number);
        else if (item instanceof Boolean bool) value.put(name, bool);
        else if ("minimum".equals(name) || "maximum".equals(name)) {
            value.put(name, new java.math.BigDecimal(item.toString()));
        } else value.put(name, item.toString());
    }

    private static ConfigTypes.FieldType configType(
            AiConfigurationFieldFacade.FieldType value) {
        return switch (value) {
            case TEXT -> ConfigTypes.FieldType.TEXT;
            case LONG_TEXT -> ConfigTypes.FieldType.TEXTAREA;
            case INTEGER, DECIMAL -> ConfigTypes.FieldType.NUMBER;
            case BOOLEAN -> ConfigTypes.FieldType.SWITCH;
            case DATE -> ConfigTypes.FieldType.DATE;
            case DATETIME -> ConfigTypes.FieldType.DATETIME;
        };
    }

    private static void assertCreated(
            AiConfigurationFieldCommandCodec.Command command,
            ConfigViews.Field value) {
        if (!value.moduleId().equals(Long.toString(command.moduleId()))
                || !value.code().equals(command.field().fieldCode())
                || !value.name().equals(command.field().fieldName())
                || value.type() != configType(command.field().fieldType())
                || value.required() != command.field().required()
                || value.sortOrder() != command.sortOrder()
                || value.dictionaryId() != null || value.targetModuleId() != null) {
            throw new IllegalStateException(
                    "Configuration draft returned a different field");
        }
    }

    private static void assertAccess(
            AiConfigurationFieldCommandCodec.Command command,
            AiConfigurationFieldContextReader.Access currentAccess) {
        Objects.requireNonNull(currentAccess, "currentAccess");
        if (currentAccess.accountId() != command.accountId()
                || currentAccess.systemId() != command.systemId()
                || currentAccess.tenantId() != command.tenantId()
                || currentAccess.memberId() != command.memberId()
                || !currentAccess.moduleCode().equals(command.moduleCode())
                || !currentAccess.fieldCode().equals(
                command.field().fieldCode())) {
            throw AiConfigurationFieldCommandCodec.invalid();
        }
    }

    private static String scope(
            AiConfigurationFieldCommandCodec.Command command) {
        return "ai-config-field:" + AiConfigurationFieldCommandSealer.sha256(
                command.accountId() + ":" + command.systemId() + ":"
                        + command.tenantId() + ":" + command.memberId() + ":"
                        + command.configRootId() + ":" + command.moduleId());
    }

    private static BusinessException stale() {
        return new BusinessException(
                "AI_CONFIG_DRAFT_STALE",
                "The configuration draft changed before confirmation",
                HttpStatus.CONFLICT);
    }

    @FunctionalInterface
    interface ContextResolver {
        AiConfigurationFieldContextReader.Snapshot resolve(
                AiConfigurationFieldContextReader.Access access);
    }

    @FunctionalInterface
    interface DraftCreator {
        ConfigViews.Field create(
                ConfigSession session,
                long moduleId,
                ConfigRequests.CreateField request,
                String idempotencyKey,
                RequestContext context);
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
}
