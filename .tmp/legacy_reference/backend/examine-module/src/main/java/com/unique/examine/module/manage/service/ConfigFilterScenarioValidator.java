package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes.PageType;
import com.unique.examine.module.runtime.query.RecordQueryCompiler;
import com.unique.examine.module.runtime.query.RecordQueryModels.FilterNode;
import com.unique.examine.module.runtime.query.RecordQueryModels.Group;
import com.unique.examine.module.runtime.query.RecordQueryModels.Predicate;
import com.unique.examine.module.runtime.query.RecordQueryModels.QueryField;
import com.unique.examine.module.runtime.query.RecordQueryParser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared configuration contract for LIST-page filter scenarios. */
final class ConfigFilterScenarioValidator {
    static final Set<String> LAYOUT_KEYS = Set.of("filterScenarios", "defaultFilterScenarioCode");

    private static final Set<String> SCENARIO_KEYS = Set.of("code", "name", "filter", "sort");
    private static final Set<String> SENSITIVE_TYPES = Set.of("IDENTITY", "SECRET");
    private static final Set<String> SYSTEM_SCALAR_TYPES = Set.of(
            "TENANT", "AUTO_NUMBER", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT");
    private static final Set<String> NATIVE_QUERY_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "DATETIME", "RADIO", "MEMBER", "DEPARTMENT",
            "PERCENT", "MONEY", "DATE_RANGE", "TIME", "TIME_RANGE", "MULTI_SELECT", "CASCADE",
            "SWITCH", "RATING", "PROGRESS", "TAG", "PHONE", "EMAIL", "URL", "ADDRESS", "GEO",
            "BARCODE", "RICH_TEXT", "JSON", "STATUS", "RELATION");

    private final ObjectMapper objectMapper;
    private final RecordQueryParser parser;
    private final RecordQueryCompiler compiler;

    ConfigFilterScenarioValidator(ObjectMapper objectMapper) {
        this(objectMapper, new RecordQueryParser(), new RecordQueryCompiler());
    }

    ConfigFilterScenarioValidator(
            ObjectMapper objectMapper,
            RecordQueryParser parser,
            RecordQueryCompiler compiler
    ) {
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.compiler = compiler;
    }

    ObjectNode canonicalize(PageType pageType, JsonNode layout) {
        var result = ((ObjectNode) layout).deepCopy();
        var declaresScenarios = result.has("filterScenarios")
                || result.has("defaultFilterScenarioCode");
        if (pageType != PageType.LIST) {
            if (declaresScenarios) {
                throw ConfigErrors.invalid(
                        "filterScenarios and defaultFilterScenarioCode are supported only by LIST pages");
            }
            return result;
        }

        var codes = new LinkedHashSet<String>();
        var names = new LinkedHashSet<String>();
        if (result.has("filterScenarios")) {
            var scenarios = result.get("filterScenarios");
            if (!scenarios.isArray() || scenarios.size() > 10) {
                throw ConfigErrors.invalid("layout.filterScenarios must be an array with at most 10 items");
            }
            var canonical = objectMapper.createArrayNode();
            for (var index = 0; index < scenarios.size(); index++) {
                var scenario = scenarios.get(index);
                requireExactScenario(scenario, index);
                var code = scenarioCode(scenario.get("code"), "layout.filterScenarios[" + index + "].code");
                var name = scenarioName(scenario.get("name"), "layout.filterScenarios[" + index + "].name");
                if (!codes.add(code)) {
                    throw ConfigErrors.invalid("layout.filterScenarios codes must be unique");
                }
                if (!names.add(name)) {
                    throw ConfigErrors.invalid("layout.filterScenarios names must be unique after trimming");
                }
                var query = parseScenario(scenario.get("filter"), scenario.get("sort"), index);
                if (query.filter() == null && query.sort().isEmpty()) {
                    throw ConfigErrors.invalid(
                            "Each filter scenario must contain a non-null filter or at least one sort item");
                }
                var canonicalQuery = canonicalQuery(query.canonicalJson());
                var item = objectMapper.createObjectNode();
                item.put("code", code);
                item.put("name", name);
                item.set("filter", canonicalQuery.get("filter"));
                item.set("sort", canonicalQuery.get("sort"));
                canonical.add(item);
            }
            result.set("filterScenarios", canonical);
        }

        if (result.has("defaultFilterScenarioCode")
                && !result.get("defaultFilterScenarioCode").isNull()) {
            var code = scenarioCode(result.get("defaultFilterScenarioCode"),
                    "layout.defaultFilterScenarioCode");
            if (!codes.contains(code)) {
                throw ConfigErrors.invalid(
                        "layout.defaultFilterScenarioCode must reference an existing filter scenario");
            }
            result.put("defaultFilterScenarioCode", code);
        }
        return result;
    }

