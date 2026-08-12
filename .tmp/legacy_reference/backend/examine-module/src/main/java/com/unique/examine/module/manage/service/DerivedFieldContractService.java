package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DerivedFieldContractService {
    public static final int EVALUATOR_VERSION = 1;
    private static final Set<String> DERIVED_TYPES = Set.of(
            "FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE", "AI_FILL"
    );
    private static final Set<String> AI_SOURCE_TYPES = Set.of(
            "TEXT", "TEXTAREA", "PHONE", "EMAIL", "URL", "NUMBER", "PERCENT", "MONEY",
            "DATE", "DATETIME", "RADIO", "RATING", "PROGRESS", "BARCODE", "RICH_TEXT",
            "STATUS", "SWITCH");
    private static final Set<ResultSchema> NUMERIC = Set.of(ResultSchema.DECIMAL, ResultSchema.INTEGER);
    private static final Set<ResultSchema> ORDERED = Set.of(
            ResultSchema.STRING, ResultSchema.DECIMAL, ResultSchema.INTEGER,
            ResultSchema.DATE, ResultSchema.DATETIME
    );

    private final ObjectMapper objectMapper;

    public DerivedFieldContractService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Analysis analyze(JsonNode snapshot) {
        var fields = new LinkedHashMap<Long, Field>();
        snapshot.path("fields").forEach(node -> {
            var id = positiveId(node.path("id"));
            var moduleId = positiveId(node.path("module_id"));
            if (id != null && moduleId != null && "ENABLED".equals(node.path("desired_status").asText("ENABLED"))) {
                fields.put(id, new Field(id, moduleId, positiveId(node.path("target_module_id")),
                        node.path("field_type").asText(), node.path("is_required").asBoolean(false),
                        node.path("is_readonly").asBoolean(false), node.path("is_hidden").asBoolean(false),
                        node.path("property_json")));
            }
        });

        var issues = new ArrayList<ContractIssue>();
        var candidates = new LinkedHashMap<Long, Candidate>();
        for (var field : fields.values()) {
            if (!DERIVED_TYPES.contains(field.type())) {
                continue;
            }
            var before = issues.size();
            if (field.required() || !field.readonly()) {
                issues.add(issue("SCHEMA_TYPE", field.id(), "field",
                        "Derived fields must be readonly and non-required",
                        "Set readonly=true and required=false"));
            }
            var declared = resultSchema(field.properties().path("resultSchema"));
            if (declared == null) {
                issues.add(issue("SCHEMA_TYPE", field.id(), "properties.resultSchema",
                        "Derived field resultSchema is missing or unsupported",
                        "Choose STRING, DECIMAL, INTEGER, DATE, DATETIME or BOOLEAN"));
                continue;
            }
            var dependencies = new ArrayList<Dependency>();
            ResultSchema inferred = switch (field.type()) {
                case "FORMULA", "CALCULATED" -> inferAst(field, field.properties().path("expressionAst"),
                        "properties.expressionAst", fields, dependencies, issues);
                case "SUMMARY" -> inspectSummary(field, fields, dependencies, issues);
                case "LOOKUP" -> inspectLookup(field, fields, dependencies, issues);
                case "AGGREGATE" -> inspectAggregate(field, fields, dependencies, issues);
                case "AI_FILL" -> inspectAiFill(field, declared, fields, dependencies, issues);
                default -> null;
            };
            if (inferred != null && inferred != declared) {
                issues.add(issue("SCHEMA_TYPE", field.id(), "properties.resultSchema",
                        "Declared resultSchema does not match the typed dependency declaration",
                        "Use resultSchema " + inferred));
            }
            if (issues.size() == before) {
                candidates.put(field.id(), new Candidate(field, declared,
                        List.copyOf(new LinkedHashSet<>(dependencies)), checksum(field.properties())));
            }
        }

        var graph = new LinkedHashMap<Long, Set<Long>>();
        candidates.forEach((fieldId, candidate) -> {
            var derivedDependencies = new LinkedHashSet<Long>();
            for (var dependency : candidate.dependencies()) {
                var dependencyId = parseId(dependency.fieldId());
                if (dependencyId != null && candidates.containsKey(dependencyId)) {
                    derivedDependencies.add(dependencyId);
                }
            }
            graph.put(fieldId, derivedDependencies);
        });
        var cycleFields = cycleFields(graph);
        for (var fieldId : cycleFields) {
            issues.add(issue("SCHEMA_CYCLE", fieldId, "properties",
                    "Derived dependency graph contains a direct or indirect cycle",
                    "Remove one dependency edge from the cycle"));
            candidates.remove(fieldId);
        }

        var ranks = new HashMap<Long, Integer>();
        var metadata = new LinkedHashMap<Long, Metadata>();
        candidates.forEach((fieldId, candidate) -> metadata.put(fieldId, new Metadata(
                fieldId, candidate.resultSchema().name(), EVALUATOR_VERSION, candidate.checksum(),
                rank(fieldId, graph, candidates.keySet(), ranks), candidate.dependencies()
        )));
        return new Analysis(Map.copyOf(metadata), List.copyOf(issues));
    }

    private ResultSchema inspectAiFill(
            Field field,
            ResultSchema declared,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        var properties = field.properties();
        var prompt = properties.path("promptTemplate");
        if (!prompt.isTextual() || prompt.textValue().isBlank()
                || prompt.textValue().length() > 4000) {
            issues.add(issue("AI_FILL_CONTRACT_INVALID", field.id(), "properties.promptTemplate",
                    "AI_FILL promptTemplate must contain 1..4000 characters",
                    "Provide a bounded immutable prompt template"));
        }
        if (!"SYSTEM_DEFAULT".equals(properties.path("modelPolicy").asText())) {
            issues.add(issue("AI_FILL_CONTRACT_INVALID", field.id(), "properties.modelPolicy",
                    "AI_FILL modelPolicy must be SYSTEM_DEFAULT",
                    "Use the governed system-default provider policy"));
        }
        var minimum = properties.path("minConfidence");
        if (!minimum.isNumber()
                || minimum.decimalValue().compareTo(new java.math.BigDecimal("0.50")) < 0
                || minimum.decimalValue().compareTo(java.math.BigDecimal.ONE) > 0) {
            issues.add(issue("AI_FILL_CONTRACT_INVALID", field.id(), "properties.minConfidence",
                    "AI_FILL minConfidence must be within 0.50..1.00",
                    "Choose a confidence threshold within the supported range"));
        }
        if (!Set.of("NEVER", "CONFIRM")
                .contains(properties.path("overwriteMode").asText())) {
            issues.add(issue("AI_FILL_CONTRACT_INVALID", field.id(), "properties.overwriteMode",
                    "AI_FILL overwriteMode must be NEVER or CONFIRM",
                    "Choose an explicit overwrite mode"));
        }
        var sourceNodes = properties.path("sourceFieldIds");
        var sourceIds = new LinkedHashSet<Long>();
        if (!sourceNodes.isArray() || sourceNodes.isEmpty() || sourceNodes.size() > 16) {
            issues.add(issue("AI_FILL_SOURCE_INVALID", field.id(), "properties.sourceFieldIds",
                    "AI_FILL requires 1..16 unique source fields",
                    "Select enabled readable scalar fields from this module"));
            return declared;
        }
        for (var node : sourceNodes) {
            var sourceId = positiveId(node);
            var source = sourceId == null ? null : fields.get(sourceId);
            if (sourceId == null || !sourceIds.add(sourceId) || source == null
                    || source.moduleId() != field.moduleId() || source.hidden()
                    || !AI_SOURCE_TYPES.contains(source.type())) {
                issues.add(issue("AI_FILL_SOURCE_INVALID", field.id(), "properties.sourceFieldIds",
                        "AI_FILL sources must be unique enabled readable non-secret scalar fields in the same module",
                        "Remove hidden, secret, system, composition, derived or cross-module sources"));
                continue;
            }
            var schema = scalarSchema(source, fields, new HashSet<>());
            dependencies.add(new Dependency(
                    "AI_SOURCE", Long.toString(sourceId), null, null, null,
                    schema == null ? null : schema.name()));
        }
        return declared;
    }

    private ResultSchema inspectSummary(
            Field field,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        var relation = relation(field, fields, dependencies, issues);
        var reduction = field.properties().path("reduction").asText();
        if (relation == null) {
            return null;
        }
        if ("COUNT".equals(reduction)) {
            return ResultSchema.INTEGER;
        }
        var target = targetField(field, relation, fields, dependencies, issues);
        var targetSchema = target == null ? null : scalarSchema(target, fields, new HashSet<>());
        if (targetSchema == null) {
            issues.add(issue("DEPENDENCY_UNREACHABLE", field.id(), "properties.targetFieldId",
                    "SUMMARY target field is missing or has no supported scalar result",
                    "Select an enabled scalar target field"));
            return null;
        }
        if (Set.of("SUM", "AVG").contains(reduction)) {
            if (!NUMERIC.contains(targetSchema)) {
                issues.add(issue("SCHEMA_TYPE", field.id(), "properties.reduction",
                        "SUMMARY SUM and AVG require a numeric target", "Select a numeric target field"));
                return null;
            }
            return ResultSchema.DECIMAL;
        }
        if (!Set.of("MIN", "MAX").contains(reduction) || !ORDERED.contains(targetSchema)) {
            issues.add(issue("SCHEMA_TYPE", field.id(), "properties.reduction",
                    "SUMMARY reduction is incompatible with its target field",
                    "Use COUNT, numeric SUM/AVG, or ordered MIN/MAX"));
            return null;
        }
        return targetSchema;
    }

    private ResultSchema inspectLookup(
            Field field,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        var relation = relation(field, fields, dependencies, issues);
        if (relation == null) {
            return null;
        }
        var target = targetField(field, relation, fields, dependencies, issues);
        var schema = target == null ? null : scalarSchema(target, fields, new HashSet<>());
        if (schema == null) {
            issues.add(issue("DEPENDENCY_UNREACHABLE", field.id(), "properties.targetFieldId",
                    "LOOKUP target field is missing or unsupported",
                    "Select an enabled scalar target field"));
        }
        return schema;
    }

    private ResultSchema inspectAggregate(
            Field field,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        var subtableId = positiveId(field.properties().path("subtableFieldId"));
        var aggregateId = field.properties().path("aggregateId").asText();
        var subtable = subtableId == null ? null : fields.get(subtableId);
        if (subtable == null || subtable.moduleId() != field.moduleId() || !"SUBTABLE".equals(subtable.type())) {
            issues.add(issue("DEPENDENCY_UNREACHABLE", field.id(), "properties.subtableFieldId",
                    "AGGREGATE requires a current-module SUBTABLE", "Select an enabled SUBTABLE field"));
            return null;
        }
        JsonNode declaration = null;
        for (var aggregate : subtable.properties().path("aggregates")) {
            if (aggregateId.equals(aggregate.path("id").asText())) {
                declaration = aggregate;
                break;
            }
        }
        if (declaration == null) {
            issues.add(issue("DEPENDENCY_UNREACHABLE", field.id(), "properties.aggregateId",
                    "AGGREGATE source declaration is missing", "Select a declared SUBTABLE aggregate"));
            return null;
        }
        dependencies.add(new Dependency("SUBTABLE_AGGREGATE", Long.toString(subtable.id()), null,
                null, aggregateId, ResultSchema.DECIMAL.name()));
        if (!"COUNT".equals(declaration.path("function").asText())) {
            var columnId = positiveId(declaration.path("columnFieldId"));
            var column = columnId == null ? null : fields.get(columnId);
            var schema = column == null ? null : scalarSchema(column, fields, new HashSet<>());
            if (column == null || subtable.targetModuleId() == null
                    || column.moduleId() != subtable.targetModuleId() || !NUMERIC.contains(schema)) {
                issues.add(issue("SCHEMA_TYPE", field.id(), "properties.aggregateId",
                        "AGGREGATE source must be COUNT or a numeric SUBTABLE aggregate",
                        "Repair the SUBTABLE aggregate declaration"));
                return null;
            }
            dependencies.add(new Dependency("SUBTABLE_COLUMN", Long.toString(column.id()), null,
                    null, aggregateId, schema.name()));
        }
        return ResultSchema.DECIMAL;
    }

    private Field relation(
            Field field,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        var relationId = positiveId(field.properties().path("relationFieldId"));
        var relation = relationId == null ? null : fields.get(relationId);
        if (relation == null || relation.moduleId() != field.moduleId() || !"RELATION".equals(relation.type())
                || relation.targetModuleId() == null) {
            issues.add(issue("DEPENDENCY_UNREACHABLE", field.id(), "properties.relationFieldId",
                    field.type() + " requires a current-module RELATION",
                    "Select an enabled relation with a reachable target module"));
            return null;
        }
        dependencies.add(new Dependency("RELATION", Long.toString(relation.id()),
                Long.toString(relation.id()), null, null, null));
        return relation;
    }

    private Field targetField(
            Field field,
            Field relation,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        var targetId = positiveId(field.properties().path("targetFieldId"));
        var target = targetId == null ? null : fields.get(targetId);
        if (target == null || target.moduleId() != relation.targetModuleId()) {
            issues.add(issue("DEPENDENCY_UNREACHABLE", field.id(), "properties.targetFieldId",
                    field.type() + " target does not belong to the relation target module",
                    "Select an enabled target field from the relation module"));
            return null;
        }
        var schema = scalarSchema(target, fields, new HashSet<>());
        dependencies.add(new Dependency("TARGET_FIELD", Long.toString(target.id()),
                Long.toString(relation.id()), Long.toString(target.id()), null,
                schema == null ? null : schema.name()));
        return target;
    }

    private ResultSchema inferAst(
            Field owner,
            JsonNode node,
            String path,
            Map<Long, Field> fields,
            List<Dependency> dependencies,
            List<ContractIssue> issues
    ) {
        if (node.has("fieldId")) {
            var dependencyId = positiveId(node.path("fieldId"));
            var dependency = dependencyId == null ? null : fields.get(dependencyId);
            if (dependency == null || dependency.moduleId() != owner.moduleId()) {
                issues.add(issue("DEPENDENCY_UNREACHABLE", owner.id(), path + ".fieldId",
                        "AST field reference is missing or outside the current module",
                        "Select an enabled current-module field"));
                return null;
            }
            var schema = scalarSchema(dependency, fields, new HashSet<>());
            if (schema == null) {
                issues.add(issue("SCHEMA_TYPE", owner.id(), path + ".fieldId",
                        "AST field reference has no supported scalar result",
                        "Select a field with a supported scalar resultSchema"));
                return null;
            }
            dependencies.add(new Dependency("FIELD", Long.toString(dependency.id()),
                    null, null, null, schema.name()));
            return schema;
        }
        if (node.has("literalType")) {
            return resultSchema(node.path("literalType"));
        }
        var args = node.path("args");
        var schemas = new ArrayList<ResultSchema>();
        for (var index = 0; index < args.size(); index++) {
            schemas.add(inferAst(owner, args.get(index), path + ".args[" + index + "]",
                    fields, dependencies, issues));
        }
        if (schemas.stream().anyMatch(java.util.Objects::isNull)) {
            return null;
        }
        var op = node.path("op").asText();
        ResultSchema result = switch (op) {
            case "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE" ->
                    schemas.stream().allMatch(NUMERIC::contains) ? ResultSchema.DECIMAL : null;
            case "CONCAT" -> schemas.stream().allMatch(value -> value == ResultSchema.STRING)
                    ? ResultSchema.STRING : null;
            case "EQ", "NE" -> schemas.get(0) == schemas.get(1) ? ResultSchema.BOOLEAN : null;
            case "GT", "GTE", "LT", "LTE" -> schemas.get(0) == schemas.get(1)
                    && ORDERED.contains(schemas.get(0)) ? ResultSchema.BOOLEAN : null;
            case "AND", "OR" -> schemas.stream().allMatch(value -> value == ResultSchema.BOOLEAN)
                    ? ResultSchema.BOOLEAN : null;
            case "NOT" -> schemas.getFirst() == ResultSchema.BOOLEAN ? ResultSchema.BOOLEAN : null;
            case "IF" -> schemas.getFirst() == ResultSchema.BOOLEAN && schemas.get(1) == schemas.get(2)
                    ? schemas.get(1) : null;
            case "ADD_DAYS" -> (schemas.getFirst() == ResultSchema.DATE
                    || schemas.getFirst() == ResultSchema.DATETIME) && schemas.get(1) == ResultSchema.INTEGER
                    ? schemas.getFirst() : null;
            case "DAYS_BETWEEN" -> (schemas.getFirst() == ResultSchema.DATE
                    || schemas.getFirst() == ResultSchema.DATETIME) && schemas.getFirst() == schemas.get(1)
                    ? ResultSchema.INTEGER : null;
            default -> null;
        };
        if (result == null) {
            issues.add(issue("SCHEMA_TYPE", owner.id(), path,
                    "AST operator arguments are not type compatible",
                    "Use operands compatible with operator " + op));
        }
        return result;
    }

    private ResultSchema scalarSchema(Field field, Map<Long, Field> fields, Set<Long> visiting) {
        if (!visiting.add(field.id())) {
            return null;
        }
        if (DERIVED_TYPES.contains(field.type())) {
            return resultSchema(field.properties().path("resultSchema"));
        }
        return switch (field.type()) {
            case "TEXT", "TEXTAREA", "PHONE", "EMAIL", "URL", "BARCODE", "RICH_TEXT", "RADIO", "STATUS" ->
                    ResultSchema.STRING;
            case "NUMBER", "PERCENT", "MONEY", "PROGRESS" -> ResultSchema.DECIMAL;
            case "RATING" -> ResultSchema.INTEGER;
            case "DATE" -> ResultSchema.DATE;
            case "DATETIME" -> ResultSchema.DATETIME;
            case "SWITCH" -> ResultSchema.BOOLEAN;
            case "REFERENCE" -> {
                var relation = fields.get(positiveId(field.properties().path("sourceFieldId")));
                var target = fields.get(positiveId(field.properties().path("targetFieldId")));
                yield relation != null && target != null && relation.targetModuleId() != null
                        && target.moduleId() == relation.targetModuleId()
                        ? scalarSchema(target, fields, visiting) : null;
            }
            default -> null;
        };
    }

    private Set<Long> cycleFields(Map<Long, Set<Long>> graph) {
        var state = new HashMap<Long, Integer>();
        var stack = new ArrayList<Long>();
        var result = new LinkedHashSet<Long>();
        for (var fieldId : graph.keySet()) {
            findCycles(fieldId, graph, state, stack, result);
        }
        return result;
    }

    private void findCycles(long fieldId, Map<Long, Set<Long>> graph, Map<Long, Integer> state,
                            List<Long> stack, Set<Long> cycles) {
        if (state.getOrDefault(fieldId, 0) == 2) return;
        if (state.getOrDefault(fieldId, 0) == 1) {
            var start = stack.indexOf(fieldId);
            cycles.addAll(stack.subList(start, stack.size()));
            return;
        }
        state.put(fieldId, 1);
        stack.add(fieldId);
        for (var dependency : graph.getOrDefault(fieldId, Set.of())) {
            findCycles(dependency, graph, state, stack, cycles);
        }
        stack.removeLast();
        state.put(fieldId, 2);
    }

    private int rank(long fieldId, Map<Long, Set<Long>> graph, Set<Long> valid, Map<Long, Integer> cache) {
        if (cache.containsKey(fieldId)) return cache.get(fieldId);
        var result = graph.getOrDefault(fieldId, Set.of()).stream()
                .filter(valid::contains)
                .mapToInt(dependency -> rank(dependency, graph, valid, cache) + 1)
                .max().orElse(0);
        cache.put(fieldId, result);
        return result;
    }

    private String checksum(JsonNode properties) {
        try {
            return ConfigMutationSupport.sha256(objectMapper.writeValueAsString(canonical(properties)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot canonicalize a derived field declaration", exception);
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted(Comparator.naturalOrder()).forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node.deepCopy();
    }

    private static ResultSchema resultSchema(JsonNode value) {
        try {
            return value.isTextual() ? ResultSchema.valueOf(value.asText()) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Long positiveId(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        try {
            var result = Long.parseLong(value.asText());
            return result > 0 ? result : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long parseId(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ContractIssue issue(String code, long fieldId, String path, String message, String action) {
        return new ContractIssue(code, fieldId, path, message, action);
    }

    private enum ResultSchema { STRING, DECIMAL, INTEGER, DATE, DATETIME, BOOLEAN }

    private record Field(long id, long moduleId, Long targetModuleId, String type, boolean required,
                         boolean readonly, boolean hidden, JsonNode properties) { }

    private record Candidate(Field field, ResultSchema resultSchema, List<Dependency> dependencies,
                             String checksum) { }

    public record Dependency(String kind, String fieldId, String relationFieldId, String targetFieldId,
                             String aggregateId, String resultSchema) { }

    public record Metadata(long fieldId, String resultSchema, int evaluatorVersion, String expressionChecksum,
                           int topologicalRank, List<Dependency> dependencies) { }

    public record ContractIssue(String code, long fieldId, String propertyPath, String message,
                                String suggestedAction) { }

    public record Analysis(Map<Long, Metadata> metadataByField, List<ContractIssue> issues) { }
}
