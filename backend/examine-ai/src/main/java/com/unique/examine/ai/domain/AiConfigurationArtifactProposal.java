package com.unique.examine.ai.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Bound proposal for one of the strictly supported configuration draft artifacts. */
public record AiConfigurationArtifactProposal(
        long id,
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        long sessionId,
        long turnId,
        long policyVersionId,
        long providerId,
        long providerVersion,
        long authorizationEpoch,
        String promptVersion,
        AiConfigurationArtifactFacade.Operation operation,
        String moduleCode,
        String planHash,
        State state,
        long revision,
        Preview preview,
        double confidence,
        String clarification,
        SealedCommand sealedCommand,
        Instant expiresAt,
        Long actedBy,
        Result result,
        String resultCode,
        String requestId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
    public static final String REDACTED_NAME = "[name:redacted]";
    public static final String REDACTED_LABEL = "[label:redacted]";
    public static final String REDACTED_VALUE = "[value:redacted]";
    public static final String REDACTED_CLARIFICATION = "[clarification:redacted]";

    public AiConfigurationArtifactProposal {
        positive(id, "id"); positive(accountId, "accountId");
        positive(systemId, "systemId"); positive(tenantId, "tenantId");
        positive(memberId, "memberId"); positive(sessionId, "sessionId");
        positive(turnId, "turnId"); positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        if (providerVersion < 0 || authorizationEpoch <= 0 || revision < 0) {
            invalid("snapshot");
        }
        promptVersion = token(promptVersion, "promptVersion", 64);
        operation = Objects.requireNonNull(operation, "operation");
        if (moduleCode != null) moduleCode = code(moduleCode, "moduleCode");
        hash(planHash, "planHash");
        state = Objects.requireNonNull(state, "state");
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            invalid("confidence");
        }
        if (clarification != null) clarification = text(
                clarification, "clarification", 500);
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            invalid("timestamps");
        }
        resultCode = token(resultCode, "resultCode", 64);
        requestId = token(requestId, "requestId", 64);
        traceId = token(traceId, "traceId", 64);
        if (actedBy != null && actedBy <= 0) invalid("actedBy");
        if (state == State.CLARIFICATION_REQUIRED) {
            if (moduleCode != null || preview != null || sealedCommand != null
                    || clarification == null || actedBy != null || result != null
                    || finishedAt != null) invalid("clarification state");
        } else {
            moduleCode = code(moduleCode, "moduleCode");
            preview = Objects.requireNonNull(preview, "preview");
            if (preview.operation() != operation) invalid("preview operation");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            if (clarification != null) invalid("actionable clarification");
        }
        if (state == State.PENDING) {
            if (actedBy != null || result != null || finishedAt != null) {
                invalid("pending state");
            }
        } else if (state == State.EXECUTING) {
            positive(Objects.requireNonNull(actedBy, "actedBy"), "actedBy");
            if (result != null || finishedAt != null) invalid("executing state");
        } else if (state != State.CLARIFICATION_REQUIRED && finishedAt == null) {
            invalid("terminal state");
        }
        if (state == State.SUCCEEDED && (result == null
                || result.operation() != operation)) invalid("success result");
        if (state != State.SUCCEEDED && result != null) invalid("non-success result");
    }

    public ArtifactKind artifactKind() {
        return switch (operation) {
            case CONFIG_SELECTION_FIELD_DRAFT -> ArtifactKind.SELECTION_FIELD;
            case CONFIG_PAGE_LAYOUT_DRAFT -> ArtifactKind.PAGE_LAYOUT;
            case CONFIG_FILTER_SCENARIO_DRAFT -> ArtifactKind.FILTER_SCENARIO;
            case CONFIG_FIELD_PERMISSION_STAGE_DRAFT ->
                    ArtifactKind.FIELD_PERMISSION_STAGE;
        };
    }

    public enum ArtifactKind {
        SELECTION_FIELD, PAGE_LAYOUT, FILTER_SCENARIO, FIELD_PERMISSION_STAGE
    }
    public enum State {
        CLARIFICATION_REQUIRED, PENDING, EXECUTING,
        SUCCEEDED, FAILED, REJECTED, EXPIRED
    }

    public record Option(
            String code, String label, String semanticKey, String color,
            boolean defaultOption, int sortOrder) {
        public Option {
            code = text(code, "option code", 64);
            label = text(label, "option label", 128);
            if (semanticKey != null) semanticKey = text(
                    semanticKey, "semanticKey", 32);
            if (color != null) color = text(color, "color", 9);
            if (sortOrder < 0) invalid("option sortOrder");
        }
    }

    public record SelectionPreview(
            int sortOrder,
            String fieldCode,
            String fieldName,
            String fieldType,
            boolean required,
            String dictionaryCode,
            String dictionaryName,
            List<Option> options,
            Integer maxSelections
    ) {
        public SelectionPreview {
            if (sortOrder < 0) invalid("field sortOrder");
            fieldCode = text(fieldCode, "fieldCode", 64);
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = token(fieldType, "fieldType", 32);
            dictionaryCode = text(dictionaryCode, "dictionaryCode", 64);
            dictionaryName = text(dictionaryName, "dictionaryName", 128);
            options = List.copyOf(Objects.requireNonNull(options, "options"));
        }
    }

    public record Section(
            String code, String title, List<String> fieldCodes, int sortOrder) {
        public Section {
            code = text(code, "section code", 64);
            title = text(title, "section title", 128);
            fieldCodes = List.copyOf(Objects.requireNonNull(
                    fieldCodes, "fieldCodes"));
            if (sortOrder < 0) invalid("section sortOrder");
        }
    }

    public record Layout(
            int columns,
            int gap,
            String labelPosition,
            String density,
            boolean stickyActions,
            Integer pageSize,
            Boolean searchEnabled,
            Boolean filterEnabled,
            List<Section> sections,
            int sectionCount,
            int fieldCount,
            boolean redacted
    ) {
        public Layout {
            if (columns < 1 || gap < 0 || sectionCount < 1 || fieldCount < 1) {
                invalid("layout bounds");
            }
            labelPosition = token(labelPosition, "labelPosition", 16);
            density = token(density, "density", 16);
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (redacted && !sections.isEmpty()) invalid("redacted sections");
            if (!redacted && sections.size() != sectionCount) {
                invalid("section count");
            }
        }
    }

    public record PagePreview(
            String pageId,
            String pageCode,
            String pageType,
            long pageVersion,
            Layout layout
    ) {
        public PagePreview {
            pageId = positiveDecimal(pageId, "pageId");
            pageCode = text(pageCode, "pageCode", 64);
            pageType = token(pageType, "pageType", 32);
            if (pageVersion < 0) invalid("pageVersion");
            layout = Objects.requireNonNull(layout, "layout");
        }
    }

    public record FilterScenario(
            String code, String name, JsonNode filter, JsonNode sort) {
        public FilterScenario {
            code = scenarioCode(code, "scenario code");
            name = text(name, "scenario name", 100);
            filter = filter == null ? null : filter.deepCopy();
            sort = Objects.requireNonNull(sort, "sort").deepCopy();
            if (filter != null && !filter.isNull() && !filter.isObject()
                    || !sort.isArray()
                    || jsonBytes(filter) + jsonBytes(sort) > 32 * 1024) {
                invalid("scenario filter/sort");
            }
        }
    }

    public record FilterScenarioState(
            List<FilterScenario> filterScenarios,
            String defaultFilterScenarioCode) {
        public FilterScenarioState {
            filterScenarios = List.copyOf(Objects.requireNonNull(
                    filterScenarios, "filterScenarios"));
            if (filterScenarios.isEmpty() || filterScenarios.size() > 10) {
                invalid("filter scenarios");
            }
            var codes = new HashSet<String>();
            for (var scenario : filterScenarios) {
                if (scenario == null || !codes.add(scenario.code())) {
                    invalid("filter scenario codes");
                }
            }
            if (defaultFilterScenarioCode != null) {
                defaultFilterScenarioCode = scenarioCode(
                        defaultFilterScenarioCode, "default scenario code");
                if (!codes.contains(defaultFilterScenarioCode)) {
                    invalid("default scenario code");
                }
            }
        }
    }

    public record FilterScenarioPreview(
            String pageId,
            String pageCode,
            long pageVersion,
            boolean makeDefault,
            FilterScenario scenario,
            FilterScenarioState resolvedState,
            JsonNode resolvedLayout
    ) {
        public FilterScenarioPreview {
            pageId = positiveDecimal(pageId, "filter pageId");
            pageCode = text(pageCode, "filter pageCode", 64);
            if (pageVersion < 0) invalid("filter pageVersion");
            scenario = Objects.requireNonNull(scenario, "scenario");
            resolvedState = Objects.requireNonNull(resolvedState, "resolvedState");
            resolvedLayout = Objects.requireNonNull(
                    resolvedLayout, "resolvedLayout").deepCopy();
            if (!resolvedLayout.isObject()) invalid("resolved layout");
        }
    }

    public record FieldPermissionStagePreview(
            String fieldId,
            String fieldCode,
            String fieldName,
            long fieldVersion,
            boolean stageRead,
            boolean stageWrite,
            String expectedReadPermissionMode,
            String expectedWritePermissionMode,
            String readPermissionMode,
            String writePermissionMode,
            String readPermissionCode,
            String writePermissionCode
    ) {
        public FieldPermissionStagePreview {
            fieldId = positiveDecimal(fieldId, "permission fieldId");
            fieldCode = text(fieldCode, "permission fieldCode", 64);
            fieldName = text(fieldName, "permission fieldName", 128);
            if (fieldVersion < 0 || (!stageRead && !stageWrite)) {
                invalid("permission stage");
            }
            expectedReadPermissionMode = permissionMode(
                    expectedReadPermissionMode, "expected read permission mode");
            expectedWritePermissionMode = permissionMode(
                    expectedWritePermissionMode, "expected write permission mode");
            readPermissionMode = permissionMode(
                    readPermissionMode, "read permission mode");
            writePermissionMode = permissionMode(
                    writePermissionMode, "write permission mode");
            readPermissionCode = permissionCode(
                    readPermissionCode, fieldCode, "read");
            writePermissionCode = permissionCode(
                    writePermissionCode, fieldCode, "write");
            validateStageTransition(stageRead, stageWrite,
                    expectedReadPermissionMode, expectedWritePermissionMode,
                    readPermissionMode, writePermissionMode);
        }
    }

    public record Preview(
            AiConfigurationArtifactFacade.Operation operation,
            String configRootId,
            String moduleId,
            String moduleCode,
            long expectedDraftRevision,
            long nextDraftRevision,
            SelectionPreview selectionField,
            PagePreview pageLayout,
            FilterScenarioPreview filterScenario,
            FieldPermissionStagePreview fieldPermissionStage
    ) {
        public Preview(
                AiConfigurationArtifactFacade.Operation operation,
                String configRootId,
                String moduleId,
                String moduleCode,
                long expectedDraftRevision,
                long nextDraftRevision,
                SelectionPreview selectionField,
                PagePreview pageLayout) {
            this(operation, configRootId, moduleId, moduleCode,
                    expectedDraftRevision, nextDraftRevision, selectionField,
                    pageLayout, null, null);
        }

        public Preview {
            operation = Objects.requireNonNull(operation, "operation");
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "preview moduleCode");
            if (expectedDraftRevision < 0
                    || nextDraftRevision != expectedDraftRevision + 1) {
                invalid("draft revision");
            }
            matching(operation, selectionField, pageLayout,
                    filterScenario, fieldPermissionStage);
        }
    }

    public record SealedCommand(
            String ciphertext, String keyVersion, String commandHash) {
        public SealedCommand {
            ciphertext = text(ciphertext, "ciphertext", 262_144);
            keyVersion = token(keyVersion, "keyVersion", 64);
            hash(commandHash, "commandHash");
        }
    }

    public record DictionaryResult(
            String dictionaryId, String dictionaryCode,
            String dictionaryName, long version) {
        public DictionaryResult {
            dictionaryId = positiveDecimal(dictionaryId, "dictionaryId");
            dictionaryCode = text(dictionaryCode, "dictionaryCode", 64);
            dictionaryName = text(dictionaryName, "dictionaryName", 128);
            if (version < 0) invalid("dictionary version");
        }
    }

    public record OptionResult(
            String optionId, String code, String label, String semanticKey,
            String color, boolean defaultOption, int sortOrder, long version) {
        public OptionResult {
            optionId = positiveDecimal(optionId, "optionId");
            code = text(code, "option code", 64);
            label = text(label, "option label", 128);
            if (semanticKey != null) semanticKey = text(
                    semanticKey, "semanticKey", 32);
            if (color != null) color = text(color, "color", 9);
            if (sortOrder < 0 || version < 0) invalid("option version");
        }
    }

    public record SelectionResult(
            DictionaryResult dictionary,
            List<OptionResult> options,
            String fieldId,
            String fieldCode,
            String fieldName,
            String fieldType,
            boolean required,
            String dictionaryId,
            int sortOrder,
            Integer maxSelections,
            long version
    ) {
        public SelectionResult {
            dictionary = Objects.requireNonNull(dictionary, "dictionary");
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = text(fieldCode, "fieldCode", 64);
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = token(fieldType, "fieldType", 32);
            dictionaryId = positiveDecimal(dictionaryId, "field dictionaryId");
            if (sortOrder < 0 || version < 0) invalid("field version");
        }
    }

    public record PageResult(
            String pageId, String pageCode, String pageType,
            long version, Layout layout) {
        public PageResult {
            pageId = positiveDecimal(pageId, "result pageId");
            pageCode = text(pageCode, "result pageCode", 64);
            pageType = token(pageType, "result pageType", 32);
            if (version < 0) invalid("result pageVersion");
            layout = Objects.requireNonNull(layout, "result layout");
        }
    }

    public record FilterScenarioResult(
            String pageId,
            String pageCode,
            long version,
            FilterScenarioState state
    ) {
        public FilterScenarioResult {
            pageId = positiveDecimal(pageId, "result filter pageId");
            pageCode = text(pageCode, "result filter pageCode", 64);
            if (version < 0) invalid("result filter pageVersion");
            state = Objects.requireNonNull(state, "state");
        }
    }

    public record FieldPermissionStageResult(
            String fieldId,
            String fieldCode,
            String fieldName,
            long version,
            String readPermissionMode,
            String writePermissionMode,
            String readPermissionCode,
            String writePermissionCode
    ) {
        public FieldPermissionStageResult {
            fieldId = positiveDecimal(fieldId, "result permission fieldId");
            fieldCode = text(fieldCode, "result permission fieldCode", 64);
            fieldName = text(fieldName, "result permission fieldName", 128);
            if (version < 0) invalid("result permission fieldVersion");
            readPermissionMode = permissionMode(
                    readPermissionMode, "result read permission mode");
            writePermissionMode = permissionMode(
                    writePermissionMode, "result write permission mode");
            readPermissionCode = permissionCode(
                    readPermissionCode, fieldCode, "read");
            writePermissionCode = permissionCode(
                    writePermissionCode, fieldCode, "write");
        }
    }

    public record Result(
            AiConfigurationArtifactFacade.Operation operation,
            String configRootId,
            String moduleId,
            String moduleCode,
            long draftRevision,
            SelectionResult selectionField,
            PageResult pageLayout,
            FilterScenarioResult filterScenario,
            FieldPermissionStageResult fieldPermissionStage,
            String draftStatus
    ) {
        public Result(
                AiConfigurationArtifactFacade.Operation operation,
                String configRootId,
                String moduleId,
                String moduleCode,
                long draftRevision,
                SelectionResult selectionField,
                PageResult pageLayout,
                String draftStatus) {
            this(operation, configRootId, moduleId, moduleCode, draftRevision,
                    selectionField, pageLayout, null, null, draftStatus);
        }

        public Result {
            operation = Objects.requireNonNull(operation, "operation");
            configRootId = positiveDecimal(configRootId, "result configRootId");
            moduleId = positiveDecimal(moduleId, "result moduleId");
            moduleCode = code(moduleCode, "result moduleCode");
            positive(draftRevision, "draftRevision");
            matching(operation, selectionField, pageLayout,
                    filterScenario, fieldPermissionStage);
            if (!"DRAFT".equals(draftStatus)) invalid("draftStatus");
        }
    }

    public record Attempt(
            long id, long accountId, long systemId, long tenantId, long memberId,
            long sessionId, long turnId, long policyVersionId, long providerId,
            long providerVersion, long proposalId, String action,
            String requestKey, String requestHash, State status,
            String resultHash, String resultCode,
            Instant createdAt, Instant finishedAt
    ) {
        public Attempt {
            positive(id, "attempt id"); positive(accountId, "attempt accountId");
            positive(systemId, "attempt systemId"); positive(tenantId, "attempt tenantId");
            positive(memberId, "attempt memberId"); positive(sessionId, "attempt sessionId");
            positive(turnId, "attempt turnId"); positive(policyVersionId, "attempt policy");
            positive(providerId, "attempt provider"); positive(proposalId, "attempt proposal");
            if (providerVersion < 0) invalid("attempt providerVersion");
            action = token(action, "action", 16); requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash"); status = Objects.requireNonNull(status, "status");
            if (resultHash != null) hash(resultHash, "resultHash");
            resultCode = token(resultCode, "resultCode", 64);
            Objects.requireNonNull(createdAt, "createdAt");
            if ((status == State.EXECUTING) == (finishedAt != null)) {
                invalid("attempt finishedAt");
            }
        }
    }

    public record Event(
            long id, long accountId, long systemId, long tenantId, long memberId,
            long sessionId, long turnId, long policyVersionId, long providerId,
            long providerVersion, long proposalId, Long attemptId,
            String eventType, State fromState, State toState, long revision,
            long actorMemberId, String requestId, String traceId,
            String resultCode, String eventHash, Instant createdAt
    ) {
        public Event {
            positive(id, "event id"); positive(accountId, "event accountId");
            positive(systemId, "event systemId"); positive(tenantId, "event tenantId");
            positive(memberId, "event memberId"); positive(sessionId, "event sessionId");
            positive(turnId, "event turnId"); positive(policyVersionId, "event policy");
            positive(providerId, "event provider"); positive(proposalId, "event proposal");
            if (providerVersion < 0 || revision < 0) invalid("event version");
            if (attemptId != null && attemptId <= 0) invalid("attemptId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            positive(actorMemberId, "actorMemberId");
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
            resultCode = token(resultCode, "resultCode", 64);
            hash(eventHash, "eventHash"); Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static void matching(
            AiConfigurationArtifactFacade.Operation operation,
            Object selection, Object page, Object filterScenario,
            Object fieldPermissionStage) {
        if ((operation == AiConfigurationArtifactFacade.Operation
                .CONFIG_SELECTION_FIELD_DRAFT) != (selection != null)
                || (operation == AiConfigurationArtifactFacade.Operation
                .CONFIG_PAGE_LAYOUT_DRAFT) != (page != null)
                || (operation == AiConfigurationArtifactFacade.Operation
                .CONFIG_FILTER_SCENARIO_DRAFT) != (filterScenario != null)
                || (operation == AiConfigurationArtifactFacade.Operation
                .CONFIG_FIELD_PERMISSION_STAGE_DRAFT)
                != (fieldPermissionStage != null)) invalid("artifact payload");
    }

    private static String positiveDecimal(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("AI artifact " + field + " is invalid", failure);
        }
    }

    private static String code(String value, String field) {
        if (value == null || !value.matches("^[a-z][a-z0-9_]{1,63}$")) invalid(field);
        return value;
    }

    private static String scenarioCode(String value, String field) {
        if (value == null || value.length() > 64
                || !value.matches("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")) {
            invalid(field);
        }
        return value;
    }

    private static String permissionMode(String value, String field) {
        value = token(value, field, 16);
        if (!List.of("INHERIT", "STAGED", "ENFORCED").contains(value)) {
            invalid(field);
        }
        return value;
    }

    private static String permissionCode(
            String value, String fieldCode, String direction) {
        value = token(value, direction + " permission code", 256);
        if (!value.matches("^module\\.[a-z][a-z0-9_]{1,63}\\.field\\."
                + java.util.regex.Pattern.quote(fieldCode)
                + "\\." + direction + "$")) {
            invalid(direction + " permission code");
        }
        return value;
    }

    private static void validateStageTransition(
            boolean stageRead, boolean stageWrite,
            String expectedRead, String expectedWrite,
            String resolvedRead, String resolvedWrite) {
        if (!resolvedRead.equals(stageRead ? "STAGED" : expectedRead)
                || !resolvedWrite.equals(stageWrite ? "STAGED" : expectedWrite)
                || stageRead && !"INHERIT".equals(expectedRead)
                || stageWrite && !"INHERIT".equals(expectedWrite)) {
            invalid("permission stage transition");
        }
    }

    private static int jsonBytes(JsonNode value) {
        return value == null ? 0 : value.toString()
                .getBytes(StandardCharsets.UTF_8).length;
    }

    private static String token(String value, String field, int maximum) {
        value = text(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0," + (maximum - 1) + "}$")) {
            invalid(field);
        }
        return value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) invalid(field);
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) invalid(field);
        return value;
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) invalid(field);
    }
    private static void positive(long value, String field) { if (value <= 0) invalid(field); }
    private static void invalid(String field) {
        throw new IllegalArgumentException("AI configuration artifact " + field + " is invalid");
    }
}