    List<Finding> inspect(JsonNode snapshot) {
        var fieldsByModule = fields(snapshot.path("fields"));
        var findings = new ArrayList<Finding>();
        for (var page : snapshot.path("pages")) {
            if (!"ENABLED".equals(page.path("desired_status").asText())) {
                continue;
            }
            var layout = page.path("layout_json");
            var hasScenarioKeys = layout.has("filterScenarios")
                    || layout.has("defaultFilterScenarioCode");
            if (!hasScenarioKeys) {
                continue;
            }
            var pageId = positiveLong(page.path("id"));
            final PageType pageType;
            try {
                pageType = PageType.valueOf(page.path("page_type").asText());
            } catch (IllegalArgumentException exception) {
                findings.add(finding(pageId, null, "Page type is unavailable"));
                continue;
            }
            final ObjectNode canonical;
            try {
                canonical = canonicalize(pageType, layout);
            } catch (BusinessException exception) {
                findings.add(finding(pageId, null, exception.getMessage()));
                continue;
            }
            if (pageType != PageType.LIST) {
                continue;
            }
            var available = fieldsByModule.getOrDefault(page.path("module_id").asText(), Map.of());
            var queryFields = available.values().stream()
                    .filter(FieldContract::enabled)
                    .filter(FieldContract::supported)
                    .filter(field -> !field.sensitive())
                    .map(FieldContract::queryField)
                    .toList();
            for (var scenario : canonical.path("filterScenarios")) {
                var code = scenario.path("code").asText();
                try {
                    var query = parser.parseScenario(scenario.get("filter"), scenario.get("sort"));
                    requireAvailableFields(query.filter(), query.sort().stream()
                            .map(value -> value.fieldCode()).toList(), available);
                    compiler.compile(query, queryFields);
                } catch (BusinessException | IllegalArgumentException exception) {
                    findings.add(finding(pageId, code, exception.getMessage()));
                }
            }
        }
        return List.copyOf(findings);
    }

