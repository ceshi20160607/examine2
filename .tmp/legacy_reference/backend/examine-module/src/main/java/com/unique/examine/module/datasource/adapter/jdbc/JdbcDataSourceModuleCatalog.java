package com.unique.examine.module.datasource.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository("jdbcDataSourceModuleCatalog")
public class JdbcDataSourceModuleCatalog
        implements DataSourceModuleCatalog {
    private static final Set<String> NUMERIC_OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY");
    private static final Map<String, Set<String>> OPERATORS = Map.ofEntries(
            Map.entry("TEXT", Set.of("EQ", "CONTAINS", "PREFIX", "EMPTY")),
            Map.entry("TEXTAREA", Set.of("CONTAINS", "EMPTY")),
            Map.entry("NUMBER", Set.of("EQ", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY")),
            Map.entry("DATE", Set.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY")),
            Map.entry("DATETIME", Set.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY")),
            Map.entry("RADIO", Set.of("EQ", "IN", "EMPTY")),
            Map.entry("MEMBER", Set.of("HAS_ANY", "EMPTY")),
            Map.entry("DEPARTMENT", Set.of("HAS_ANY", "EMPTY")),
            Map.entry("PERCENT", NUMERIC_OPERATORS),
            Map.entry("MONEY", NUMERIC_OPERATORS),
            Map.entry("DATE_RANGE", Set.of("OVERLAPS", "CONTAINS", "BEFORE", "AFTER", "EMPTY")),
            Map.entry("TIME", Set.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY")),
            Map.entry("TIME_RANGE", Set.of("OVERLAPS", "CONTAINS", "EMPTY")),
            Map.entry("MULTI_SELECT", Set.of("HAS_ANY", "HAS_ALL", "NOT_ANY", "EMPTY")),
            Map.entry("CASCADE", Set.of("CONTAINS_NODE", "LEAF_EQ", "EMPTY")),
            Map.entry("SWITCH", Set.of("EQ", "EMPTY")),
            Map.entry("RATING", NUMERIC_OPERATORS),
            Map.entry("PROGRESS", NUMERIC_OPERATORS),
            Map.entry("TAG", Set.of("HAS_ANY", "HAS_ALL", "EMPTY")),
            Map.entry("PHONE", Set.of("EQ", "EMPTY")),
            Map.entry("EMAIL", Set.of("EQ", "PREFIX", "EMPTY")),
            Map.entry("URL", Set.of("EQ", "PREFIX", "EMPTY")),
            Map.entry("IDENTITY", Set.of("EQ", "EMPTY")),
            Map.entry("ADDRESS", Set.of("EQ_REGION", "PREFIX", "EMPTY")),
            Map.entry("GEO", Set.of("WITHIN_BOX", "NEAR", "EMPTY")),
            Map.entry("BARCODE", Set.of("EQ", "PREFIX", "EMPTY")),
            Map.entry("RICH_TEXT", Set.of("CONTAINS", "EMPTY")),
            Map.entry("JSON", Set.of("DECLARED_PATH_EQ", "DECLARED_PATH_EXISTS")),
            Map.entry("SECRET", Set.of("EQ", "EMPTY")),
            Map.entry("STATUS", Set.of("EQ", "IN", "EMPTY")),
            Map.entry("RELATION", Set.of("HAS_ANY", "EMPTY")),
            Map.entry("SUBTABLE", Set.of("DECLARED_AGGREGATE")));
    private static final Set<String> SORTABLE_TYPES = Set.of(
            "TEXT", "TEXTAREA", "BARCODE", "STATUS", "NUMBER", "PERCENT",
            "MONEY", "RATING", "PROGRESS", "DATE", "DATETIME", "TIME",
            "SWITCH", "RADIO", "MEMBER", "DEPARTMENT");
    private static final Set<String> SORTABLE_INDEX_MODES = Set.of(
            "SORT", "UNIQUE", "STATISTIC");
    private static final Set<String> P4_C2_TYPES = Set.of(
            "PHONE", "EMAIL", "URL", "IDENTITY", "ADDRESS", "GEO",
            "BARCODE", "RICH_TEXT", "JSON", "SECRET", "STATUS");
    private static final Set<String> TEMPORAL_TYPES = Set.of("DATE", "DATETIME");
    private static final Set<String> DERIVED_TYPES = Set.of(
            "CALCULATED", "LOOKUP", "AGGREGATE", "AI_FILL");

    static final String CATALOG_SQL = """
            SELECT module_row.logical_module_id,module_row.module_code,
                   module_row.module_name,module_row.schema_version_id,
                   field_row.logical_field_id,field_row.field_code,
                   field_row.field_name,field_row.field_type,
                   field_row.field_scope,field_row.result_schema,
                   parent_field.field_code AS parent_field_code,
                   parent_field.field_name AS parent_field_name,
                   reference_target.field_type AS reference_target_type,
                   published_field.is_filterable,published_field.index_mode
              FROM un_module_config_root config_root
              JOIN un_plat_tenant tenant_row
                ON tenant_row.system_id=config_root.system_id
               AND tenant_row.id=?
               AND tenant_row.status='ACTIVE'
               AND tenant_row.deleted_at IS NULL
              JOIN un_module_runtime_schema_module module_row
                ON module_row.system_id=config_root.system_id
               AND module_row.schema_version_id=config_root.active_version_id
              JOIN un_module_config_version config_version
                ON config_version.system_id=config_root.system_id
               AND config_version.id=config_root.active_version_id
              LEFT JOIN un_module_runtime_schema_field field_row
                ON field_row.system_id=module_row.system_id
               AND field_row.schema_version_id=module_row.schema_version_id
               AND field_row.module_snapshot_id=module_row.module_snapshot_id
              LEFT JOIN un_module_runtime_schema_field reference_target
                ON reference_target.system_id=field_row.system_id
               AND reference_target.schema_version_id=field_row.schema_version_id
               AND reference_target.source_field_id=CAST(NULLIF(JSON_UNQUOTE(
                   JSON_EXTRACT(field_row.property_json,'$.targetFieldId')),'')
                   AS UNSIGNED)
               AND reference_target.field_scope='RECORD'
              LEFT JOIN un_module_runtime_schema_field parent_field
                ON parent_field.system_id=field_row.system_id
               AND parent_field.schema_version_id=field_row.schema_version_id
               AND parent_field.module_snapshot_id=field_row.module_snapshot_id
               AND parent_field.field_snapshot_id=field_row.parent_field_snapshot_id
               AND parent_field.field_scope='RECORD'
              LEFT JOIN JSON_TABLE(
                   config_version.snapshot_json,
                   '$.fields[*]' COLUMNS (
                       source_field_id BIGINT PATH '$.id',
                       is_filterable BOOLEAN PATH '$.is_filterable',
                       index_mode VARCHAR(16) PATH '$.index_mode'
                   )
              ) published_field
                ON published_field.source_field_id=field_row.source_field_id
             WHERE config_root.system_id=?
               AND config_root.active_version_id IS NOT NULL
            """;
    private static final String CATALOG_ORDER = """
             ORDER BY module_row.module_name,module_row.module_code,
                      module_row.logical_module_id,field_row.field_name,
                      field_row.field_code,field_row.field_snapshot_id
            """;
    static final String FIELD_PROPERTY_SQL = """
            SELECT field_row.property_json
              FROM un_module_config_root config_root
              JOIN un_plat_tenant tenant_row
                ON tenant_row.system_id=config_root.system_id
               AND tenant_row.id=?
               AND tenant_row.status='ACTIVE'
               AND tenant_row.deleted_at IS NULL
              JOIN un_module_runtime_schema_module module_row
                ON module_row.system_id=config_root.system_id
               AND module_row.schema_version_id=config_root.active_version_id
               AND module_row.logical_module_id=?
              JOIN un_module_runtime_schema_field field_row
                ON field_row.system_id=module_row.system_id
               AND field_row.schema_version_id=module_row.schema_version_id
               AND field_row.module_snapshot_id=module_row.module_snapshot_id
               AND field_row.field_code=?
               AND field_row.field_scope='RECORD'
             WHERE config_root.system_id=?
               AND config_root.active_version_id IS NOT NULL
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcDataSourceModuleCatalog(
            JdbcTemplate jdbc,
            ObjectMapper json
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public List<PublishedModule> publishedModules(
            long systemId,
            long tenantId
    ) {
        return modules(jdbc.query(
                CATALOG_SQL + CATALOG_ORDER,
                JdbcDataSourceModuleCatalog::row,
                tenantId, systemId));
    }

    @Override
    public Optional<PublishedModule> publishedModule(
            long systemId,
            long tenantId,
            long moduleId
    ) {
        var rows = modules(jdbc.query(
                CATALOG_SQL + " AND module_row.logical_module_id=?\n"
                        + CATALOG_ORDER,
                JdbcDataSourceModuleCatalog::row,
                tenantId, systemId, moduleId));
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Published module lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }

    @Override
    public CanonicalFilterValue canonicalizeFilterValue(
            long systemId,
            long tenantId,
            long moduleId,
            FieldCapability field,
            String operator,
            String canonicalValue
    ) {
        Objects.requireNonNull(field, "field");
        try {
            if (!field.available() || !field.operators().contains(operator)) {
                return CanonicalFilterValue.rejected(
                        "Field or operator is unavailable in the published schema");
            }
            if ("EMPTY".equals(operator)) {
                return canonicalValue == null
                        ? CanonicalFilterValue.accepted(null)
                        : CanonicalFilterValue.rejected(
                        "EMPTY filters cannot contain a value");
            }
            if (canonicalValue == null || canonicalValue.isBlank()) {
                return CanonicalFilterValue.rejected(
                        "Filter value is required");
            }
            var property = Set.of("MONEY", "JSON", "SUBTABLE")
                    .contains(field.queryType())
                    ? fieldProperty(
                    systemId, tenantId, moduleId, field.code())
                    : null;
            var normalized = normalize(
                    field.queryType(), operator, canonicalValue.strip(),
                    property);
            return CanonicalFilterValue.accepted(normalized);
        } catch (RuntimeException invalid) {
            return CanonicalFilterValue.rejected(
                    "Filter value is invalid for " + field.fieldName());
        }
    }

    private String normalize(
            String type,
            String operator,
            String value,
            JsonNode property
    ) {
        if (Set.of("IN", "HAS_ANY", "HAS_ALL", "NOT_ANY")
                .contains(operator)) {
            return collection(type, value);
        }
        if ("BETWEEN".equals(operator) || "OVERLAPS".equals(operator)) {
            return bounds(type, value, property);
        }
        return switch (type) {
            case "NUMBER", "PERCENT", "RATING", "PROGRESS" ->
                    decimal(text(value), type);
            case "MONEY" -> money(parse(value), property);
            case "SWITCH" -> bool(text(value));
            case "DATE", "DATE_RANGE" ->
                    write(LocalDate.parse(text(value)).toString());
            case "DATETIME" ->
                    write(LocalDateTime.parse(text(value)).toString());
            case "TIME", "TIME_RANGE" -> write(time(text(value)));
            case "RADIO", "MEMBER", "DEPARTMENT", "MULTI_SELECT",
                    "CASCADE", "STATUS", "RELATION" ->
                    write(positiveId(text(value)));
            case "ADDRESS" -> address(operator, value);
            case "GEO" -> geo(operator, parse(value));
            case "JSON" -> declaredJson(operator, parse(value), property);
            case "SUBTABLE" -> aggregate(parse(value), property);
            default -> write(normalizedText(text(value), textMaximum(type)));
        };
    }

    private String collection(String type, String value) {
        var node = parse(value);
        if (!node.isArray() || node.isEmpty() || node.size() > 100) {
            throw new IllegalArgumentException("Collection filter must contain 1..100 values");
        }
        var values = new ArrayList<String>();
        node.forEach(item -> {
            var normalized = switch (type) {
                case "RADIO", "MEMBER", "DEPARTMENT", "MULTI_SELECT",
                        "STATUS", "RELATION" ->
                        positiveId(nodeText(item));
                case "TAG" -> normalizedText(nodeText(item), 64);
                default -> normalizedText(nodeText(item), 512);
            };
            if (!values.contains(normalized)) {
                values.add(normalized);
            }
        });
        if (values.size() != node.size()) {
            throw new IllegalArgumentException("Collection filter contains duplicates");
        }
        values.sort(String::compareTo);
        return write(values);
    }

    private String bounds(String type, String value, JsonNode property) {
        var node = parse(value);
        if (!node.isArray() || node.size() != 2) {
            throw new IllegalArgumentException("Range filter requires two bounds");
        }
        if ("MONEY".equals(type)) {
            var first = moneyNode(node.get(0), property);
            var second = moneyNode(node.get(1), property);
            if (!first.currency().equals(second.currency())
                    || first.amount().compareTo(second.amount()) > 0) {
                throw new IllegalArgumentException("Money range is invalid");
            }
            return write(List.of(first.value(), second.value()));
        }
        if (Set.of("NUMBER", "PERCENT", "RATING", "PROGRESS").contains(type)) {
            var first = decimal(nodeText(node.get(0)), type);
            var second = decimal(nodeText(node.get(1)), type);
            if (new BigDecimal(first).compareTo(new BigDecimal(second)) > 0) {
                throw new IllegalArgumentException("Numeric range is invalid");
            }
            return "[" + first + "," + second + "]";
        }
        var first = switch (type) {
            case "DATE", "DATE_RANGE" -> LocalDate.parse(nodeText(node.get(0))).toString();
            case "DATETIME" -> LocalDateTime.parse(nodeText(node.get(0))).toString();
            case "TIME", "TIME_RANGE" -> time(nodeText(node.get(0)));
            default -> throw new IllegalArgumentException("Field has no range value");
        };
        var second = switch (type) {
            case "DATE", "DATE_RANGE" -> LocalDate.parse(nodeText(node.get(1))).toString();
            case "DATETIME" -> LocalDateTime.parse(nodeText(node.get(1))).toString();
            case "TIME", "TIME_RANGE" -> time(nodeText(node.get(1)));
            default -> throw new IllegalArgumentException("Field has no range value");
        };
        if (first.compareTo(second) > 0) {
            throw new IllegalArgumentException("Range lower bound exceeds upper bound");
        }
        return write(List.of(first, second));
    }

    private String money(JsonNode node, JsonNode property) {
        return write(moneyNode(node, property).value());
    }

    private MoneyValue moneyNode(JsonNode node, JsonNode property) {
        if (node == null || !node.isObject() || node.size() != 2
                || !node.path("amount").isTextual()
                || !node.path("currency").isTextual()) {
            throw new IllegalArgumentException("Money value is invalid");
        }
        var amountText = node.path("amount").textValue();
        if (!amountText.matches("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$")) {
            throw new IllegalArgumentException("Money amount is invalid");
        }
        var currency = node.path("currency").asText().toUpperCase(Locale.ROOT);
        if (!allowedCurrencies(property).contains(currency)) {
            throw new IllegalArgumentException("Money currency is unavailable");
        }
        var definition = Currency.getInstance(currency);
        var amount = new BigDecimal(amountText);
        if (definition.getDefaultFractionDigits() < 0
                || amount.precision() > 38
                || amount.scale() != definition.getDefaultFractionDigits()) {
            throw new IllegalArgumentException("Money amount scale is invalid");
        }
        var normalized = new LinkedHashMap<String, String>();
        normalized.put("amount", amount.toPlainString());
        normalized.put("currency", currency);
        return new MoneyValue(amount, currency, normalized);
    }

    private String address(String operator, String value) {
        if ("PREFIX".equals(operator)) {
            return write(normalizedText(text(value), 512));
        }
        var node = parse(value);
        if (node == null || !node.isObject() || node.isEmpty()
                || node.size() > 2 || !node.path("countryCode").isTextual()
                || node.properties().stream().anyMatch(entry ->
                !Set.of("countryCode", "regionCode").contains(entry.getKey()))) {
            throw new IllegalArgumentException("Address region is invalid");
        }
        var country = node.path("countryCode").textValue().toUpperCase();
        if (!country.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("Address country is invalid");
        }
        var normalized = new LinkedHashMap<String, String>();
        normalized.put("countryCode", country);
        if (node.has("regionCode")) {
            normalized.put("regionCode", normalizedText(
                    nodeText(node.path("regionCode")), 256));
        }
        return write(normalized);
    }

    private String geo(String operator, JsonNode node) {
        var keys = "WITHIN_BOX".equals(operator)
                ? List.of("south", "west", "north", "east")
                : List.of("lat", "lng", "radiusMeters");
        exactObject(node, Set.copyOf(keys));
        var normalized = new LinkedHashMap<String, BigDecimal>();
        for (var key : keys) {
            var minimum = Set.of("south", "north", "lat").contains(key)
                    ? -90 : Set.of("west", "east", "lng").contains(key)
                    ? -180 : 0;
            var maximum = Set.of("south", "north", "lat").contains(key)
                    ? 90 : Set.of("west", "east", "lng").contains(key)
                    ? 180 : 500_000;
            normalized.put(key, coordinate(node.path(key), minimum, maximum));
        }
        if ("WITHIN_BOX".equals(operator)
                && (normalized.get("south").compareTo(normalized.get("north")) > 0
                || normalized.get("west").compareTo(normalized.get("east")) > 0)
                || "NEAR".equals(operator)
                && normalized.get("radiusMeters").compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Geographic bounds are invalid");
        }
        return write(normalized);
    }

    private String declaredJson(
            String operator,
            JsonNode node,
            JsonNode property
    ) {
        if ("DECLARED_PATH_EXISTS".equals(operator)) {
            var path = declaredPath(property, positivePathId(node));
            return write(path.id());
        }
        exactObject(node, Set.of("pathSnapshotId", "value"));
        if (node.path("value").isNull() || node.path("value").isMissingNode()) {
            throw new IllegalArgumentException("Declared JSON value is invalid");
        }
        var path = declaredPath(property, positivePathId(
                node.path("pathSnapshotId")));
        var normalized = new LinkedHashMap<String, Object>();
        normalized.put("pathSnapshotId", path.id());
        normalized.put("value", declaredValue(path.type(), node.path("value")));
        return write(normalized);
    }

    private String aggregate(JsonNode node, JsonNode property) {
        exactObject(node, Set.of("aggregateId", "operator", "value"));
        var aggregateId = normalizedText(nodeText(node.path("aggregateId")), 64);
        var operator = nodeText(node.path("operator"));
        if (!NUMERIC_OPERATORS.contains(operator) || "EMPTY".equals(operator)) {
            throw new IllegalArgumentException("Aggregate operator is invalid");
        }
        if (property == null || !property.path("aggregates").isArray()
                || property.path("aggregates").findValuesAsText("id").stream()
                .noneMatch(aggregateId::equals)) {
            throw new IllegalArgumentException("Aggregate is unavailable");
        }
        var value = node.path("value");
        var normalizedValue = "BETWEEN".equals(operator)
                ? parse(bounds("NUMBER", write(value), null))
                : parse(decimal(nodeText(value), "NUMBER"));
        var normalized = new LinkedHashMap<String, Object>();
        normalized.put("aggregateId", aggregateId);
        normalized.put("operator", operator);
        normalized.put("value", normalizedValue);
        return write(normalized);
    }

    private static BigDecimal coordinate(
            JsonNode node,
            int minimum,
            int maximum
    ) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("Coordinate must be numeric");
        }
        var value = node.decimalValue().stripTrailingZeros();
        if (value.scale() > 10 || value.precision() > 20
                || value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException("Coordinate is outside its range");
        }
        return value;
    }

    private static void exactObject(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject() || node.size() != keys.size()) {
            throw new IllegalArgumentException("Compound filter value is invalid");
        }
        var supplied = new HashSet<String>();
        node.fieldNames().forEachRemaining(supplied::add);
        if (!supplied.equals(keys)) {
            throw new IllegalArgumentException("Compound filter properties are invalid");
        }
    }

    private static String positivePathId(JsonNode node) {
        if (node == null || !node.isValueNode()) {
            throw new IllegalArgumentException("Declared path id is invalid");
        }
        return positiveId(node.asText());
    }

    private static Set<String> allowedCurrencies(JsonNode property) {
        var currencies = new HashSet<String>();
        if (property != null && property.path("currencies").isArray()) {
            property.path("currencies").forEach(value ->
                    currencies.add(value.asText().toUpperCase(Locale.ROOT)));
        }
        for (var key : List.of("currency", "fixedCurrency")) {
            if (property != null && property.path(key).isTextual()) {
                currencies.add(property.path(key).textValue()
                        .toUpperCase(Locale.ROOT));
            }
        }
        if (currencies.isEmpty()) {
            currencies.add("CNY");
            currencies.add("USD");
        }
        return Set.copyOf(currencies);
    }

    private static DeclaredPath declaredPath(JsonNode property, String id) {
        if (property == null || !property.path("queryPaths").isArray()) {
            throw new IllegalArgumentException("Declared path is unavailable");
        }
        for (var path : property.path("queryPaths")) {
            if (id.equals(path.path("pathSnapshotId").asText())) {
                var type = path.path("type").asText();
                if (Set.of("STRING", "DECIMAL", "INTEGER", "BOOLEAN",
                        "DATE", "DATETIME").contains(type)) {
                    return new DeclaredPath(id, type);
                }
            }
        }
        throw new IllegalArgumentException("Declared path is unavailable");
    }

    private static Object declaredValue(String type, JsonNode value) {
        return switch (type) {
            case "STRING" -> normalizedText(nodeText(value), 512);
            case "DECIMAL" -> declaredDecimal(value, false);
            case "INTEGER" -> declaredDecimal(value, true);
            case "BOOLEAN" -> {
                if (!value.isBoolean()) {
                    throw new IllegalArgumentException(
                            "Declared boolean value is invalid");
                }
                yield value.booleanValue();
            }
            case "DATE" -> LocalDate.parse(nodeText(value)).toString();
            case "DATETIME" -> LocalDateTime.parse(nodeText(value)).toString();
            default -> throw new IllegalArgumentException(
                    "Declared path type is unavailable");
        };
    }

    private static BigDecimal declaredDecimal(JsonNode node, boolean integer) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("Declared number is invalid");
        }
        var value = node.decimalValue().stripTrailingZeros();
        if (value.precision() > 38 || value.scale() > 10
                || integer && value.scale() > 0) {
            throw new IllegalArgumentException("Declared number is invalid");
        }
        return value;
    }

    private JsonNode fieldProperty(
            long systemId,
            long tenantId,
            long moduleId,
            String fieldCode
    ) {
        var values = jdbc.query(
                FIELD_PROPERTY_SQL,
                (result, rowNumber) -> result.getString("property_json"),
                tenantId, moduleId, fieldCode, systemId);
        if (values.size() != 1) {
            throw new IllegalArgumentException(
                    "Published field property is unavailable");
        }
        return parse(values.getFirst());
    }

    private static String decimal(String value, String type) {
        var number = new BigDecimal(value).stripTrailingZeros();
        if (number.precision() > 38 || number.scale() > switch (type) {
            case "PERCENT" -> 4;
            case "PROGRESS" -> 2;
            case "RATING" -> 0;
            default -> 10;
        }) {
            throw new IllegalArgumentException("Decimal scale is invalid");
        }
        if ((Set.of("PERCENT", "PROGRESS").contains(type)
                && (number.compareTo(BigDecimal.ZERO) < 0
                || number.compareTo(new BigDecimal("100")) > 0))
                || ("RATING".equals(type)
                && (number.compareTo(BigDecimal.ONE) < 0
                || number.compareTo(new BigDecimal("5")) > 0))) {
            throw new IllegalArgumentException("Decimal range is invalid");
        }
        return number.toPlainString();
    }

    private static String bool(String value) {
        if (!"true".equalsIgnoreCase(value)
                && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Boolean value is invalid");
        }
        return value.toLowerCase();
    }

    private static String positiveId(String value) {
        if (!value.matches("^[1-9][0-9]{0,18}$")) {
            throw new IllegalArgumentException("Reference id is invalid");
        }
        return Long.toString(Long.parseLong(value));
    }

    private static String time(String value) {
        if (!value.matches("^[0-9]{2}:[0-9]{2}:[0-9]{2}$")) {
            throw new IllegalArgumentException("Time value is invalid");
        }
        LocalTime.parse(value);
        return value;
    }

    private static int textMaximum(String type) {
        return switch (type) {
            case "RICH_TEXT" -> 100;
            case "TAG" -> 64;
            default -> 512;
        };
    }

    private static String normalizedText(String value, int max) {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException("Text value is invalid");
        }
        return normalized;
    }

    private String text(String value) {
        try {
            var node = json.readTree(value);
            if (node != null && node.isValueNode()) {
                return node.asText();
            }
        } catch (JsonProcessingException ignored) {
            // Plain scalar values are the management API's compact form.
        }
        return value;
    }

    private static String nodeText(JsonNode node) {
        if (node == null || !node.isValueNode()) {
            throw new IllegalArgumentException("Scalar value is required");
        }
        return node.asText();
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Filter JSON is invalid", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Filter value cannot be serialized", exception);
        }
    }

    private static CatalogRow row(ResultSet result, int rowNumber)
            throws SQLException {
        return new CatalogRow(
                result.getLong("logical_module_id"),
                result.getString("module_code"),
                result.getString("module_name"),
                result.getString("schema_version_id"),
                result.getLong("logical_field_id"),
                result.getString("field_code"),
                result.getString("field_name"),
                result.getString("field_type"),
                result.getString("field_scope"),
                result.getString("result_schema"),
                result.getString("parent_field_code"),
                result.getString("parent_field_name"),
                result.getString("reference_target_type"),
                result.getBoolean("is_filterable"),
                result.getString("index_mode"));
    }

    static List<PublishedModule> modules(List<CatalogRow> rows) {
        var builders = new LinkedHashMap<Long, ModuleBuilder>();
        for (var row : rows) {
            var builder = builders.computeIfAbsent(row.moduleId(), ignored ->
                    new ModuleBuilder(
                            row.moduleId(), row.moduleCode(), row.moduleName(),
                            row.schemaVersionId(), new ArrayList<>()));
            if (row.fieldCode() != null) {
                var available = "RECORD".equals(row.fieldScope());
                var fieldCode = available ? row.fieldCode()
                        : nestedFieldCode(row);
                var fieldName = available ? row.fieldName()
                        : nestedFieldName(row);
                var queryType = available
                        ? queryType(row) : row.fieldType();
                var queryable = available && row.filterable()
                        && row.indexMode() != null
                        && !"NONE".equals(row.indexMode());
                var sortable = available
                        && SORTABLE_INDEX_MODES.contains(row.indexMode())
                        && SORTABLE_TYPES.contains(queryType)
                        && (!P4_C2_TYPES.contains(row.fieldType())
                        || Set.of("BARCODE", "STATUS").contains(
                        row.fieldType()));
                builder.fields().add(new FieldCapability(
                        row.logicalFieldId(), fieldCode, fieldName,
                        row.fieldType(), queryType,
                        queryable
                                ? OPERATORS.getOrDefault(queryType, Set.of())
                                : Set.of(),
                        sortable,
                        available && TEMPORAL_TYPES.contains(queryType),
                        available));
            }
        }
        return builders.values().stream()
                .map(builder -> new PublishedModule(
                        builder.moduleId(), builder.moduleCode(),
                        builder.moduleName(), builder.schemaVersionId(),
                        builder.fields()))
                .toList();
    }

    private static String nestedFieldCode(CatalogRow row) {
        if (row.parentFieldCode() == null || row.parentFieldCode().isBlank()) {
            throw new IllegalArgumentException(
                    "Nested data source field parent is invalid");
        }
        return row.parentFieldCode() + "." + row.fieldCode();
    }

    private static String nestedFieldName(CatalogRow row) {
        if (row.parentFieldName() == null || row.parentFieldName().isBlank()) {
            throw new IllegalArgumentException(
                    "Nested data source field parent is invalid");
        }
        return row.parentFieldName() + " / " + row.fieldName();
    }

    static String queryType(CatalogRow field) {
        if ("REFERENCE".equals(field.fieldType())) {
            return referenceQueryType(field.referenceTargetType());
        }
        if (DERIVED_TYPES.contains(field.fieldType())) {
            return derivedQueryType(field.resultSchema());
        }
        return switch (field.fieldType()) {
            case "AUTO_NUMBER" -> "TEXT";
            case "TENANT", "CREATED_BY", "UPDATED_BY" -> "MEMBER";
            case "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            default -> field.fieldType();
        };
    }

    private static String referenceQueryType(String targetType) {
        if (targetType == null) {
            throw new IllegalStateException(
                    "Published REFERENCE result type is unsupported");
        }
        return switch (targetType) {
            case "TEXT" -> "TEXT";
            case "NUMBER", "RATING" -> "NUMBER";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "SWITCH" -> "SWITCH";
            default -> throw new IllegalStateException(
                    "Published REFERENCE result type is unsupported");
        };
    }

    private static String derivedQueryType(String resultSchema) {
        if (resultSchema == null) {
            throw new IllegalStateException(
                    "Published derived result schema is unsupported");
        }
        return switch (resultSchema) {
            case "STRING" -> "TEXT";
            case "DECIMAL", "INTEGER" -> "NUMBER";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "BOOLEAN" -> "SWITCH";
            default -> throw new IllegalStateException(
                    "Published derived result schema is unsupported");
        };
    }

    record CatalogRow(
            long moduleId,
            String moduleCode,
            String moduleName,
            String schemaVersionId,
            long logicalFieldId,
            String fieldCode,
            String fieldName,
            String fieldType,
            String fieldScope,
            String resultSchema,
            String parentFieldCode,
            String parentFieldName,
            String referenceTargetType,
            boolean filterable,
            String indexMode
    ) {
        CatalogRow(
                long moduleId,
                String moduleCode,
                String moduleName,
                String schemaVersionId,
                String fieldCode,
                String fieldName,
                String fieldType,
                String fieldScope,
                boolean filterable,
                String indexMode
        ) {
            this(moduleId, moduleCode, moduleName, schemaVersionId,
                    legacyLogicalFieldId(fieldCode), fieldCode, fieldName,
                    fieldType, fieldScope, null, null, null, null, filterable,
                    indexMode);
        }

        private static long legacyLogicalFieldId(String code) {
            return code == null ? 1L
                    : Integer.toUnsignedLong(code.hashCode()) + 1L;
        }
    }

    private record ModuleBuilder(
            long moduleId,
            String moduleCode,
            String moduleName,
            String schemaVersionId,
            List<FieldCapability> fields
    ) {
    }

    private record MoneyValue(
            BigDecimal amount,
            String currency,
            Map<String, String> value
    ) {
    }

    private record DeclaredPath(String id, String type) {
    }
}
