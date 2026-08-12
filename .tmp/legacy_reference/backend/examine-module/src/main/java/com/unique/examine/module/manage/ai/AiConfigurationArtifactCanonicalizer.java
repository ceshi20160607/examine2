package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import com.unique.examine.module.manage.service.StructuredPropertyValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Set;

/** Resolves provider intent through the native configuration validators. */
@Component
final class AiConfigurationArtifactCanonicalizer {
    private static final Set<ConfigTypes.FieldType> DERIVED_FIELDS = Set.of(
            ConfigTypes.FieldType.FORMULA,
            ConfigTypes.FieldType.SUMMARY,
            ConfigTypes.FieldType.CALCULATED,
            ConfigTypes.FieldType.LOOKUP,
            ConfigTypes.FieldType.AGGREGATE,
            ConfigTypes.FieldType.AI_FILL);
    private static final Set<ConfigTypes.FieldType> SYSTEM_COMPUTED_FIELDS = Set.of(
            ConfigTypes.FieldType.TENANT,
            ConfigTypes.FieldType.AUTO_NUMBER,
            ConfigTypes.FieldType.CREATED_BY,
            ConfigTypes.FieldType.CREATED_AT,
            ConfigTypes.FieldType.UPDATED_BY,
            ConfigTypes.FieldType.UPDATED_AT);

    private final StructuredPropertyValidator validator;
    private final ObjectMapper json;

    AiConfigurationArtifactCanonicalizer(
            StructuredPropertyValidator validator, ObjectMapper json) {
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    AiConfigurationArtifactFacade.FilterScenarioPreview filterScenario(
            AiConfigurationArtifactFacade.FilterScenarioDraft draft,
            AiConfigurationArtifactContextReader.FilterScenarioSnapshot snapshot) {
        var current = canonicalLayout(snapshot.page().layout());
        var merged = current.deepCopy();
        var scenarios = merged.has("filterScenarios")
                ? (ArrayNode) merged.get("filterScenarios")
                : merged.putArray("filterScenarios");
        var candidate = scenarioNode(draft.scenario());
        var replaced = false;
        for (var index = 0; index < scenarios.size(); index++) {
            if (draft.scenario().code().equals(
                    scenarios.get(index).path("code").asText())) {
                scenarios.set(index, candidate);
                replaced = true;
                break;
            }
        }
        if (!replaced) scenarios.add(candidate);
        if (draft.makeDefault()) {
            merged.put("defaultFilterScenarioCode", draft.scenario().code());
        }
        var resolved = canonicalLayout(merged);
        if (resolved.equals(current)) {
            throw conflict(
                    "AI_CONFIG_FILTER_SCENARIO_NOOP",
                    "The shared filter scenario already has the requested state");
        }
        var state = state(resolved);
        var canonicalScenario = state.filterScenarios().stream()
                .filter(value -> value.code().equals(draft.scenario().code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Native scenario validator omitted the proposed scenario"));
        return new AiConfigurationArtifactFacade.FilterScenarioPreview(
                snapshot.page().id(), snapshot.page().code(),
                Long.parseLong(snapshot.page().version()), draft.makeDefault(),
                canonicalScenario, state, resolved);
    }

    AiConfigurationArtifactFacade.FieldPermissionStagePreview fieldPermission(
            String moduleCode,
            AiConfigurationArtifactFacade.FieldPermissionStageDraft draft,
            AiConfigurationArtifactContextReader.FieldPermissionSnapshot snapshot) {
        var field = snapshot.field();
        var currentRead = mode(field.readPermissionMode());
        var currentWrite = mode(field.writePermissionMode());
        if (draft.stageRead()
                && currentRead == AiConfigurationArtifactFacade.FieldPermissionMode.ENFORCED
                || draft.stageWrite()
                && currentWrite == AiConfigurationArtifactFacade.FieldPermissionMode.ENFORCED) {
            throw conflict(
                    "AI_CONFIG_FIELD_PERMISSION_ENFORCED",
                    "AI cannot replace or demote an enforced field permission");
        }
        if (draft.stageWrite() && !supportsWrite(field.type(), field.readonly())) {
            throw invalid(
                    "AI_CONFIG_FIELD_WRITE_UNSUPPORTED",
                    "Write permission staging is unavailable for this field");
        }
        var transitions = (draft.stageRead()
                && currentRead == AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT ? 1 : 0)
                + (draft.stageWrite()
                && currentWrite == AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT ? 1 : 0);
        if (transitions == 0) {
            throw conflict(
                    "AI_CONFIG_FIELD_PERMISSION_NOOP",
                    "At least one selected permission must move from INHERIT to STAGED");
        }
        return new AiConfigurationArtifactFacade.FieldPermissionStagePreview(
                field.id(), field.code(), field.name(),
                Long.parseLong(field.version()), draft.stageRead(),
                draft.stageWrite(), currentRead, currentWrite,
                draft.stageRead()
                        ? AiConfigurationArtifactFacade.FieldPermissionMode.STAGED
                        : currentRead,
                draft.stageWrite()
                        ? AiConfigurationArtifactFacade.FieldPermissionMode.STAGED
                        : currentWrite,
                FieldPermissionCodes.read(moduleCode, field.code()),
                FieldPermissionCodes.write(moduleCode, field.code()));
    }

    AiConfigurationArtifactFacade.FilterScenarioState state(JsonNode layout) {
        var scenarios = new ArrayList<AiConfigurationArtifactFacade.FilterScenario>();
        for (var value : layout.path("filterScenarios")) {
            scenarios.add(new AiConfigurationArtifactFacade.FilterScenario(
                    value.path("code").asText(), value.path("name").asText(),
                    value.get("filter"), value.get("sort")));
        }
        var defaultCode = layout.hasNonNull("defaultFilterScenarioCode")
                ? layout.path("defaultFilterScenarioCode").asText() : null;
        return new AiConfigurationArtifactFacade.FilterScenarioState(
                scenarios, defaultCode);
    }

    private ObjectNode canonicalLayout(JsonNode value) {
        try {
            return (ObjectNode) json.readTree(validator.layout(
                    ConfigTypes.PageType.LIST, value));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot read canonical LIST-page layout", failure);
        }
    }

    private ObjectNode scenarioNode(
            AiConfigurationArtifactFacade.FilterScenario value) {
        var result = json.createObjectNode();
        result.put("code", value.code());
        result.put("name", value.name());
        result.set("filter", value.filter().deepCopy());
        result.set("sort", value.sort().deepCopy());
        return result;
    }

    static AiConfigurationArtifactFacade.FieldPermissionMode mode(
            ConfigTypes.FieldPermissionMode value) {
        return AiConfigurationArtifactFacade.FieldPermissionMode.valueOf(
                value.name());
    }

    static ConfigTypes.FieldPermissionMode mode(
            AiConfigurationArtifactFacade.FieldPermissionMode value) {
        return ConfigTypes.FieldPermissionMode.valueOf(value.name());
    }

    private static boolean supportsWrite(
            ConfigTypes.FieldType type, boolean readonly) {
        return !readonly && type != ConfigTypes.FieldType.REFERENCE
                && !DERIVED_FIELDS.contains(type)
                && !SYSTEM_COMPUTED_FIELDS.contains(type);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
