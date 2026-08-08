package com.unique.examine.ai.plan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiPolicy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Strict parser for the bounded confirmation-gated configuration artifacts. */
@Component
public final class AiConfigurationArtifactPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 32 * 1024;
    private static final Set<String> SELECTION_FIELDS = Set.of(
            "operation", "moduleCode", "fieldCode", "fieldName", "fieldType",
            "required", "dictionaryCode", "dictionaryName", "options",
            "maxSelections", "confidence", "clarification");
    private static final Set<String> OPTION_FIELDS = Set.of(
            "code", "label", "semanticKey", "color", "default");
    private static final Set<String> PAGE_FIELDS = Set.of(
            "operation", "moduleCode", "pageCode", "pageType", "layout",
            "confidence", "clarification");
    private static final Set<String> LAYOUT_FIELDS = Set.of(
            "columns", "gap", "labelPosition", "density", "stickyActions",
            "pageSize", "searchEnabled", "filterEnabled", "sections");
    private static final Set<String> SECTION_FIELDS = Set.of(
            "code", "title", "fieldCodes");
    private static final Set<String> FILTER_SCENARIO_FIELDS = Set.of(
            "operation", "moduleCode", "pageCode", "scenario", "makeDefault",
            "confidence", "clarification");
    private static final Set<String> SCENARIO_FIELDS = Set.of(
            "code", "name", "filter", "sort");
    private static final Set<String> FIELD_PERMISSION_STAGE_FIELDS = Set.of(
            "operation", "moduleCode", "fieldCode", "stageRead", "stageWrite",
            "confidence", "clarification");
    private static final Set<String> PREDICATE_FIELDS = Set.of(
            "kind", "fieldCode", "operator", "value");
    private static final Set<String> REQUIRED_PREDICATE_FIELDS = Set.of(
            "kind", "fieldCode", "operator");
    private static final Set<String> GROUP_FIELDS = Set.of("kind", "children");
    private static final Set<String> SORT_FIELDS = Set.of(
            "fieldCode", "direction", "nulls", "currency");
    private static final Set<String> REQUIRED_SORT_FIELDS = Set.of(
            "fieldCode", "direction", "nulls");

    private final ObjectMapper strict;

    public AiConfigurationArtifactPlanParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public boolean isArtifactDraft(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) return false;
        try {
            var root = strict.readTree(providerOutput);
            var operation = root == null ? null : root.get("operation");
            return root != null && root.isObject() && operation != null
                    && operation.isTextual() && Operation.supported(
                    operation.textValue());
        } catch (JsonProcessingException failure) {
            return false;
        }
    }

    public Plan parse(String providerOutput, AiPolicy.Version policy) {
        Objects.requireNonNull(policy, "policy");
        var root = object(providerOutput);
        var operation = operation(root.get("operation"));
        if (!policy.allowedOperations().contains(operation.name())) {
            throw invalid("AI configuration artifact operation is not authorized by policy");
        }
        var expected = switch (operation) {
            case CONFIG_SELECTION_FIELD_DRAFT -> SELECTION_FIELDS;
            case CONFIG_PAGE_LAYOUT_DRAFT -> PAGE_FIELDS;
            case CONFIG_FILTER_SCENARIO_DRAFT -> FILTER_SCENARIO_FIELDS;
            case CONFIG_FIELD_PERMISSION_STAGE_DRAFT -> FIELD_PERMISSION_STAGE_FIELDS;
        };
        exact(root, expected, "plan");
        var confidence = confidence(root.get("confidence"));
        var clarification = nullableText(
                root.get("clarification"), "clarification", 500);
        final SelectionDraft selection;
        final PageDraft page;
        final FilterScenarioDraft filterScenario;
        final FieldPermissionStageDraft fieldPermissionStage;
        if (clarification != null) {
            requireNullExcept(root, Set.of(
                    "operation", "confidence", "clarification"));
            selection = null;
            page = null;
            filterScenario = null;
            fieldPermissionStage = null;
        } else if (operation == Operation.CONFIG_SELECTION_FIELD_DRAFT) {
            selection = selection(root, policy);
            page = null;
            filterScenario = null;
            fieldPermissionStage = null;
        } else if (operation == Operation.CONFIG_PAGE_LAYOUT_DRAFT) {
            selection = null;
            page = page(root, policy);
            filterScenario = null;
            fieldPermissionStage = null;
        } else if (operation == Operation.CONFIG_FILTER_SCENARIO_DRAFT) {
            selection = null;
            page = null;
            filterScenario = filterScenario(root, policy);
            fieldPermissionStage = null;
        } else {
            selection = null;
            page = null;
            filterScenario = null;
            fieldPermissionStage = fieldPermissionStage(root, policy);
        }
        try {
            var canonicalPlan = strict.writeValueAsString(canonical(root));
            String ownerJson = null;
            if (clarification == null) {
                var owner = JsonNodeFactory.instance.objectNode();
                for (var name : expected) {
                    if (!Set.of("operation", "confidence", "clarification")
                            .contains(name)) owner.set(name, canonical(root.get(name)));
                }
                ownerJson = strict.writeValueAsString(canonical(owner));
            }
            return new Plan(operation, selection, page, filterScenario,
                    fieldPermissionStage, confidence, clarification, ownerJson,
                    AiSupport.sha256(canonicalPlan));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot canonicalize AI configuration artifact plan", failure);
        }
    }

    private static SelectionDraft selection(
            JsonNode root, AiPolicy.Version policy) {
        var moduleCode = authorizedModule(root.get("moduleCode"), policy);
        var fieldCode = code(root.get("fieldCode"), "fieldCode");
        var fieldName = text(root.get("fieldName"), "fieldName", 128).strip();
        final SelectionType fieldType;
        try {
            fieldType = SelectionType.valueOf(text(
                    root.get("fieldType"), "fieldType", 32));
        } catch (RuntimeException failure) {
            throw invalid("AI selection fieldType must be RADIO or MULTI_SELECT");
        }
        var required = bool(root.get("required"), "required");
        var dictionaryCode = code(root.get("dictionaryCode"), "dictionaryCode");
        var dictionaryName = text(
                root.get("dictionaryName"), "dictionaryName", 128).strip();
        var optionsNode = root.get("options");
        if (optionsNode == null || !optionsNode.isArray()
                || optionsNode.size() < 2 || optionsNode.size() > 50) {
            throw invalid("AI selection options must contain 2..50 rows");
        }
        var codes = new HashSet<String>();
        var options = new ArrayList<Option>();
        var defaults = 0;
        for (var index = 0; index < optionsNode.size(); index++) {
            var option = optionsNode.get(index);
            exact(option, OPTION_FIELDS, "option");
            var code = code(option.get("code"), "options.code");
            if (!codes.add(code)) {
                throw invalid("AI selection option codes must be unique");
            }
            var label = text(option.get("label"), "options.label", 128).strip();
            var semanticKey = nullableToken(
                    option.get("semanticKey"), "options.semanticKey", 32);
            var color = nullableColor(option.get("color"));
            var defaultOption = bool(option.get("default"), "options.default");
            if (defaultOption && ++defaults > 1) {
                throw invalid("AI selection can have at most one default option");
            }
            options.add(new Option(
                    code, label, semanticKey, color, defaultOption, index * 10));
        }
        final Integer maxSelections;
        if (fieldType == SelectionType.RADIO) {
            if (!root.get("maxSelections").isNull()) {
                throw invalid("AI RADIO maxSelections must be null");
            }
            maxSelections = null;
        } else {
            maxSelections = root.get("maxSelections").isNull() ? null
                    : integer(root.get("maxSelections"),
                    "maxSelections", 1, options.size());
        }
        return new SelectionDraft(
                moduleCode, fieldCode, fieldName, fieldType, required,
                dictionaryCode, dictionaryName, options, maxSelections);
    }

    private static PageDraft page(JsonNode root, AiPolicy.Version policy) {
        var moduleCode = authorizedModule(root.get("moduleCode"), policy);
        var pageCode = code(root.get("pageCode"), "pageCode");
        final PageType pageType;
        try {
            pageType = PageType.valueOf(text(
                    root.get("pageType"), "pageType", 32));
        } catch (RuntimeException failure) {
            throw invalid("AI pageType must be LIST, FORM or DETAIL");
        }
        var value = root.get("layout");
        exact(value, LAYOUT_FIELDS, "layout");
        var columns = nullableInteger(value.get("columns"),
                "layout.columns", 1, 24, 1);
        var gap = nullableInteger(value.get("gap"),
                "layout.gap", 0, 64, 16);
        final LabelPosition labelPosition;
        final Density density;
        try {
            labelPosition = value.get("labelPosition").isNull()
                    ? LabelPosition.TOP : LabelPosition.valueOf(text(
                    value.get("labelPosition"), "layout.labelPosition", 16));
            density = value.get("density").isNull()
                    ? Density.DEFAULT : Density.valueOf(text(
                    value.get("density"), "layout.density", 16));
        } catch (RuntimeException failure) {
            throw invalid("AI page layout enum is unsupported");
        }
        var stickyActions = value.get("stickyActions").isNull() || bool(
                value.get("stickyActions"), "layout.stickyActions");
        final Integer pageSize;
        final Boolean searchEnabled;
        final Boolean filterEnabled;
        if (pageType == PageType.LIST) {
            pageSize = nullableInteger(value.get("pageSize"),
                    "layout.pageSize", 1, 200, 20);
            searchEnabled = value.get("searchEnabled").isNull()
                    || bool(value.get("searchEnabled"), "layout.searchEnabled");
            filterEnabled = value.get("filterEnabled").isNull()
                    || bool(value.get("filterEnabled"), "layout.filterEnabled");
        } else {
            requireNull(value, "pageSize", "searchEnabled", "filterEnabled");
            pageSize = null;
            searchEnabled = null;
            filterEnabled = null;
        }
        var sectionsNode = value.get("sections");
        if (sectionsNode == null || !sectionsNode.isArray()
                || sectionsNode.isEmpty() || sectionsNode.size() > 20) {
            throw invalid("AI page layout sections must contain 1..20 rows");
        }
        var sectionCodes = new HashSet<String>();
        var sectionTitles = new HashSet<String>();
        var fieldCodes = new HashSet<String>();
        var sections = new ArrayList<Section>();
        for (var index = 0; index < sectionsNode.size(); index++) {
            var section = sectionsNode.get(index);
            exact(section, SECTION_FIELDS, "section");
            var code = code(section.get("code"), "sections.code");
            if (!sectionCodes.add(code)) {
                throw invalid("AI page section codes must be unique");
            }
            var title = text(section.get("title"), "sections.title", 128).strip();
            if (!sectionTitles.add(title.toLowerCase(java.util.Locale.ROOT))) {
                throw invalid("AI page section titles must be unique");
            }
            var fields = section.get("fieldCodes");
            if (fields == null || !fields.isArray() || fields.isEmpty()
                    || fields.size() > 50) {
                throw invalid("AI page section fields must be bounded non-empty arrays");
            }
            var values = new ArrayList<String>();
            fields.forEach(field -> {
                var fieldCode = code(field, "sections.fieldCodes");
                if (!fieldCodes.add(fieldCode)) {
                    throw invalid("AI page fields must be unique across sections");
                }
                values.add(fieldCode);
            });
            sections.add(new Section(code, title, values, index * 10));
        }
        if (fieldCodes.size() > 200) {
            throw invalid("AI page layout can contain at most 200 fields");
        }
        return new PageDraft(moduleCode, pageCode, pageType,
                new Layout(columns, gap, labelPosition, density, stickyActions,
                        pageSize, searchEnabled, filterEnabled, sections));
    }

    private FilterScenarioDraft filterScenario(
            JsonNode root, AiPolicy.Version policy) {
        var moduleCode = authorizedModule(root.get("moduleCode"), policy);
        var pageCode = code(root.get("pageCode"), "pageCode");
        var scenario = root.get("scenario");
        exact(scenario, SCENARIO_FIELDS, "scenario");
        var scenarioCode = scenarioCode(scenario.get("code"));
        var name = Normalizer.normalize(
                text(scenario.get("name"), "scenario.name", 100),
                Normalizer.Form.NFKC).trim();
        if (name.isEmpty()) {
            throw invalid("AI configuration artifact scenario.name must not be blank");
        }
        var counters = new FilterCounters();
        JsonNode filter = null;
        if (!scenario.get("filter").isNull()) {
            validateFilter(scenario.get("filter"), 1, counters);
            filter = canonical(scenario.get("filter"));
        }
        var sort = scenario.get("sort");
        validateSort(sort);
        if (filter == null && sort.isEmpty()) {
            throw invalid("AI filter scenario needs a filter or sort");
        }
        return new FilterScenarioDraft(
                moduleCode, pageCode, scenarioCode, name, filter,
                canonical(sort), bool(root.get("makeDefault"), "makeDefault"));
    }

    private static FieldPermissionStageDraft fieldPermissionStage(
            JsonNode root, AiPolicy.Version policy) {
        var moduleCode = authorizedModule(root.get("moduleCode"), policy);
        var fieldCode = code(root.get("fieldCode"), "fieldCode");
        var stageRead = bool(root.get("stageRead"), "stageRead");
        var stageWrite = bool(root.get("stageWrite"), "stageWrite");
        if (!stageRead && !stageWrite) {
            throw invalid("AI field permission staging requires at least one direction");
        }
        return new FieldPermissionStageDraft(
                moduleCode, fieldCode, stageRead, stageWrite);
    }

    private static void validateFilter(
            JsonNode value, int depth, FilterCounters counters) {
        if (value == null || !value.isObject() || depth > 5) {
            throw invalid("AI filter scenario depth must be at most five");
        }
        var kind = text(value.get("kind"), "scenario.filter.kind", 16);
        if ("PREDICATE".equals(kind)) {
            exactRequired(value, PREDICATE_FIELDS, REQUIRED_PREDICATE_FIELDS,
                    "scenario predicate");
            counters.predicates++;
            if (counters.predicates > 20) {
                throw invalid("AI filter scenario may contain at most 20 predicates");
            }
            code(value.get("fieldCode"), "scenario.filter.fieldCode");
            var operator = text(
                    value.get("operator"), "scenario.filter.operator", 32);
            if (!operator.matches("^[A-Z][A-Z0-9_]{1,31}$")) {
                throw invalid("AI filter scenario operator is not canonical");
            }
            return;
        }
        if (!Set.of("AND", "OR", "NOT").contains(kind)) {
            throw invalid("AI filter scenario group kind is unsupported");
        }
        exact(value, GROUP_FIELDS, "scenario filter group");
        var children = value.get("children");
        var maximum = "NOT".equals(kind) ? 1 : 20;
        if (!children.isArray() || children.isEmpty() || children.size() > maximum
                || "NOT".equals(kind) && children.size() != 1) {
            throw invalid("AI filter scenario group has an invalid child count");
        }
        children.forEach(child -> validateFilter(child, depth + 1, counters));
    }

    private static void validateSort(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 3) {
            throw invalid("AI filter scenario sort must contain at most three rows");
        }
        var fields = new HashSet<String>();
        for (var item : value) {
            exactRequired(item, SORT_FIELDS, REQUIRED_SORT_FIELDS,
                    "scenario sort item");
            var fieldCode = code(
                    item.get("fieldCode"), "scenario.sort.fieldCode");
            if (!fields.add(fieldCode)) {
                throw invalid("AI filter scenario sort fields must be unique");
            }
            var direction = text(
                    item.get("direction"), "scenario.sort.direction", 8);
            var nulls = text(item.get("nulls"), "scenario.sort.nulls", 8);
            if (!Set.of("ASC", "DESC").contains(direction)
                    || !Set.of("FIRST", "LAST").contains(nulls)) {
                throw invalid("AI filter scenario sort enum is unsupported");
            }
            if (item.has("currency")) {
                var currency = text(
                        item.get("currency"), "scenario.sort.currency", 3);
                if (!currency.matches("^[A-Z]{3}$")) {
                    throw invalid("AI filter scenario currency is invalid");
                }
            }
        }
    }

    private JsonNode object(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI configuration artifact plan must be no larger than 32 KiB");
        }
        try {
            var value = strict.readTree(providerOutput);
            if (value == null || !value.isObject()) {
                throw invalid("AI configuration artifact plan must be a JSON object");
            }
            return value;
        } catch (JsonProcessingException failure) {
            throw invalid("AI configuration artifact JSON is malformed or duplicated");
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        if (node.isFloatingPointNumber()) {
            return JsonNodeFactory.instance.numberNode(
                    node.decimalValue().stripTrailingZeros());
        }
        return node.deepCopy();
    }

    private static Operation operation(JsonNode node) {
        try {
            return Operation.valueOf(text(node, "operation", 64));
        } catch (RuntimeException failure) {
            throw invalid("AI configuration artifact operation is unsupported");
        }
    }

    private static double confidence(JsonNode node) {
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())
                || node.doubleValue() < 0d || node.doubleValue() > 1d) {
            throw invalid("AI configuration artifact confidence must be within 0..1");
        }
        return node.doubleValue();
    }

    private static String authorizedModule(JsonNode node, AiPolicy.Version policy) {
        var value = code(node, "moduleCode");
        if (!policy.allowedModuleCodes().contains(value)) {
            throw invalid("AI configuration artifact module is not authorized by policy");
        }
        return value;
    }

    private static void exact(JsonNode node, Set<String> expected, String path) {
        if (node == null || !node.isObject() || !fields(node).equals(expected)) {
            throw invalid("AI configuration artifact " + path
                    + " contains unknown or missing fields");
        }
    }

    private static void exactRequired(
            JsonNode node, Set<String> allowed, Set<String> required, String path) {
        if (node == null || !node.isObject()) {
            throw invalid("AI configuration artifact " + path + " must be an object");
        }
        var actual = fields(node);
        if (!allowed.containsAll(actual) || !actual.containsAll(required)) {
            throw invalid("AI configuration artifact " + path
                    + " contains unknown or missing fields");
        }
    }

    private static Set<String> fields(JsonNode node) {
        var result = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    private static void requireNullExcept(JsonNode root, Set<String> allowed) {
        root.fields().forEachRemaining(entry -> {
            if (!allowed.contains(entry.getKey()) && !entry.getValue().isNull()) {
                throw invalid("AI clarification cannot contain executable artifact data");
            }
        });
    }

    private static void requireNull(JsonNode root, String... names) {
        for (var name : names) {
            if (!root.get(name).isNull()) {
                throw invalid("AI page type contains unsupported layout settings");
            }
        }
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path, 64);
        if (!value.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw invalid("AI configuration artifact " + path
                    + " is not a stable code");
        }
        return value;
    }

    private static String scenarioCode(JsonNode node) {
        var value = text(node, "scenario.code", 64);
        if (!value.matches("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")) {
            throw invalid("AI configuration artifact scenario.code is not canonical");
        }
        return value;
    }

    private static String text(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()
                || node.textValue().codePointCount(
                0, node.textValue().length()) > maximum) {
            throw invalid("AI configuration artifact " + path
                    + " must be bounded text");
        }
        return node.textValue();
    }

    private static String nullableText(JsonNode node, String path, int maximum) {
        return node == null || node.isNull() ? null : text(node, path, maximum).strip();
    }

    private static String nullableToken(JsonNode node, String path, int maximum) {
        if (node == null || node.isNull()) return null;
        var value = text(node, path, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) {
            throw invalid("AI configuration artifact " + path + " is invalid");
        }
        return value;
    }

    private static String nullableColor(JsonNode node) {
        if (node == null || node.isNull()) return null;
        var value = text(node, "options.color", 9);
        if (!value.matches("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")) {
            throw invalid("AI selection color must be #RRGGBB or #RRGGBBAA");
        }
        return value.toUpperCase();
    }

    private static boolean bool(JsonNode node, String path) {
        if (node == null || !node.isBoolean()) {
            throw invalid("AI configuration artifact " + path + " must be boolean");
        }
        return node.booleanValue();
    }

    private static int integer(
            JsonNode node, String path, int minimum, int maximum) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < minimum || node.intValue() > maximum) {
            throw invalid("AI configuration artifact " + path
                    + " is outside its bound");
        }
        return node.intValue();
    }

    private static int nullableInteger(
            JsonNode node, String path, int minimum, int maximum,
            int defaultValue) {
        return node == null || node.isNull() ? defaultValue
                : integer(node, path, minimum, maximum);
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_CONFIG_ARTIFACT_PLAN_INVALID", message);
    }

    public enum Operation {
        CONFIG_SELECTION_FIELD_DRAFT,
        CONFIG_PAGE_LAYOUT_DRAFT,
        CONFIG_FILTER_SCENARIO_DRAFT,
        CONFIG_FIELD_PERMISSION_STAGE_DRAFT;

        private static boolean supported(String value) {
            return "CONFIG_SELECTION_FIELD_DRAFT".equals(value)
                    || "CONFIG_PAGE_LAYOUT_DRAFT".equals(value)
                    || "CONFIG_FILTER_SCENARIO_DRAFT".equals(value)
                    || "CONFIG_FIELD_PERMISSION_STAGE_DRAFT".equals(value);
        }
    }

    public enum SelectionType { RADIO, MULTI_SELECT }
    public enum PageType { LIST, FORM, DETAIL }
    public enum LabelPosition { TOP, LEFT }
    public enum Density { COMPACT, DEFAULT }

    public record Option(
            String code,
            String label,
            String semanticKey,
            String color,
            boolean defaultOption,
            int sortOrder
    ) { }

    public record SelectionDraft(
            String moduleCode,
            String fieldCode,
            String fieldName,
            SelectionType fieldType,
            boolean required,
            String dictionaryCode,
            String dictionaryName,
            List<Option> options,
            Integer maxSelections
    ) {
        public SelectionDraft {
            options = List.copyOf(options);
        }
    }

    public record Section(
            String code,
            String title,
            List<String> fieldCodes,
            int sortOrder
    ) {
        public Section {
            fieldCodes = List.copyOf(fieldCodes);
        }
    }

    public record Layout(
            int columns,
            int gap,
            LabelPosition labelPosition,
            Density density,
            boolean stickyActions,
            Integer pageSize,
            Boolean searchEnabled,
            Boolean filterEnabled,
            List<Section> sections
    ) {
        public Layout {
            sections = List.copyOf(sections);
        }
    }

    public record PageDraft(
            String moduleCode,
            String pageCode,
            PageType pageType,
            Layout layout
    ) { }

    public record FilterScenarioDraft(
            String moduleCode,
            String pageCode,
            String scenarioCode,
            String scenarioName,
            JsonNode filter,
            JsonNode sort,
            boolean makeDefault
    ) {
        public FilterScenarioDraft {
            filter = filter == null ? null : filter.deepCopy();
            sort = Objects.requireNonNull(sort, "sort").deepCopy();
        }
    }

    public record FieldPermissionStageDraft(
            String moduleCode,
            String fieldCode,
            boolean stageRead,
            boolean stageWrite
    ) { }

    public record Plan(
            Operation operation,
            SelectionDraft selection,
            PageDraft page,
            FilterScenarioDraft filterScenario,
            FieldPermissionStageDraft fieldPermissionStage,
            double confidence,
            String clarification,
            String canonicalOwnerCommandJson,
            String planHash
    ) {
        public Plan {
            Objects.requireNonNull(operation, "operation");
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException(
                        "AI configuration artifact confidence is invalid");
            }
            if (clarification == null) {
                var payloads = (selection == null ? 0 : 1)
                        + (page == null ? 0 : 1)
                        + (filterScenario == null ? 0 : 1)
                        + (fieldPermissionStage == null ? 0 : 1);
                if (payloads != 1 || canonicalOwnerCommandJson == null) {
                    throw new IllegalArgumentException(
                            "AI configuration artifact executable plan is invalid");
                }
            } else if (selection != null || page != null || filterScenario != null
                    || fieldPermissionStage != null
                    || canonicalOwnerCommandJson != null) {
                throw new IllegalArgumentException(
                        "AI configuration artifact clarification is invalid");
            }
        }

        public boolean actionable() { return clarification == null; }

        public String moduleCode() {
            return selection != null ? selection.moduleCode()
                    : page != null ? page.moduleCode()
                    : filterScenario != null ? filterScenario.moduleCode()
                    : fieldPermissionStage == null ? null
                    : fieldPermissionStage.moduleCode();
        }
    }

    private static final class FilterCounters {
        private int predicates;
    }
}
