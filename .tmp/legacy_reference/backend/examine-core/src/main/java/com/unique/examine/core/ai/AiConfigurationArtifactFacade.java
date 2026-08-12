package com.unique.examine.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Confirmation boundary for bounded AI configuration artifacts.
 * Implementations may mutate a draft only from {@link #execute(ExecuteRequest)}.
 */
public interface AiConfigurationArtifactFacade {

    PreparedArtifact prepare(PrepareRequest request);

    ArtifactReadback execute(ExecuteRequest request);

    enum Operation {
        CONFIG_SELECTION_FIELD_DRAFT,
        CONFIG_PAGE_LAYOUT_DRAFT,
        CONFIG_FILTER_SCENARIO_DRAFT,
        CONFIG_FIELD_PERMISSION_STAGE_DRAFT
    }

    enum SelectionType {
        RADIO,
        MULTI_SELECT
    }

    enum PageType {
        LIST,
        FORM,
        DETAIL
    }

    enum LabelPosition {
        TOP,
        LEFT
    }

    enum Density {
        DEFAULT,
        COMPACT
    }

    enum FieldPermissionMode {
        INHERIT,
        STAGED,
        ENFORCED
    }

    record FilterScenario(
            String code,
            String name,
            JsonNode filter,
            JsonNode sort
    ) {
        public FilterScenario {
            code = scenarioCode(code, "scenario.code");
            name = text(name, "scenario.name", 100);
            filter = filter == null ? NullNode.getInstance() : filter.deepCopy();
            sort = Objects.requireNonNull(sort, "scenario.sort").deepCopy();
            if ((!filter.isNull() && !filter.isObject()) || !sort.isArray()) {
                throw new IllegalArgumentException(
                        "scenario filter/sort shape is invalid");
            }
            if (jsonBytes(filter) + jsonBytes(sort) > 32 * 1024) {
                throw new IllegalArgumentException("scenario exceeds 32 KiB");
            }
        }

        @Override
        public JsonNode filter() {
            return filter.deepCopy();
        }

        @Override
        public JsonNode sort() {
            return sort.deepCopy();
        }
    }

    record FilterScenarioState(
            List<FilterScenario> filterScenarios,
            String defaultFilterScenarioCode
    ) {
        public FilterScenarioState {
            filterScenarios = List.copyOf(Objects.requireNonNull(
                    filterScenarios, "filterScenarios"));
            if (filterScenarios.size() > 10) {
                throw new IllegalArgumentException(
                        "filterScenarios may contain at most 10 rows");
            }
            var codes = new HashSet<String>();
            var names = new HashSet<String>();
            for (var scenario : filterScenarios) {
                Objects.requireNonNull(scenario, "filterScenario");
                if (!codes.add(scenario.code())
                        || !names.add(scenario.name().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "filter scenario codes and names must be unique");
                }
            }
            if (defaultFilterScenarioCode != null) {
                defaultFilterScenarioCode = scenarioCode(
                        defaultFilterScenarioCode,
                        "defaultFilterScenarioCode");
                if (!codes.contains(defaultFilterScenarioCode)) {
                    throw new IllegalArgumentException(
                            "defaultFilterScenarioCode is unavailable");
                }
            }
        }
    }

    record FilterScenarioDraft(
            String pageCode,
            FilterScenario scenario,
            boolean makeDefault
    ) {
        public FilterScenarioDraft {
            pageCode = code(pageCode, "pageCode");
            scenario = Objects.requireNonNull(scenario, "scenario");
        }
    }

    record FieldPermissionStageDraft(
            String fieldCode,
            boolean stageRead,
            boolean stageWrite
    ) {
        public FieldPermissionStageDraft {
            fieldCode = code(fieldCode, "fieldCode");
            if (!stageRead && !stageWrite) {
                throw new IllegalArgumentException(
                        "at least one permission direction must be staged");
            }
        }
    }

    record OptionDraft(
            String code,
            String label,
            String semanticKey,
            String color,
            boolean defaultOption
    ) {
        public OptionDraft {
            code = itemCode(code, "option.code");
            label = text(label, "option.label", 128);
            semanticKey = optionalCode(semanticKey, "option.semanticKey", 32);
            color = optionalColor(color);
        }
    }

    record SelectionFieldDraft(
            String fieldCode,
            String fieldName,
            SelectionType fieldType,
            boolean required,
            String dictionaryCode,
            String dictionaryName,
            List<OptionDraft> options,
            Integer maxSelections
    ) {
        public SelectionFieldDraft {
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = Objects.requireNonNull(fieldType, "fieldType");
            dictionaryCode = code(dictionaryCode, "dictionaryCode");
            dictionaryName = text(dictionaryName, "dictionaryName", 128);
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (options.size() < 2 || options.size() > 50) {
                throw new IllegalArgumentException("options must contain 2..50 rows");
            }
            var codes = new HashSet<String>();
            var defaults = 0;
            for (var option : options) {
                Objects.requireNonNull(option, "option");
                if (!codes.add(option.code().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("option codes must be unique");
                }
                if (option.defaultOption()) defaults++;
            }
            if (defaults > 1) {
                throw new IllegalArgumentException("at most one option may be default");
            }
            if (fieldType == SelectionType.RADIO && maxSelections != null) {
                throw new IllegalArgumentException(
                        "RADIO does not accept maxSelections");
            }
            if (fieldType == SelectionType.MULTI_SELECT
                    && maxSelections != null
                    && (maxSelections < 1 || maxSelections > options.size())) {
                throw new IllegalArgumentException(
                        "maxSelections must be within the option count");
            }
        }
    }

    record PageSection(
            String code,
            String title,
            List<String> fieldCodes
    ) {
        public PageSection {
            code = AiConfigurationArtifactFacade.code(
                    code, "section.code");
            title = text(title, "section.title", 128);
            fieldCodes = List.copyOf(Objects.requireNonNull(
                    fieldCodes, "fieldCodes"));
            if (fieldCodes.isEmpty() || fieldCodes.size() > 50) {
                throw new IllegalArgumentException(
                        "section fieldCodes must contain 1..50 codes");
            }
            var unique = new HashSet<String>();
            fieldCodes = fieldCodes.stream()
                    .map(value -> AiConfigurationArtifactFacade.code(
                            value, "section.fieldCode"))
                    .peek(value -> {
                        if (!unique.add(value)) {
                            throw new IllegalArgumentException(
                                    "section fieldCodes must be unique");
                        }
                    }).toList();
        }
    }

    record PageLayout(
            Integer columns,
            Integer gap,
            LabelPosition labelPosition,
            Density density,
            Boolean stickyActions,
            Integer pageSize,
            Boolean searchEnabled,
            Boolean filterEnabled,
            List<PageSection> sections
    ) {
        public PageLayout {
            columns = columns == null ? 1 : columns;
            gap = gap == null ? 16 : gap;
            labelPosition = labelPosition == null
                    ? LabelPosition.TOP : labelPosition;
            density = density == null ? Density.DEFAULT : density;
            stickyActions = stickyActions == null || stickyActions;
            if (columns < 1 || columns > 24) {
                throw new IllegalArgumentException("columns must be within 1..24");
            }
            if (gap < 0 || gap > 64) {
                throw new IllegalArgumentException("gap must be within 0..64");
            }
            if (pageSize != null && (pageSize < 1 || pageSize > 200)) {
                throw new IllegalArgumentException("pageSize must be within 1..200");
            }
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (sections.isEmpty() || sections.size() > 20) {
                throw new IllegalArgumentException("sections must contain 1..20 rows");
            }
            var codes = new HashSet<String>();
            var titles = new HashSet<String>();
            var fields = new HashSet<String>();
            for (var section : sections) {
                Objects.requireNonNull(section, "section");
                if (!codes.add(section.code())
                        || !titles.add(section.title().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "section codes and titles must be unique");
                }
                for (var fieldCode : section.fieldCodes()) {
                    if (!fields.add(fieldCode)) {
                        throw new IllegalArgumentException(
                                "layout field codes must be globally unique");
                    }
                }
            }
            if (fields.size() > 200) {
                throw new IllegalArgumentException(
                        "layout may reference at most 200 fields");
            }
        }

        public PageLayout forPage(PageType type) {
            Objects.requireNonNull(type, "type");
            if (type == PageType.LIST) {
                return new PageLayout(
                        columns, gap, labelPosition, density, stickyActions,
                        pageSize == null ? 20 : pageSize,
                        searchEnabled == null || searchEnabled,
                        filterEnabled == null || filterEnabled,
                        sections);
            }
            if (pageSize != null || searchEnabled != null || filterEnabled != null) {
                throw new IllegalArgumentException(
                        "FORM and DETAIL reject list-only layout settings");
            }
            return this;
        }
    }

    record PageLayoutDraft(
            String pageCode,
            PageType pageType,
            PageLayout layout
    ) {
        public PageLayoutDraft {
            pageCode = code(pageCode, "pageCode");
            pageType = Objects.requireNonNull(pageType, "pageType");
            layout = Objects.requireNonNull(layout, "layout").forPage(pageType);
        }
    }

    record PrepareRequest(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            Operation operation,
            SelectionFieldDraft selectionField,
            PageLayoutDraft pageLayout,
            FilterScenarioDraft filterScenario,
            FieldPermissionStageDraft fieldPermissionStage,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            proposalId = token(proposalId, "proposalId", 128);
            sessionId = token(sessionId, "sessionId", 128);
            turnId = token(turnId, "turnId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, selectionField, pageLayout,
                    filterScenario, fieldPermissionStage);
            policyVersionId = positiveDecimal(policyVersionId, "policyVersionId");
            providerId = positiveDecimal(providerId, "providerId");
            nonNegative(providerVersion, "providerVersion");
            promptVersion = token(promptVersion, "promptVersion", 64);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }

        public PrepareRequest(
                String proposalId, String sessionId, String turnId,
                long accountId, long systemId, long tenantId, long memberId,
                long authorizationEpoch, Set<String> effectivePermissions,
                String moduleCode, Operation operation,
                SelectionFieldDraft selectionField, PageLayoutDraft pageLayout,
                String policyVersionId, String providerId, long providerVersion,
                String promptVersion, String requestId, String traceId) {
            this(proposalId, sessionId, turnId, accountId, systemId, tenantId,
                    memberId, authorizationEpoch, effectivePermissions,
                    moduleCode, operation, selectionField, pageLayout,
                    null, null, policyVersionId, providerId, providerVersion,
                    promptVersion, requestId, traceId);
        }
    }

    record PreparedArtifact(
            ArtifactPreview preview,
            Instant expiresAt,
            SealedCommand sealedCommand
    ) {
        public PreparedArtifact {
            preview = Objects.requireNonNull(preview, "preview");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        }
    }

    record ArtifactPreview(
            Operation operation,
            String configRootId,
            String moduleId,
            String moduleCode,
            long expectedDraftRevision,
            long nextDraftRevision,
            SelectionFieldPreview selectionField,
            PageLayoutPreview pageLayout,
            FilterScenarioPreview filterScenario,
            FieldPermissionStagePreview fieldPermissionStage
    ) {
        public ArtifactPreview {
            operation = Objects.requireNonNull(operation, "operation");
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "moduleCode");
            nonNegative(expectedDraftRevision, "expectedDraftRevision");
            if (nextDraftRevision != expectedDraftRevision + 1) {
                throw new IllegalArgumentException("nextDraftRevision is invalid");
            }
            matching(operation, selectionField, pageLayout,
                    filterScenario, fieldPermissionStage);
        }

        public ArtifactPreview(
                Operation operation, String configRootId, String moduleId,
                String moduleCode, long expectedDraftRevision,
                long nextDraftRevision, SelectionFieldPreview selectionField,
                PageLayoutPreview pageLayout) {
            this(operation, configRootId, moduleId, moduleCode,
                    expectedDraftRevision, nextDraftRevision, selectionField,
                    pageLayout, null, null);
        }
    }

    record SelectionFieldPreview(
            int sortOrder,
            SelectionFieldDraft draft
    ) {
        public SelectionFieldPreview {
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder is invalid");
            draft = Objects.requireNonNull(draft, "draft");
        }
    }

    record PageLayoutPreview(
            String pageId,
            String pageCode,
            PageType pageType,
            long pageVersion,
            PageLayout layout
    ) {
        public PageLayoutPreview {
            pageId = positiveDecimal(pageId, "pageId");
            pageCode = code(pageCode, "pageCode");
            pageType = Objects.requireNonNull(pageType, "pageType");
            nonNegative(pageVersion, "pageVersion");
            layout = Objects.requireNonNull(layout, "layout").forPage(pageType);
        }
    }

    record FilterScenarioPreview(
            String pageId,
            String pageCode,
            long pageVersion,
            boolean makeDefault,
            FilterScenario scenario,
            FilterScenarioState resolvedState,
            JsonNode resolvedLayout
    ) {
        public FilterScenarioPreview {
            pageId = positiveDecimal(pageId, "pageId");
            pageCode = code(pageCode, "pageCode");
            nonNegative(pageVersion, "pageVersion");
            scenario = Objects.requireNonNull(scenario, "scenario");
            resolvedState = Objects.requireNonNull(
                    resolvedState, "resolvedState");
            resolvedLayout = Objects.requireNonNull(
                    resolvedLayout, "resolvedLayout").deepCopy();
            var scenarioCode = scenario.code();
            if (!resolvedLayout.isObject()
                    || resolvedState.filterScenarios().stream().noneMatch(
                    value -> value.code().equals(scenarioCode))) {
                throw new IllegalArgumentException(
                        "resolved filter scenario state is invalid");
            }
        }

        @Override
        public JsonNode resolvedLayout() {
            return resolvedLayout.deepCopy();
        }
    }

    record FieldPermissionStagePreview(
            String fieldId,
            String fieldCode,
            String fieldName,
            long fieldVersion,
            boolean stageRead,
            boolean stageWrite,
            FieldPermissionMode expectedReadPermissionMode,
            FieldPermissionMode expectedWritePermissionMode,
            FieldPermissionMode readPermissionMode,
            FieldPermissionMode writePermissionMode,
            String readPermissionCode,
            String writePermissionCode
    ) {
        public FieldPermissionStagePreview {
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = text(fieldName, "fieldName", 128);
            nonNegative(fieldVersion, "fieldVersion");
            expectedReadPermissionMode = Objects.requireNonNull(
                    expectedReadPermissionMode, "expectedReadPermissionMode");
            expectedWritePermissionMode = Objects.requireNonNull(
                    expectedWritePermissionMode, "expectedWritePermissionMode");
            readPermissionMode = Objects.requireNonNull(
                    readPermissionMode, "readPermissionMode");
            writePermissionMode = Objects.requireNonNull(
                    writePermissionMode, "writePermissionMode");
            readPermissionCode = permissionCode(
                    readPermissionCode, fieldCode, "read");
            writePermissionCode = permissionCode(
                    writePermissionCode, fieldCode, "write");
            validateStageTransition(
                    stageRead, stageWrite,
                    expectedReadPermissionMode, expectedWritePermissionMode,
                    readPermissionMode, writePermissionMode);
        }
    }

    record SealedCommand(
            String ciphertext,
            String encryptionKeyVersion,
            String commandSha256
    ) {
        public SealedCommand {
            ciphertext = opaque(ciphertext, "ciphertext", 262_144);
            encryptionKeyVersion = token(
                    encryptionKeyVersion, "encryptionKeyVersion", 64);
            if (commandSha256 == null
                    || !commandSha256.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("commandSha256 is invalid");
            }
        }
    }

    record ExecuteRequest(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String configRootId,
            String moduleId,
            String moduleCode,
            long expectedDraftRevision,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            proposalId = token(proposalId, "proposalId", 128);
            sessionId = token(sessionId, "sessionId", 128);
            turnId = token(turnId, "turnId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "moduleCode");
            nonNegative(expectedDraftRevision, "expectedDraftRevision");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record ArtifactReadback(
            Operation operation,
            String configRootId,
            String moduleId,
            String moduleCode,
            long draftRevision,
            SelectionFieldReadback selectionField,
            PageLayoutReadback pageLayout,
            FilterScenarioReadback filterScenario,
            FieldPermissionStageReadback fieldPermissionStage
    ) {
        public ArtifactReadback {
            operation = Objects.requireNonNull(operation, "operation");
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "moduleCode");
            nonNegative(draftRevision, "draftRevision");
            matching(operation, selectionField, pageLayout,
                    filterScenario, fieldPermissionStage);
        }

        public ArtifactReadback(
                Operation operation, String configRootId, String moduleId,
                String moduleCode, long draftRevision,
                SelectionFieldReadback selectionField,
                PageLayoutReadback pageLayout) {
            this(operation, configRootId, moduleId, moduleCode, draftRevision,
                    selectionField, pageLayout, null, null);
        }
    }

    record SelectionFieldReadback(
            DictionaryView dictionary,
            List<OptionView> options,
            SelectionFieldView field
    ) {
        public SelectionFieldReadback {
            dictionary = Objects.requireNonNull(dictionary, "dictionary");
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (options.size() < 2 || options.size() > 50) {
                throw new IllegalArgumentException("options readback is invalid");
            }
            field = Objects.requireNonNull(field, "field");
            if (!dictionary.id().equals(field.dictionaryId())) {
                throw new IllegalArgumentException("field dictionary is invalid");
            }
        }
    }

    record DictionaryView(
            String dictionaryId,
            String dictionaryCode,
            String dictionaryName,
            long version
    ) {
        public DictionaryView {
            dictionaryId = positiveDecimal(dictionaryId, "dictionaryId");
            dictionaryCode = code(dictionaryCode, "dictionaryCode");
            dictionaryName = text(dictionaryName, "dictionaryName", 128);
            nonNegative(version, "version");
        }

        String id() { return dictionaryId; }
    }

    record OptionView(
            String optionId,
            String code,
            String label,
            String semanticKey,
            String color,
            boolean defaultOption,
            int sortOrder,
            long version
    ) {
        public OptionView {
            optionId = positiveDecimal(optionId, "optionId");
            code = itemCode(code, "option.code");
            label = text(label, "option.label", 128);
            semanticKey = optionalCode(semanticKey, "option.semanticKey", 32);
            color = optionalColor(color);
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder is invalid");
            nonNegative(version, "version");
        }
    }

    record SelectionFieldView(
            String fieldId,
            String fieldCode,
            String fieldName,
            SelectionType fieldType,
            boolean required,
            String dictionaryId,
            int sortOrder,
            Integer maxSelections,
            long version
    ) {
        public SelectionFieldView {
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = Objects.requireNonNull(fieldType, "fieldType");
            dictionaryId = positiveDecimal(dictionaryId, "dictionaryId");
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder is invalid");
            if (fieldType == SelectionType.RADIO && maxSelections != null) {
                throw new IllegalArgumentException("RADIO maxSelections is invalid");
            }
            if (maxSelections != null && (maxSelections < 1 || maxSelections > 50)) {
                throw new IllegalArgumentException("maxSelections is invalid");
            }
            nonNegative(version, "version");
        }
    }

    record PageLayoutReadback(
            String pageId,
            String pageCode,
            PageType pageType,
            long version,
            PageLayout layout
    ) {
        public PageLayoutReadback {
            pageId = positiveDecimal(pageId, "pageId");
            pageCode = code(pageCode, "pageCode");
            pageType = Objects.requireNonNull(pageType, "pageType");
            nonNegative(version, "version");
            layout = Objects.requireNonNull(layout, "layout").forPage(pageType);
        }
    }

    record FilterScenarioReadback(
            String pageId,
            String pageCode,
            long pageVersion,
            FilterScenarioState state
    ) {
        public FilterScenarioReadback {
            pageId = positiveDecimal(pageId, "pageId");
            pageCode = code(pageCode, "pageCode");
            nonNegative(pageVersion, "pageVersion");
            state = Objects.requireNonNull(state, "state");
        }
    }

    record FieldPermissionStageReadback(
            String fieldId,
            String fieldCode,
            String fieldName,
            long fieldVersion,
            FieldPermissionMode readPermissionMode,
            FieldPermissionMode writePermissionMode,
            String readPermissionCode,
            String writePermissionCode
    ) {
        public FieldPermissionStageReadback {
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = text(fieldName, "fieldName", 128);
            nonNegative(fieldVersion, "fieldVersion");
            readPermissionMode = Objects.requireNonNull(
                    readPermissionMode, "readPermissionMode");
            writePermissionMode = Objects.requireNonNull(
                    writePermissionMode, "writePermissionMode");
            readPermissionCode = permissionCode(
                    readPermissionCode, fieldCode, "read");
            writePermissionCode = permissionCode(
                    writePermissionCode, fieldCode, "write");
        }
    }

    private static void matching(
            Operation operation, Object selection, Object page,
            Object filterScenario, Object fieldPermissionStage) {
        var count = (selection == null ? 0 : 1) + (page == null ? 0 : 1)
                + (filterScenario == null ? 0 : 1)
                + (fieldPermissionStage == null ? 0 : 1);
        if (count != 1
                || (operation == Operation.CONFIG_SELECTION_FIELD_DRAFT)
                != (selection != null)
                || (operation == Operation.CONFIG_PAGE_LAYOUT_DRAFT)
                != (page != null)
                || (operation == Operation.CONFIG_FILTER_SCENARIO_DRAFT)
                != (filterScenario != null)
                || (operation == Operation.CONFIG_FIELD_PERMISSION_STAGE_DRAFT)
                != (fieldPermissionStage != null)) {
            throw new IllegalArgumentException(
                    "artifact payload does not match operation");
        }
    }

    private static void validateStageTransition(
            boolean stageRead,
            boolean stageWrite,
            FieldPermissionMode expectedRead,
            FieldPermissionMode expectedWrite,
            FieldPermissionMode resolvedRead,
            FieldPermissionMode resolvedWrite) {
        if (!stageRead && !stageWrite) {
            throw new IllegalArgumentException(
                    "at least one permission direction must be staged");
        }
        if (resolvedRead != (stageRead ? FieldPermissionMode.STAGED : expectedRead)
                || resolvedWrite != (stageWrite
                ? FieldPermissionMode.STAGED : expectedWrite)
                || stageRead && expectedRead == FieldPermissionMode.ENFORCED
                || stageWrite && expectedWrite == FieldPermissionMode.ENFORCED
                || !(stageRead && expectedRead == FieldPermissionMode.INHERIT
                || stageWrite && expectedWrite == FieldPermissionMode.INHERIT)) {
            throw new IllegalArgumentException(
                    "field permission stage transition is invalid");
        }
    }

    private static Set<String> permissions(Set<String> values) {
        values = Set.copyOf(Objects.requireNonNull(values, "effectivePermissions"));
        if (values.size() > 512) {
            throw new IllegalArgumentException("effectivePermissions is too large");
        }
        for (var value : values) token(value, "permission", 128);
        return values;
    }

    private static String code(String value, String name) {
        value = token(value, name, 64).toLowerCase(Locale.ROOT);
        if (!value.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String scenarioCode(String value, String name) {
        value = token(value, name, 64).toLowerCase(Locale.ROOT);
        if (!value.matches("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String permissionCode(
            String value, String fieldCode, String direction) {
        value = token(value, direction + "PermissionCode", 256);
        if (!value.matches("^module\\.[a-z][a-z0-9_]{1,63}\\.field\\."
                + java.util.regex.Pattern.quote(fieldCode)
                + "\\." + direction + "$")) {
            throw new IllegalArgumentException(
                    direction + "PermissionCode is invalid");
        }
        return value;
    }

    private static int jsonBytes(JsonNode value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String itemCode(String value, String name) {
        value = token(value, name, 64);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String optionalCode(String value, String name, int max) {
        if (value == null || value.isBlank()) return null;
        value = token(value, name, max);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.-]*$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String optionalColor(String value) {
        if (value == null || value.isBlank()) return null;
        value = value.strip();
        if (value.matches("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")) {
            return value.toUpperCase(Locale.ROOT);
        }
        if (!value.matches("^[A-Za-z][A-Za-z0-9_-]{0,31}$")) {
            throw new IllegalArgumentException("option.color is invalid");
        }
        return value;
    }

    private static String text(String value, String name, int max) {
        value = Normalizer.normalize(
                Objects.requireNonNull(value, name), Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > max
                || value.chars().anyMatch(character -> character < 0x20)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String token(String value, String name, int max) {
        value = Objects.requireNonNull(value, name).strip();
        if (value.isEmpty() || value.length() > max
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String opaque(String value, String name, int max) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String positiveDecimal(String value, String name) {
        value = token(value, name, 32);
        try {
            if (Long.parseLong(value) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " is invalid");
    }

    private static void nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " is invalid");
    }
}