    boolean referencesField(JsonNode layout, String fieldCode) {
        for (var scenario : layout.path("filterScenarios")) {
            var query = parser.parseScenario(scenario.get("filter"), scenario.get("sort"));
            var codes = new LinkedHashSet<String>();
            collectFilterCodes(query.filter(), codes);
            query.sort().forEach(sort -> codes.add(sort.fieldCode()));
            if (codes.contains(fieldCode)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Map<String, FieldContract>> fields(JsonNode fields) {
        var result = new LinkedHashMap<String, Map<String, FieldContract>>();
        for (var field : fields) {
            var moduleId = field.path("module_id").asText();
            var code = field.path("field_code").asText();
            var sourceType = field.path("field_type").asText();
            var effectiveType = effectiveType(sourceType);
            var supported = NATIVE_QUERY_TYPES.contains(effectiveType)
                    && (NATIVE_QUERY_TYPES.contains(sourceType) || SYSTEM_SCALAR_TYPES.contains(sourceType));
            var enabled = "ENABLED".equals(field.path("desired_status").asText());
            var sensitive = SENSITIVE_TYPES.contains(sourceType);
            var indexMode = field.path("index_mode").asText("NONE");
            var indexActive = !"NONE".equals(indexMode) || "RELATION".equals(sourceType)
                    || SYSTEM_SCALAR_TYPES.contains(sourceType);
            var filterable = enabled && supported && ("RELATION".equals(sourceType)
                    || field.path("is_filterable").asBoolean(false) && indexActive);
            var sortable = enabled && supported
                    && Set.of("SORT", "UNIQUE", "STATISTIC").contains(indexMode);
            var schema = field.path("property_json").isObject()
                    ? field.path("property_json").deepCopy() : objectMapper.createObjectNode();
            var queryField = new QueryField(
                    positiveLong(field.path("id")), code, effectiveType, false,
                    filterable, sortable, false, schema);
            result.computeIfAbsent(moduleId, ignored -> new LinkedHashMap<>())
                    .put(code, new FieldContract(queryField, enabled, sensitive, supported));
        }
        return result;
    }

    private void requireAvailableFields(
            FilterNode filter,
            List<String> sortCodes,
            Map<String, FieldContract> fields
    ) {
        var codes = new LinkedHashSet<String>();
        collectFilterCodes(filter, codes);
        codes.addAll(sortCodes);
        for (var code : codes) {
            var field = fields.get(code);
            if (field == null) {
                throw semantic("Scenario references missing field " + code);
            }
            if (!field.enabled()) {
                throw semantic("Scenario references disabled field " + code);
            }
            if (field.sensitive()) {
                throw semantic("Scenario must not reference sensitive field " + code);
            }
            if (!field.supported()) {
                throw semantic("Scenario references unsupported field type for " + code);
            }
        }
    }

    private static void collectFilterCodes(FilterNode node, Set<String> result) {
        if (node == null) {
            return;
        }
        if (node instanceof Predicate predicate) {
            result.add(predicate.fieldCode());
            return;
        }
        for (var child : ((Group) node).children()) {
            collectFilterCodes(child, result);
        }
    }

    private com.unique.examine.module.runtime.query.RecordQueryModels.RecordQuery parseScenario(
            JsonNode filter,
            JsonNode sort,
            int index
    ) {
        try {
            return parser.parseScenario(filter, sort);
        } catch (BusinessException exception) {
            throw ConfigErrors.invalid(
                    "layout.filterScenarios[" + index + "]: " + exception.getMessage());
        }
    }

    private JsonNode canonicalQuery(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read canonical shared filter scenario", exception);
        }
    }

    private static void requireExactScenario(JsonNode scenario, int index) {
        if (scenario == null || !scenario.isObject() || scenario.size() != SCENARIO_KEYS.size()) {
            throw ConfigErrors.invalid(
                    "layout.filterScenarios[" + index + "] must contain exactly code,name,filter,sort");
        }
        var actual = new HashSet<String>();
        scenario.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(SCENARIO_KEYS)) {
            throw ConfigErrors.invalid(
                    "layout.filterScenarios[" + index + "] contains unknown or missing properties");
        }
    }

    private static String scenarioCode(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            throw ConfigErrors.invalid(path + " must be canonical lower snake case");
        }
        var code = node.textValue();
        if (code.length() > 64 || !code.matches("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")) {
            throw ConfigErrors.invalid(path + " must be canonical lower snake case within 1..64 characters");
        }
        return code;
    }

    private static String scenarioName(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            throw ConfigErrors.invalid(path + " must be text within 1..100 characters");
        }
        var name = Normalizer.normalize(node.textValue(), Normalizer.Form.NFKC).trim();
        if (name.isEmpty() || name.length() > 100) {
            throw ConfigErrors.invalid(path + " must be trimmed text within 1..100 characters");
        }
        return name;
    }

    private static String effectiveType(String sourceType) {
        return switch (sourceType) {
            case "AUTO_NUMBER" -> "TEXT";
            case "TENANT", "CREATED_BY", "UPDATED_BY" -> "MEMBER";
            case "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            default -> sourceType;
        };
    }

    private static long positiveLong(JsonNode node) {
        try {
            var value = Long.parseLong(node.asText());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Configuration snapshot id is invalid", exception);
        }
    }

    private static BusinessException semantic(String message) {
        return new BusinessException("FILTER_SCENARIO_INVALID", message,
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static Finding finding(long pageId, String scenarioCode, String message) {
        return new Finding(
                pageId,
                scenarioCode,
                "FILTER_SCENARIO_INVALID",
                scenarioCode == null ? "layout.filterScenarios"
                        : "layout.filterScenarios[" + scenarioCode + "]",
                message,
                "Use enabled non-sensitive fields with active native filter/sort indexes and valid values");
    }

    record Finding(
            long pageId,
            String scenarioCode,
            String code,
            String propertyPath,
            String message,
            String suggestedAction
    ) { }

    private record FieldContract(
            QueryField queryField,
            boolean enabled,
            boolean sensitive,
            boolean supported
    ) { }
}
