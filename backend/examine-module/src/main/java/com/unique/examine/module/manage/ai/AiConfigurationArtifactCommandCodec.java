package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Strict canonical codec for sealed configuration artifact commands. */
final class AiConfigurationArtifactCommandCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "proposalId", "sessionId", "turnId", "accountId", "systemId",
            "tenantId", "memberId", "authorizationEpoch",
            "effectivePermissions", "configRootId", "moduleId", "moduleCode",
            "expectedDraftRevision", "operation", "selectionField",
            "pageLayout", "filterScenario", "fieldPermissionStage",
            "policyVersionId", "providerId", "providerVersion",
            "promptVersion", "expiresAt", "requestId", "traceId");
    private static final Set<String> SELECTION_PREVIEW_FIELDS = Set.of(
            "sortOrder", "draft");
    private static final Set<String> SELECTION_FIELDS = Set.of(
            "fieldCode", "fieldName", "fieldType", "required",
            "dictionaryCode", "dictionaryName", "options", "maxSelections");
    private static final Set<String> OPTION_FIELDS = Set.of(
            "code", "label", "semanticKey", "color", "defaultOption");
    private static final Set<String> PAGE_PREVIEW_FIELDS = Set.of(
            "pageId", "pageCode", "pageType", "pageVersion", "layout");
    private static final Set<String> LAYOUT_FIELDS = Set.of(
            "columns", "gap", "labelPosition", "density", "stickyActions",
            "pageSize", "searchEnabled", "filterEnabled", "sections");
    private static final Set<String> SECTION_FIELDS = Set.of(
            "code", "title", "fieldCodes");
    private static final Set<String> FILTER_PREVIEW_FIELDS = Set.of(
            "pageId", "pageCode", "pageVersion", "makeDefault", "scenario",
            "resolvedState", "resolvedLayout");
    private static final Set<String> FILTER_SCENARIO_FIELDS = Set.of(
            "code", "name", "filter", "sort");
    private static final Set<String> FILTER_STATE_FIELDS = Set.of(
            "filterScenarios", "defaultFilterScenarioCode");
    private static final Set<String> FIELD_PERMISSION_PREVIEW_FIELDS = Set.of(
            "fieldId", "fieldCode", "fieldName", "fieldVersion",
            "stageRead", "stageWrite", "expectedReadPermissionMode",
            "expectedWritePermissionMode", "readPermissionMode",
            "writePermissionMode", "readPermissionCode", "writePermissionCode");

    private final ObjectMapper json;

    AiConfigurationArtifactCommandCodec(ObjectMapper json) {
        this.json = java.util.Objects.requireNonNull(json, "json").copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    Command command(
            AiConfigurationArtifactFacade.PrepareRequest request,
            long configRootId,
            long moduleId,
            long expectedDraftRevision,
            AiConfigurationArtifactFacade.SelectionFieldPreview selection,
            AiConfigurationArtifactFacade.PageLayoutPreview page,
            AiConfigurationArtifactFacade.FilterScenarioPreview filterScenario,
            AiConfigurationArtifactFacade.FieldPermissionStagePreview fieldPermissionStage,
            Instant expiresAt) {
        return new Command(
                request.proposalId(), request.sessionId(), request.turnId(),
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), configRootId, moduleId,
                request.moduleCode(), expectedDraftRevision,
                request.operation(), selection, page, filterScenario,
                fieldPermissionStage, request.policyVersionId(),
                request.providerId(), request.providerVersion(),
                request.promptVersion(), expiresAt, request.requestId(),
                request.traceId());
    }

    Command command(
            AiConfigurationArtifactFacade.PrepareRequest request,
            long configRootId,
            long moduleId,
            long expectedDraftRevision,
            AiConfigurationArtifactFacade.SelectionFieldPreview selection,
            AiConfigurationArtifactFacade.PageLayoutPreview page,
            Instant expiresAt) {
        return command(request, configRootId, moduleId, expectedDraftRevision,
                selection, page, null, null, expiresAt);
    }

    String encode(Command value) {
        var root = json.createObjectNode();
        root.put("accountId", value.accountId());
        root.put("authorizationEpoch", value.authorizationEpoch());
        root.put("configRootId", value.configRootId());
        var permissions = root.putArray("effectivePermissions");
        value.effectivePermissions().forEach(permissions::add);
        root.put("expectedDraftRevision", value.expectedDraftRevision());
        root.put("expiresAt", value.expiresAt().toString());
        root.put("memberId", value.memberId());
        root.put("moduleCode", value.moduleCode());
        root.put("moduleId", value.moduleId());
        root.put("operation", value.operation().name());
        root.set("pageLayout", tree(value.pageLayout()));
        root.set("filterScenario", tree(value.filterScenario()));
        root.set("fieldPermissionStage", tree(value.fieldPermissionStage()));
        root.put("policyVersionId", value.policyVersionId());
        root.put("promptVersion", value.promptVersion());
        root.put("proposalId", value.proposalId());
        root.put("providerId", value.providerId());
        root.put("providerVersion", value.providerVersion());
        root.put("requestId", value.requestId());
        root.set("selectionField", tree(value.selectionField()));
        root.put("sessionId", value.sessionId());
        root.put("systemId", value.systemId());
        root.put("tenantId", value.tenantId());
        root.put("traceId", value.traceId());
        root.put("turnId", value.turnId());
        return write(root);
    }

    Command decode(String value) {
        try {
            var raw = json.readTree(value);
            if (!(raw instanceof ObjectNode root) || !exact(root, ROOT_FIELDS)) {
                throw invalid();
            }
            validateNested(root);
            var command = new Command(
                    text(root, "proposalId"), text(root, "sessionId"),
                    text(root, "turnId"), positive(root, "accountId"),
                    positive(root, "systemId"), positive(root, "tenantId"),
                    positive(root, "memberId"),
                    positive(root, "authorizationEpoch"),
                    permissions(root.get("effectivePermissions")),
                    positive(root, "configRootId"), positive(root, "moduleId"),
                    text(root, "moduleCode"),
                    nonNegative(root, "expectedDraftRevision"),
                    enumValue(root, "operation",
                            AiConfigurationArtifactFacade.Operation.class),
                    nullable(root.get("selectionField"),
                            AiConfigurationArtifactFacade.SelectionFieldPreview.class),
                    nullable(root.get("pageLayout"),
                            AiConfigurationArtifactFacade.PageLayoutPreview.class),
                    nullable(root.get("filterScenario"),
                            AiConfigurationArtifactFacade.FilterScenarioPreview.class),
                    nullable(root.get("fieldPermissionStage"),
                            AiConfigurationArtifactFacade.FieldPermissionStagePreview.class),
                    text(root, "policyVersionId"), text(root, "providerId"),
                    nonNegative(root, "providerVersion"),
                    text(root, "promptVersion"), instant(root, "expiresAt"),
                    text(root, "requestId"), text(root, "traceId"));
            validate(command);
            if (!encode(command).equals(value)) throw invalid();
            return command;
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException | JsonProcessingException failure) {
            throw invalid();
        }
    }

    private void validate(Command value) {
        var selection = value.selectionField() == null
                ? null : value.selectionField().draft();
        var page = value.pageLayout() == null ? null
                : new AiConfigurationArtifactFacade.PageLayoutDraft(
                value.pageLayout().pageCode(), value.pageLayout().pageType(),
                value.pageLayout().layout());
        var filterScenario = value.filterScenario() == null ? null
                : new AiConfigurationArtifactFacade.FilterScenarioDraft(
                value.filterScenario().pageCode(),
                value.filterScenario().scenario(),
                value.filterScenario().makeDefault());
        var fieldPermissionStage = value.fieldPermissionStage() == null ? null
                : new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                value.fieldPermissionStage().fieldCode(),
                value.fieldPermissionStage().stageRead(),
                value.fieldPermissionStage().stageWrite());
        new AiConfigurationArtifactFacade.PrepareRequest(
                value.proposalId(), value.sessionId(), value.turnId(),
                value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.authorizationEpoch(),
                value.effectivePermissions(), value.moduleCode(),
                value.operation(), selection, page, filterScenario,
                fieldPermissionStage, value.policyVersionId(),
                value.providerId(), value.providerVersion(),
                value.promptVersion(), value.requestId(), value.traceId());
        if (value.selectionField() != null
                && value.selectionField().sortOrder() < 0) throw invalid();
    }

    private void validateNested(ObjectNode root) {
        var selection = root.get("selectionField");
        if (selection != null && !selection.isNull()) {
            var preview = object(selection, SELECTION_PREVIEW_FIELDS);
            var draft = object(preview.get("draft"), SELECTION_FIELDS);
            var options = draft.get("options");
            if (options == null || !options.isArray()) throw invalid();
            options.forEach(option -> object(option, OPTION_FIELDS));
        }
        var page = root.get("pageLayout");
        if (page != null && !page.isNull()) {
            var preview = object(page, PAGE_PREVIEW_FIELDS);
            var layout = object(preview.get("layout"), LAYOUT_FIELDS);
            var sections = layout.get("sections");
            if (sections == null || !sections.isArray()) throw invalid();
            sections.forEach(section -> object(section, SECTION_FIELDS));
        }
        var filterScenario = root.get("filterScenario");
        if (filterScenario != null && !filterScenario.isNull()) {
            var preview = object(filterScenario, FILTER_PREVIEW_FIELDS);
            object(preview.get("scenario"), FILTER_SCENARIO_FIELDS);
            var state = object(preview.get("resolvedState"), FILTER_STATE_FIELDS);
            var scenarios = state.get("filterScenarios");
            if (scenarios == null || !scenarios.isArray()) throw invalid();
            scenarios.forEach(value -> object(value, FILTER_SCENARIO_FIELDS));
            if (!(preview.get("resolvedLayout") instanceof ObjectNode)) {
                throw invalid();
            }
        }
        var fieldPermission = root.get("fieldPermissionStage");
        if (fieldPermission != null && !fieldPermission.isNull()) {
            object(fieldPermission, FIELD_PERMISSION_PREVIEW_FIELDS);
        }
    }

    private <T> T nullable(JsonNode value, Class<T> type)
            throws JsonProcessingException {
        return value == null || value.isNull() ? null
                : json.treeToValue(value, type);
    }

    private JsonNode tree(Object value) {
        return value == null ? json.nullNode() : json.valueToTree(value);
    }

    private static ObjectNode object(JsonNode value, Set<String> fields) {
        if (!(value instanceof ObjectNode object) || !exact(object, fields)) {
            throw invalid();
        }
        return object;
    }

    private static boolean exact(ObjectNode value, Set<String> fields) {
        if (value.size() != fields.size()) return false;
        var names = new HashSet<String>();
        value.fieldNames().forEachRemaining(names::add);
        return names.equals(fields);
    }

    private static Set<String> permissions(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 512) {
            throw invalid();
        }
        var sorted = new TreeSet<String>();
        String previous = null;
        for (var item : value) {
            if (!item.isTextual() || item.textValue().isBlank()
                    || item.textValue().length() > 128
                    || previous != null
                    && previous.compareTo(item.textValue()) >= 0) {
                throw invalid();
            }
            previous = item.textValue();
            sorted.add(item.textValue());
        }
        return Collections.unmodifiableSortedSet(sorted);
    }

    private static String text(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || !node.isTextual()) throw invalid();
        return node.textValue();
    }

    private static long positive(ObjectNode value, String name) {
        var result = nonNegative(value, name);
        if (result <= 0) throw invalid();
        return result;
    }

    private static long nonNegative(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()
                || node.longValue() < 0) throw invalid();
        return node.longValue();
    }

    private static <T extends Enum<T>> T enumValue(
            ObjectNode value, String name, Class<T> type) {
        try {
            return Enum.valueOf(type, text(value, name));
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    private static Instant instant(ObjectNode value, String name) {
        try {
            return Instant.parse(text(value, name));
        } catch (DateTimeParseException failure) {
            throw invalid();
        }
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode configuration artifact command", failure);
        }
    }

    static BusinessException invalid() {
        return new BusinessException(
                "AI_CONFIG_ARTIFACT_COMMAND_INVALID",
                "The configuration artifact command is invalid",
                HttpStatus.CONFLICT);
    }

    record Command(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            long configRootId,
            long moduleId,
            String moduleCode,
            long expectedDraftRevision,
            AiConfigurationArtifactFacade.Operation operation,
            AiConfigurationArtifactFacade.SelectionFieldPreview selectionField,
            AiConfigurationArtifactFacade.PageLayoutPreview pageLayout,
            AiConfigurationArtifactFacade.FilterScenarioPreview filterScenario,
            AiConfigurationArtifactFacade.FieldPermissionStagePreview fieldPermissionStage,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String requestId,
            String traceId
    ) {
        Command {
            effectivePermissions = Collections.unmodifiableSortedSet(
                    new TreeSet<>(effectivePermissions));
        }
    }
}
