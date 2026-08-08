package com.unique.examine.work.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Transactional
public class WorkConfigurationService {
    public static final String MANAGE = "work.config.manage";

    private final WorkConfigurationRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public WorkConfigurationService(
            WorkConfigurationRepository repository,
            ObjectMapper json,
            Clock clock) {
        if (repository == null || json == null || clock == null) {
            throw new IllegalArgumentException(
                    "Work configuration service dependencies are required");
        }
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    public WorkConfiguration createDraft(
            WorkActor actor, WorkConfiguration.Snapshot snapshot) {
        require(actor, MANAGE);
        var now = Instant.now(clock);
        return repository.insert(new WorkConfiguration(
                repository.nextId(), actor.systemId(), actor.tenantId(),
                repository.nextRevision(actor.systemId(), actor.tenantId()),
                WorkConfiguration.Status.DRAFT, snapshot, null,
                actor.memberId(), now, null, null, 1));
    }

    public WorkConfiguration publish(
            WorkActor actor, long configurationId, long expectedVersion) {
        require(actor, MANAGE);
        var draft = configuration(actor, configurationId);
        if (draft.version() != expectedVersion) throw versionConflict();
        validateDictionaries(draft);
        repository.active(actor.systemId(), actor.tenantId()).ifPresent(current ->
                repository.update(current.retire(), current.version()));
        var published = draft.publish(actor.memberId(), Instant.now(clock));
        return repository.update(published, expectedVersion);
    }

    @Transactional(readOnly = true)
    public PublishCheck check(
            WorkActor actor, long configurationId, long expectedVersion) {
        require(actor, MANAGE);
        var draft = configuration(actor, configurationId);
        if (draft.status() != WorkConfiguration.Status.DRAFT) {
            throw error("WORK_CONFIG_STATE_INVALID",
                    "Only a draft can run publication checks");
        }
        if (draft.version() != expectedVersion) throw versionConflict();
        validateDictionaries(draft);
        return new PublishCheck(
                draft.revision(), true, actor.memberId(), Instant.now(clock));
    }

    public WorkConfiguration rollback(WorkActor actor, long targetRevision) {
        require(actor, MANAGE);
        var target = repository.findByRevision(
                        actor.systemId(), actor.tenantId(), targetRevision)
                .orElseThrow(() -> error(
                        "WORK_CONFIG_NOT_FOUND", "Rollback revision was not found"));
        if (target.status() == WorkConfiguration.Status.DRAFT) {
            throw error("WORK_CONFIG_STATE_INVALID",
                    "A draft revision cannot be used as a rollback target");
        }
        validateDictionaries(target);
        repository.active(actor.systemId(), actor.tenantId()).ifPresent(current ->
                repository.update(current.retire(), current.version()));
        var now = Instant.now(clock);
        return repository.insert(new WorkConfiguration(
                repository.nextId(), actor.systemId(), actor.tenantId(),
                repository.nextRevision(actor.systemId(), actor.tenantId()),
                WorkConfiguration.Status.PUBLISHED, target.snapshot(),
                targetRevision, actor.memberId(), now, actor.memberId(), now, 1));
    }

    @Transactional(readOnly = true)
    public List<WorkConfiguration> history(WorkActor actor) {
        require(actor, MANAGE);
        return repository.history(actor.systemId(), actor.tenantId());
    }

    @Transactional(readOnly = true)
    public WorkConfiguration active(WorkActor actor) {
        return repository.active(actor.systemId(), actor.tenantId())
                .orElseThrow(() -> error(
                        "WORK_CONFIG_NOT_PUBLISHED",
                        "No Work configuration has been published"));
    }

    public PreparedValues prepareCreate(
            WorkActor actor, WorkConfiguration.ObjectType type,
            JsonNode submitted) {
        return prepare(actor, type, null, submitted, true);
    }

    public PreparedValues prepareUpdate(
            WorkActor actor, WorkConfiguration.ObjectType type,
            long objectId, JsonNode submitted) {
        return prepare(actor, type, objectId, submitted, false);
    }

    public void saveValues(
            WorkActor actor, WorkConfiguration.ObjectType type,
            long objectId, PreparedValues prepared) {
        if (prepared == null || prepared.configurationRevision() == 0) return;
        repository.saveRuntimeValues(
                actor.systemId(), actor.tenantId(), type, objectId,
                prepared.configurationRevision(), prepared.values(), actor.memberId());
    }

    @Transactional(readOnly = true)
    public RuntimeView runtimeView(
            WorkActor actor, WorkConfiguration.ObjectType type, long objectId) {
        var active = repository.active(actor.systemId(), actor.tenantId())
                .orElse(null);
        if (active == null) {
            return RuntimeView.empty(type);
        }
        var stored = repository.runtimeValues(
                actor.systemId(), actor.tenantId(), type, objectId)
                .orElse(new WorkConfigurationRepository.RuntimeValues(
                        active.revision(), json.createObjectNode()));
        var permissions = actor.permissions();
        var values = json.createObjectNode();
        var cardValues = json.createObjectNode();
        var fields = new ArrayList<RuntimeField>();
        String column = null;
        String group = null;
        String numeric = null;
        for (var field : active.snapshot().fields(type)) {
            if (!field.readable(permissions)) continue;
            fields.add(RuntimeField.from(field, permissions));
            var value = stored.values().get(field.code());
            if (value != null) {
                values.set(field.code(), value);
                if (field.cardVisible()) cardValues.set(field.code(), value);
            }
            switch (field.kanbanRole()) {
                case COLUMN -> column = field.code();
                case GROUP -> group = field.code();
                case NUMERIC -> numeric = field.code();
                default -> { }
            }
        }
        return new RuntimeView(active.revision(), stored.configurationRevision(),
                type, fields, values, cardValues,
                new KanbanSettings(column, group, numeric,
                        value(values, column), value(values, group),
                        value(values, numeric)));
    }

    private PreparedValues prepare(
            WorkActor actor, WorkConfiguration.ObjectType type,
            Long objectId, JsonNode submitted, boolean create) {
        if (type == null) throw invalid("Work object type is required");
        if (submitted != null && !submitted.isObject()) {
            throw invalid("customFields must be a JSON object");
        }
        var active = repository.active(actor.systemId(), actor.tenantId())
                .orElse(null);
        if (active == null) {
            if (submitted != null && !submitted.isEmpty()) {
                throw error("WORK_CONFIG_NOT_PUBLISHED",
                        "Custom Work fields require a published configuration");
            }
            return new PreparedValues(0, json.createObjectNode());
        }
        var values = objectId == null ? json.createObjectNode()
                : repository.runtimeValues(actor.systemId(), actor.tenantId(), type, objectId)
                .map(value -> (ObjectNode) value.values().deepCopy())
                .orElseGet(json::createObjectNode);
        var definitions = new HashMap<String, WorkConfiguration.Field>();
        active.snapshot().fields(type).forEach(field -> definitions.put(field.code(), field));
        if (submitted != null) {
            submitted.fields().forEachRemaining(entry -> {
                var field = definitions.get(entry.getKey());
                if (field == null) {
                    throw error("WORK_FIELD_UNKNOWN",
                            "Work field is not published: " + entry.getKey());
                }
                if (!field.editable(actor.permissions())) {
                    throw error("WORK_FIELD_FORBIDDEN",
                            "Work field is not editable: " + entry.getKey());
                }
                if (entry.getValue() == null || entry.getValue().isNull()) {
                    values.remove(entry.getKey());
                } else {
                    values.set(entry.getKey(), normalize(field, entry.getValue(), actor.systemId()));
                }
            });
        }
        for (var field : definitions.values()) {
            if (field.required() && field.editable(actor.permissions())
                    && missing(values.get(field.code()))) {
                throw error("WORK_FIELD_REQUIRED",
                        "Required Work field is missing: " + field.code());
            }
        }
        if (create && submitted == null && !definitions.isEmpty()) {
            // Required-field validation above intentionally applies even when
            // the client omits customFields on create.
        }
        return new PreparedValues(active.revision(), values);
    }

    private JsonNode normalize(
            WorkConfiguration.Field field, JsonNode value, long systemId) {
        return switch (field.type()) {
            case TEXT -> {
                if (!value.isTextual() || value.textValue().isBlank()
                        || value.textValue().codePointCount(0, value.textValue().length()) > 4000) {
                    throw invalidField(field, "must contain 1 to 4000 characters");
                }
                yield json.getNodeFactory().textNode(value.textValue().strip());
            }
            case NUMBER -> {
                if (!value.isNumber()) throw invalidField(field, "must be numeric");
                var number = value.decimalValue().stripTrailingZeros();
                if (number.precision() > 30 || number.scale() > 10) {
                    throw invalidField(field, "exceeds numeric precision 30,10");
                }
                yield json.getNodeFactory().numberNode(number);
            }
            case BOOLEAN -> {
                if (!value.isBoolean()) throw invalidField(field, "must be boolean");
                yield value.booleanValue() ? json.getNodeFactory().booleanNode(true)
                        : json.getNodeFactory().booleanNode(false);
            }
            case DATE -> {
                if (!value.isTextual()) throw invalidField(field, "must be an ISO date");
                try {
                    yield json.getNodeFactory().textNode(LocalDate.parse(value.textValue()).toString());
                } catch (RuntimeException failure) {
                    throw invalidField(field, "must be an ISO date");
                }
            }
            case DATETIME -> {
                if (!value.isTextual()) throw invalidField(field, "must be an ISO instant");
                try {
                    yield json.getNodeFactory().textNode(Instant.parse(value.textValue()).toString());
                } catch (RuntimeException failure) {
                    throw invalidField(field, "must be an ISO instant");
                }
            }
            case SELECT -> {
                if (!value.isTextual() || value.textValue().isBlank()) {
                    throw invalidField(field, "must be a dictionary item code");
                }
                var item = value.textValue().strip();
                requireDictionaryItems(systemId, field, Set.of(item));
                yield json.getNodeFactory().textNode(item);
            }
            case MULTI_SELECT -> {
                if (!value.isArray() || value.isEmpty() || value.size() > 100) {
                    throw invalidField(field, "must contain 1 to 100 dictionary item codes");
                }
                var items = new LinkedHashSet<String>();
                value.forEach(item -> {
                    if (!item.isTextual() || item.textValue().isBlank()) {
                        throw invalidField(field, "contains an invalid dictionary item code");
                    }
                    items.add(item.textValue().strip());
                });
                if (items.size() != value.size()) {
                    throw invalidField(field, "contains duplicate dictionary item codes");
                }
                requireDictionaryItems(systemId, field, items);
                var result = json.createArrayNode();
                items.forEach(result::add);
                yield result;
            }
        };
    }

    private void requireDictionaryItems(
            long systemId, WorkConfiguration.Field field, Set<String> items) {
        if (!repository.enabledDictionaryItems(
                systemId, field.dictionaryCode(), items)) {
            throw invalidField(field,
                    "contains a disabled or unknown dictionary item");
        }
    }

    private void validateDictionaries(WorkConfiguration configuration) {
        configuration.snapshot().fields().values().stream()
                .flatMap(List::stream)
                .map(WorkConfiguration.Field::dictionaryCode)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(code -> {
                    if (!repository.enabledDictionary(configuration.systemId(), code)) {
                        throw error("WORK_CONFIG_DICTIONARY_INVALID",
                                "Dictionary is disabled or unknown: " + code);
                    }
                });
    }

    private WorkConfiguration configuration(WorkActor actor, long id) {
        return repository.findById(actor.systemId(), actor.tenantId(), id)
                .orElseThrow(() -> error(
                        "WORK_CONFIG_NOT_FOUND", "Work configuration was not found"));
    }

    private static void require(WorkActor actor, String permission) {
        if (actor == null || !actor.has(permission)) {
            throw error("WORK_CONFIG_FORBIDDEN",
                    "Work configuration requires " + permission);
        }
    }

    private static JsonNode value(ObjectNode values, String code) {
        return code == null ? null : values.get(code);
    }

    private static boolean missing(JsonNode value) {
        return value == null || value.isNull()
                || value.isTextual() && value.textValue().isBlank()
                || value.isArray() && value.isEmpty();
    }

    private static WorkDomainException invalidField(
            WorkConfiguration.Field field, String reason) {
        return error("WORK_FIELD_VALUE_INVALID",
                "Work field " + field.code() + " " + reason);
    }

    private static WorkDomainException invalid(String message) {
        return error("WORK_CONFIG_INVALID", message);
    }

    private static WorkDomainException versionConflict() {
        return error("WORK_CONFIG_VERSION_CONFLICT",
                "Work configuration version is stale");
    }

    private static WorkDomainException error(String code, String message) {
        return new WorkDomainException(code, message);
    }

    public record PreparedValues(long configurationRevision, ObjectNode values) {
        public PreparedValues {
            if (configurationRevision < 0 || values == null) {
                throw new IllegalArgumentException("Prepared Work values are invalid");
            }
        }
    }

    public record PublishCheck(
            long revision, boolean ready, long checkedBy, Instant checkedAt) {
    }

    public record RuntimeField(
            String code, String name, WorkConfiguration.FieldType type,
            boolean required, String dictionaryCode, boolean editable,
            boolean cardVisible, WorkConfiguration.KanbanRole kanbanRole) {
        static RuntimeField from(
                WorkConfiguration.Field field, Set<String> permissions) {
            return new RuntimeField(field.code(), field.name(), field.type(),
                    field.required(), field.dictionaryCode(),
                    field.editable(permissions),
                    field.cardVisible(), field.kanbanRole());
        }
    }

    public record KanbanSettings(
            String columnFieldCode, String groupFieldCode,
            String numericFieldCode, JsonNode columnValue,
            JsonNode groupValue, JsonNode numericValue) {
    }

    public record RuntimeView(
            long activeRevision,
            long storedRevision,
            WorkConfiguration.ObjectType objectType,
            List<RuntimeField> fields,
            JsonNode values,
            JsonNode cardValues,
            KanbanSettings kanban) {
        public RuntimeView {
            fields = List.copyOf(fields);
        }

        static RuntimeView empty(WorkConfiguration.ObjectType type) {
            var empty = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            return new RuntimeView(0, 0, type, List.of(), empty, empty.deepCopy(),
                    new KanbanSettings(null, null, null, null, null, null));
        }
    }
}
