package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.query.RecordQueryModels.CompiledQuery;
import com.unique.examine.module.runtime.query.RecordQueryModels.EffectiveSort;
import com.unique.examine.module.runtime.query.RecordQueryModels.FilterNode;
import com.unique.examine.module.runtime.query.RecordQueryModels.Group;
import com.unique.examine.module.runtime.query.RecordQueryModels.Predicate;
import com.unique.examine.module.runtime.query.RecordQueryModels.QueryField;
import com.unique.examine.module.runtime.query.RecordQueryModels.RecordQuery;
import com.unique.examine.module.runtime.query.RecordQueryModels.SensitiveHashProvider;
import com.unique.examine.module.runtime.query.RecordQueryModels.SortItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RecordQueryCompiler {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> NUMERIC_OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY");
    private static final Map<String, Set<String>> OPERATORS = Map.ofEntries(
            Map.entry("TEXT", Set.of("EQ", "CONTAINS", "PREFIX", "EMPTY")),
            Map.entry("RECORD_NO", Set.of("EQ", "CONTAINS", "PREFIX")),
            Map.entry("TEXTAREA", Set.of("CONTAINS", "EMPTY")),
            Map.entry("NUMBER", Set.of("EQ", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY")),
            Map.entry("DATE", Set.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY")),
            Map.entry("DATETIME", Set.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY")),
            Map.entry("RADIO", Set.of("EQ", "IN", "EMPTY")),
            Map.entry("MEMBER", Set.of("HAS_ANY", "EMPTY")),
            Map.entry("DEPARTMENT", Set.of("HAS_ANY", "IN_TREE", "EMPTY")),
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
            Map.entry("SUBTABLE", Set.of("DECLARED_AGGREGATE"))
    );

    public CompiledQuery compile(RecordQuery query, List<QueryField> fields) {
        return compile(query, fields, null);
    }

    public CompiledQuery compile(
            RecordQuery query,
            List<QueryField> fields,
            SensitiveHashProvider sensitiveHashes
    ) {
        var byCode = new LinkedHashMap<String, QueryField>();
        fields.forEach(field -> byCode.put(field.code(), field));
        for (var column : query.columns()) {
            requireField(byCode, column, "columns");
        }
        var arguments = new ArrayList<Object>();
        var predicates = new ArrayList<String>();
        if (query.q() != null) {
            var searchableIds = fields.stream().filter(QueryField::searchable).map(QueryField::id).toList();
            if (searchableIds.isEmpty()) {
                throw unavailable("q", "No readable searchable field is active");
            }
            final List<String> tokens;
            try {
                tokens = RecordSearchTokenizer.queryTokens(query.q());
            } catch (IllegalArgumentException exception) {
                throw invalid(exception.getMessage());
            }
            var fieldIds = searchableIds.stream().map(String::valueOf).toList();
            for (var token : tokens) {
                predicates.add("EXISTS (SELECT 1 FROM un_module_record_search s WHERE "
                        + "s.system_id=r.system_id AND s.tenant_id=r.tenant_id AND s.record_id=r.record_id "
                        + "AND s.schema_version_id=r.schema_version_id AND s.module_snapshot_id=r.module_snapshot_id "
                        + "AND s.logical_module_id=r.logical_module_id AND s.record_status=r.status "
                        + "AND s.index_generation_id=1 AND s.logical_field_id IN ("
                        + String.join(",", fieldIds) + ") AND s.token_hash=?)");
                arguments.add(RecordSearchTokenizer.hash(token));
            }
        }
        if (query.filter() != null) {
            predicates.add(filter(query.filter(), byCode, arguments, "filter", sensitiveHashes));
        }
        var order = new ArrayList<String>();
        var orderArguments = new ArrayList<Object>();
        var effectiveSorts = new ArrayList<EffectiveSort>();
        for (var sort : query.sort()) {
            var field = requireField(byCode, sort.fieldCode(), "sort");
            if (!field.sortable()) {
                throw unavailable("sort." + sort.fieldCode(), "Field is not sortable in the active index plan");
            }
            var expression = sortExpression(field, sort);
            effectiveSorts.add(new EffectiveSort(
                    expression.sql(),
                    sort.direction(),
                    sort.nulls(),
                    sortValueType(field),
                    expression.arguments()));
            order.add(expression.sql() + " IS NULL " + ("FIRST".equals(sort.nulls()) ? "DESC" : "ASC"));
            orderArguments.addAll(expression.arguments());
            order.add(expression.sql() + " " + sort.direction());
            orderArguments.addAll(expression.arguments());
        }
        if (order.isEmpty()) {
            order.add("r.updated_at DESC");
            effectiveSorts.add(new EffectiveSort(
                    "r.updated_at",
                    "DESC",
                    "LAST",
                    "DATETIME",
                    List.of()));
        }
        order.add("r.record_id ASC");
        return new CompiledQuery(predicates.isEmpty() ? "1=1" : String.join(" AND ", predicates),
                arguments, String.join(",", order), orderArguments, effectiveSorts);
    }

    public boolean supportsFilter(QueryField field, String operator) {
        return field != null && field.filterable()
                && OPERATORS.getOrDefault(field.type(), Set.of()).contains(operator);
    }

    public List<GeoCandidateWindow> geoCandidateWindows(RecordQuery query, List<QueryField> fields) {
        if (query.filter() == null) {
            return List.of();
        }
        var byCode = new LinkedHashMap<String, QueryField>();
        fields.forEach(field -> byCode.put(field.code(), field));
        var result = new ArrayList<GeoCandidateWindow>();
        collectGeoWindows(query.filter(), byCode, result, "filter");
        return List.copyOf(result);
    }

    private void collectGeoWindows(
            FilterNode node,
            Map<String, QueryField> fields,
            List<GeoCandidateWindow> result,
            String path
    ) {
        if (node instanceof Group group) {
            for (var index = 0; index < group.children().size(); index++) {
                collectGeoWindows(group.children().get(index), fields, result,
                        path + ".children[" + index + "]");
            }
            return;
        }
        var predicate = (Predicate) node;
        var field = fields.get(predicate.fieldCode());
        if (field != null && "GEO".equals(field.type())
                && Set.of("WITHIN_BOX", "NEAR").contains(predicate.operator())) {
            result.add(geoWindow(field, predicate.operator(), predicate.value(), path + ".value"));
        }
    }

    private String filter(
            FilterNode node,
            Map<String, QueryField> fields,
            List<Object> arguments,
            String path,
            SensitiveHashProvider sensitiveHashes
    ) {
        if (node instanceof Group group) {
            var children = new ArrayList<String>();
            for (var index = 0; index < group.children().size(); index++) {
                children.add(filter(group.children().get(index), fields, arguments,
                        path + ".children[" + index + "]", sensitiveHashes));
            }
            if ("NOT".equals(group.kind())) {
                return "NOT (" + children.getFirst() + ")";
            }
            return "(" + String.join(" " + group.kind() + " ", children) + ")";
        }
        var predicate = (Predicate) node;
        var field = requireField(fields, predicate.fieldCode(), path + ".fieldCode");
        if (!field.filterable()) {
            throw unavailable(path, "Field is not filterable in the active index plan");
        }
        if (!OPERATORS.getOrDefault(field.type(), Set.of()).contains(predicate.operator())) {
            throw unavailable(path + ".operator", "Operator is unavailable for the field type");
        }
        if (Set.of("IDENTITY", "SECRET").contains(field.type()) && !field.sensitiveQueryable()) {
            throw unavailable(path, "Sensitive query permission is required");
        }
        if ("RECORD_NO".equals(field.type())) {
            return stringCondition("r.record_no", predicate.operator(), predicate.value(), arguments,
                    path + ".value");
        }
        if ("RELATION".equals(field.type())) {
            return relationPredicate(field, predicate.operator(), predicate.value(), arguments, path + ".value");
        }
        if ("SUBTABLE".equals(field.type())) {
            return subtableAggregatePredicate(field, predicate.value(), arguments, path + ".value");
        }
        var base = indexBase("i", field);
        if ("DEPARTMENT".equals(field.type()) && "IN_TREE".equals(predicate.operator())) {
            arguments.add(reference(predicate.value(), path + ".value"));
            return "EXISTS (" + base + " AND i.reference_value IN (SELECT dc.descendant_id "
                    + "FROM un_plat_department_closure dc WHERE dc.scope_type='SYSTEM' "
                    + "AND dc.scope_key=r.system_id AND dc.tenant_key=r.tenant_id "
                    + "AND dc.ancestor_id=?))";
        }
        if ("EMPTY".equals(predicate.operator())) {
            var empty = booleanValue(predicate.value(), path + ".value");
            return (empty ? "NOT " : "") + "EXISTS (" + base + ")";
        }
        return switch (field.type()) {
            case "DATE_RANGE" -> rangePredicate(field, predicate.operator(), predicate.value(), arguments,
                    path + ".value", false);
            case "TIME_RANGE" -> rangePredicate(field, predicate.operator(), predicate.value(), arguments,
                    path + ".value", true);
            case "MULTI_SELECT", "TAG" -> membershipPredicate(field, predicate.operator(), predicate.value(),
                    arguments, path + ".value");
            case "CASCADE" -> cascadePredicate(field, predicate.operator(), predicate.value(), arguments,
                    path + ".value");
            case "IDENTITY", "SECRET" -> sensitivePredicate(field, predicate.value(), arguments,
                    path + ".value", sensitiveHashes);
            case "ADDRESS" -> addressPredicate(field, predicate.operator(), predicate.value(), arguments,
                    path + ".value");
            case "GEO" -> geoPredicate(field, predicate.operator(), predicate.value(), arguments,
                    path + ".value");
            case "RICH_TEXT" -> richTextPredicate(field, predicate.value(), arguments, path + ".value");
            case "JSON" -> jsonPredicate(field, predicate.operator(), predicate.value(), arguments,
                    path + ".value");
            default -> "EXISTS (" + base + " AND "
                    + condition(field, predicate.operator(), predicate.value(), arguments, path + ".value") + ")";
        };
    }

    private String relationPredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var base = "SELECT 1 FROM un_module_record_relation rr WHERE rr.system_id=r.system_id "
                + "AND rr.tenant_id=r.tenant_id AND rr.source_record_id=r.record_id "
                + "AND rr.source_schema_version_id=r.schema_version_id "
                + "AND rr.source_module_snapshot_id=r.module_snapshot_id "
                + "AND rr.source_logical_module_id=r.logical_module_id "
                + "AND rr.source_logical_field_id=" + field.id();
        if ("EMPTY".equals(operator)) {
            var empty = booleanValue(value, path);
            return (empty ? "NOT " : "") + "EXISTS (" + base + ")";
        }
        var targets = referenceStrings(value, path);
        arguments.addAll(targets);
        return "EXISTS (" + base + " AND rr.target_record_id IN (" + placeholders(targets.size()) + "))";
    }

    private String subtableAggregatePredicate(
            QueryField field,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        requireExactObject(value, Set.of("aggregateId", "operator", "value"), path);
        var aggregateId = stringValue(value.path("aggregateId"), path + ".aggregateId", 64);
        var operator = stringValue(value.path("operator"), path + ".operator", 16);
        if (!NUMERIC_OPERATORS.contains(operator) || "EMPTY".equals(operator)) {
            throw unavailable(path + ".operator", "Declared aggregate operator is unavailable");
        }
        JsonNode declaration = null;
        for (var candidate : field.schema().path("aggregates")) {
            if (aggregateId.equals(candidate.path("id").asText())) {
                declaration = candidate;
                break;
            }
        }
        if (declaration == null) {
            throw unavailable(path + ".aggregateId", "Declared aggregate is unavailable");
        }
        var function = declaration.path("function").asText();
        final String expression;
        if ("COUNT".equals(function)) {
            expression = "(SELECT COUNT(*) FROM un_module_sub_record sr WHERE sr.system_id=r.system_id "
                    + "AND sr.tenant_id=r.tenant_id AND sr.parent_record_id=r.record_id "
                    + "AND sr.schema_version_id=r.schema_version_id AND sr.module_snapshot_id=r.module_snapshot_id "
                    + "AND sr.parent_field_snapshot_id=" + field.id() + " AND sr.status='ACTIVE')";
        } else {
            var columnId = declaration.path("columnFieldId").asText();
            if (!columnId.matches("^[1-9][0-9]{0,18}$")) {
                throw unavailable(path + ".aggregateId", "Declared aggregate column is unavailable");
            }
            expression = "(SELECT " + function + "(sv.decimal_value) FROM un_module_sub_record sr "
                    + "JOIN un_module_sub_value sv ON sv.system_id=sr.system_id AND sv.tenant_id=sr.tenant_id "
                    + "AND sv.parent_record_id=sr.parent_record_id "
                    + "AND sv.schema_version_id=sr.schema_version_id "
                    + "AND sv.module_snapshot_id=sr.module_snapshot_id "
                    + "AND sv.parent_field_snapshot_id=sr.parent_field_snapshot_id AND sv.row_id=sr.row_id "
                    + "WHERE sr.system_id=r.system_id AND sr.tenant_id=r.tenant_id "
                    + "AND sr.parent_record_id=r.record_id AND sr.schema_version_id=r.schema_version_id "
                    + "AND sr.module_snapshot_id=r.module_snapshot_id AND sr.parent_field_snapshot_id="
                    + field.id() + " AND sr.status='ACTIVE' AND sv.source_field_id=" + columnId + ")";
        }
        return comparableCondition(expression, operator,
                decimalValues(value.path("value"), operator, path + ".value", null), arguments);
    }

    private String condition(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        return switch (field.type()) {
            case "TEXT", "TEXTAREA", "PHONE", "EMAIL", "URL", "BARCODE" ->
                    stringCondition(operator, value, arguments, path);
            case "NUMBER" -> comparableCondition("i.decimal_value", operator,
                    decimalValues(value, operator, path, null), arguments);
            case "PERCENT", "RATING", "PROGRESS" -> comparableCondition("i.decimal_value", operator,
                    decimalValues(value, operator, path, field.type()), arguments);
            case "MONEY" -> moneyCondition(field, operator, value, arguments, path);
            case "DATE" -> comparableCondition("i.date_value", operator, dateValues(value, operator, path), arguments);
            case "DATETIME" -> comparableCondition("i.datetime_value", operator,
                    datetimeValues(value, operator, path), arguments);
            case "TIME" -> comparableCondition("i.time_value", operator, timeValues(value, operator, path), arguments);
            case "RADIO" -> referenceCondition("i.reference_value", operator, value, arguments, path, "IN");
            case "MEMBER", "DEPARTMENT" -> referenceCondition(
                    "i.reference_value", operator, value, arguments, path, "HAS_ANY");
            case "SWITCH" -> {
                arguments.add(booleanValue(value, path));
                yield "i.boolean_value=?";
            }
            case "STATUS" -> statusCondition(operator, value, arguments, path);
            default -> throw unavailable(path, "Field type has no query compiler");
        };
    }

    private String sensitivePredicate(
            QueryField field,
            JsonNode value,
            List<Object> arguments,
            String path,
            SensitiveHashProvider sensitiveHashes
    ) {
        if (sensitiveHashes == null) {
            throw unavailable(path, "Sensitive hash service is unavailable");
        }
        var hashes = sensitiveHashes.hashes(field, value);
        if (hashes.isEmpty()) {
            throw unavailable(path, "No query-compatible sensitive hash key is available");
        }
        var clauses = new ArrayList<String>();
        for (var hash : hashes) {
            if (hash.hashKeyVersion() == null || !hash.hashKeyVersion().matches("^[A-Za-z0-9._-]{1,64}$")
                    || hash.valueHash() == null || !hash.valueHash().matches("^[0-9a-f]{64}$")) {
                throw unavailable(path, "Sensitive hash service returned an invalid route");
            }
            arguments.add(hash.hashKeyVersion());
            arguments.add(hash.valueHash());
            clauses.add("(i.hash_key_version=? AND i.hash_value=?)");
        }
        return "EXISTS (" + indexBase("i", field) + " AND (" + String.join(" OR ", clauses) + "))";
    }

    private String addressPredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        if ("PREFIX".equals(operator)) {
            arguments.add(escapeLike(stringValue(value, path, 512)) + "%");
            return "EXISTS (" + indexBase("i", field)
                    + " AND i.ordinal=1 AND i.string_value LIKE ? ESCAPE '!')";
        }
        if (!value.isObject() || value.isEmpty() || value.size() > 2
                || !value.path("countryCode").isTextual()
                || value.properties().stream().anyMatch(entry -> !Set.of("countryCode", "regionCode")
                .contains(entry.getKey()))) {
            throw invalid(path + " EQ_REGION requires countryCode and optional regionCode");
        }
        var country = value.path("countryCode").textValue().toUpperCase(Locale.ROOT);
        var region = value.has("regionCode") ? stringValue(value.path("regionCode"), path + ".regionCode", 256) : "";
        if (!country.matches("^[A-Z]{2}$")) {
            throw invalid(path + ".countryCode must be an ISO alpha-2 code");
        }
        arguments.add(country + "|" + region);
        return "EXISTS (" + indexBase("i", field) + " AND i.ordinal=0 AND i.string_value=?)";
    }

    private String geoPredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var base = indexBase("i", field);
        var window = geoWindow(field, operator, value, path);
        arguments.add(window.south());
        arguments.add(window.north());
        arguments.add(window.west());
        arguments.add(window.east());
        var bounds = "i.geo_lat BETWEEN ? AND ? AND "
                + (window.wrapsAntimeridian() ? "(i.geo_lng>=? OR i.geo_lng<=?)" : "i.geo_lng BETWEEN ? AND ?");
        if ("WITHIN_BOX".equals(operator)) {
            return "EXISTS (" + base + " AND " + bounds + ")";
        }
        var lat = coordinate(value.path("lat"), path + ".lat", -90, 90);
        var lng = coordinate(value.path("lng"), path + ".lng", -180, 180);
        var radius = coordinate(value.path("radiusMeters"), path + ".radiusMeters", 0, 500000);
        arguments.add(lng);
        arguments.add(lat);
        arguments.add(radius);
        return "EXISTS (" + base + " AND " + bounds
                + " AND ST_Distance_Sphere(POINT(i.geo_lng,i.geo_lat),POINT(?,?))<=?)";
    }

    private GeoCandidateWindow geoWindow(QueryField field, String operator, JsonNode value, String path) {
        if ("WITHIN_BOX".equals(operator)) {
            requireExactObject(value, Set.of("south", "west", "north", "east"), path);
            var south = coordinate(value.path("south"), path + ".south", -90, 90);
            var west = coordinate(value.path("west"), path + ".west", -180, 180);
            var north = coordinate(value.path("north"), path + ".north", -90, 90);
            var east = coordinate(value.path("east"), path + ".east", -180, 180);
            if (south.compareTo(north) > 0 || west.compareTo(east) > 0) {
                throw invalid(path + " bounds must be ordered and cannot cross the antimeridian");
            }
            return new GeoCandidateWindow(field.id(), south, west, north, east, false);
        }
        requireExactObject(value, Set.of("lat", "lng", "radiusMeters"), path);
        var lat = coordinate(value.path("lat"), path + ".lat", -90, 90);
        var lng = coordinate(value.path("lng"), path + ".lng", -180, 180);
        var radius = coordinate(value.path("radiusMeters"), path + ".radiusMeters", 0, 500000);
        if (radius.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(path + ".radiusMeters must be greater than zero");
        }
        var latDelta = radius.doubleValue() / 111_320.0;
        var cosine = Math.cos(Math.toRadians(lat.doubleValue()));
        var lngDelta = Math.abs(cosine) < 1.0e-12 ? 180.0
                : Math.min(180.0, radius.doubleValue() / (111_320.0 * Math.abs(cosine)));
        var south = BigDecimal.valueOf(Math.max(-90.0, lat.doubleValue() - latDelta));
        var north = BigDecimal.valueOf(Math.min(90.0, lat.doubleValue() + latDelta));
        var rawWest = lng.doubleValue() - lngDelta;
        var rawEast = lng.doubleValue() + lngDelta;
        var wraps = rawWest < -180.0 || rawEast > 180.0;
        var west = BigDecimal.valueOf(rawWest < -180.0 ? rawWest + 360.0 : rawWest);
        var east = BigDecimal.valueOf(rawEast > 180.0 ? rawEast - 360.0 : rawEast);
        return new GeoCandidateWindow(field.id(), south, west, north, east, wraps);
    }

    private String richTextPredicate(
            QueryField field,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var text = stringValue(value, path, 100);
        final List<String> tokens;
        try {
            tokens = RecordSearchTokenizer.queryTokens(text);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " " + exception.getMessage());
        }
        var clauses = new ArrayList<String>();
        for (var token : tokens) {
            arguments.add(RecordSearchTokenizer.hash(token));
            clauses.add("EXISTS (SELECT 1 FROM un_module_record_search s WHERE "
                    + "s.system_id=r.system_id AND s.tenant_id=r.tenant_id AND s.record_id=r.record_id "
                    + "AND s.schema_version_id=r.schema_version_id AND s.module_snapshot_id=r.module_snapshot_id "
                    + "AND s.logical_module_id=r.logical_module_id AND s.logical_field_id=" + field.id() + " "
                    + "AND s.index_generation_id=1 AND s.record_status=r.status AND s.token_hash=?)");
        }
        return "(" + String.join(" AND ", clauses) + ")";
    }

    private String jsonPredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        if ("DECLARED_PATH_EXISTS".equals(operator)) {
            var declaration = jsonPath(field, value, path);
            arguments.add(declaration.id());
            return "EXISTS (" + indexBase("i", field) + " AND i.path_snapshot_id=?)";
        }
        if (!value.isObject() || value.size() != 2 || !value.has("pathSnapshotId") || !value.has("value")) {
            throw invalid(path + " requires only pathSnapshotId and value");
        }
        var declaration = jsonPath(field, value.path("pathSnapshotId"), path + ".pathSnapshotId");
        var supplied = value.path("value");
        if (supplied.isNull()) {
            throw invalid(path + ".value cannot be null for DECLARED_PATH_EQ");
        }
        arguments.add(declaration.id());
        var condition = switch (declaration.type()) {
            case "STRING" -> {
                arguments.add(stringValue(supplied, path + ".value", 512));
                yield "i.string_value=?";
            }
            case "DECIMAL" -> {
                arguments.add(jsonDecimal(supplied, path + ".value", false));
                yield "i.decimal_value=?";
            }
            case "INTEGER" -> {
                arguments.add(jsonDecimal(supplied, path + ".value", true));
                yield "i.decimal_value=?";
            }
            case "BOOLEAN" -> {
                arguments.add(booleanValue(supplied, path + ".value"));
                yield "i.boolean_value=?";
            }
            case "DATE" -> {
                arguments.add(dateValues(supplied, "EQ", path + ".value").getFirst());
                yield "i.date_value=?";
            }
            case "DATETIME" -> {
                arguments.add(datetimeValues(supplied, "EQ", path + ".value").getFirst());
                yield "i.datetime_value=?";
            }
            default -> throw unavailable(path, "Declared JSON path type is unavailable");
        };
        return "EXISTS (" + indexBase("i", field) + " AND i.path_snapshot_id=? AND " + condition + ")";
    }

    private String statusCondition(String operator, JsonNode value, List<Object> arguments, String path) {
        if ("IN".equals(operator)) {
            var values = referenceStrings(value, path);
            arguments.addAll(values);
            return "i.string_value IN (" + placeholders(values.size()) + ")";
        }
        arguments.add(referenceString(value, path));
        return "i.string_value=?";
    }

    private String rangePredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path,
            boolean time
    ) {
        var column = time ? "time_value" : "date_value";
        var base = indexBase("i", field);
        var peer = indexBase("j", field);
        if ("OVERLAPS".equals(operator)) {
            if (time) {
                var bounds = timeValues(value, "BETWEEN", path);
                requireOrdered(bounds, "Range lower bound must not exceed upper bound");
                arguments.add(bounds.get(1));
                arguments.add(bounds.get(0));
            } else {
                var bounds = dateValues(value, "BETWEEN", path);
                requireOrdered(bounds, "Range lower bound must not exceed upper bound");
                arguments.add(bounds.get(1));
                arguments.add(bounds.get(0));
            }
            return "EXISTS (" + base + " AND i.ordinal=0 AND i." + column + "<=? AND EXISTS ("
                    + peer + " AND j.ordinal=1 AND j." + column + ">=?))";
        }
        if ("CONTAINS".equals(operator)) {
            var bound = time ? timeValues(value, "EQ", path).getFirst()
                    : dateValues(value, "EQ", path).getFirst();
            arguments.add(bound);
            arguments.add(bound);
            return "EXISTS (" + base + " AND i.ordinal=0 AND i." + column + "<=? AND EXISTS ("
                    + peer + " AND j.ordinal=1 AND j." + column + ">=?))";
        }
        var bound = dateValues(value, "EQ", path).getFirst();
        arguments.add(bound);
        if ("BEFORE".equals(operator)) {
            return "EXISTS (" + peer + " AND j.ordinal=1 AND j.date_value<?)";
        }
        if ("AFTER".equals(operator)) {
            return "EXISTS (" + base + " AND i.ordinal=0 AND i.date_value>?)";
        }
        throw unavailable(path, "Range operator is unavailable");
    }

    private String membershipPredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var values = "TAG".equals(field.type()) ? tagValues(value, path) : referenceStrings(value, path);
        var base = indexBase("i", field);
        if ("HAS_ALL".equals(operator)) {
            var clauses = new ArrayList<String>();
            for (var item : values) {
                arguments.add(item);
                clauses.add("EXISTS (" + base + " AND i.string_value=?)");
            }
            return "(" + String.join(" AND ", clauses) + ")";
        }
        arguments.addAll(values);
        var matching = "EXISTS (" + base + " AND i.string_value IN (" + placeholders(values.size()) + "))";
        return "NOT_ANY".equals(operator) ? "NOT " + matching : matching;
    }

    private String cascadePredicate(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var selected = referenceString(value, path);
        arguments.add(selected);
        var base = indexBase("i", field);
        if ("CONTAINS_NODE".equals(operator)) {
            return "EXISTS (" + base + " AND i.string_value=?)";
        }
        var peer = indexBase("j", field);
        return "EXISTS (" + base + " AND i.string_value=? AND i.ordinal=(SELECT MAX(j.ordinal) FROM "
                + peer.substring("SELECT 1 FROM ".length()) + "))";
    }

    private String moneyCondition(
            QueryField field,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var values = moneyValues(field, value, operator, path);
        var currency = values.getFirst().currency();
        if (values.stream().anyMatch(item -> !currency.equals(item.currency()))) {
            throw invalid(path + " BETWEEN bounds must use the same currency");
        }
        arguments.add(currency);
        if ("BETWEEN".equals(operator)) {
            requireOrdered(values.stream().map(MoneyBound::amount).toList(),
                    "BETWEEN lower bound must not exceed upper bound");
            arguments.add(values.get(0).amount());
            arguments.add(values.get(1).amount());
            return "i.currency_code=? AND i.decimal_value BETWEEN ? AND ?";
        }
        arguments.add(values.getFirst().amount());
        return "i.currency_code=? AND i.decimal_value" + comparison(operator);
    }

    private String stringCondition(String operator, JsonNode value, List<Object> arguments, String path) {
        return stringCondition("i.string_value", operator, value, arguments, path);
    }

    private String stringCondition(
            String column,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path
    ) {
        var text = stringValue(value, path, 512);
        return switch (operator) {
            case "EQ" -> {
                arguments.add(text);
                yield column + "=?";
            }
            case "CONTAINS" -> {
                arguments.add("%" + escapeLike(text) + "%");
                yield column + " LIKE ? ESCAPE '!'";
            }
            case "PREFIX" -> {
                arguments.add(escapeLike(text) + "%");
                yield column + " LIKE ? ESCAPE '!'";
            }
            default -> throw unavailable(path, "String operator is unavailable");
        };
    }

    private <T> String comparableCondition(
            String column,
            String operator,
            List<T> values,
            List<Object> arguments
    ) {
        if ("BETWEEN".equals(operator)) {
            requireOrdered(values, "BETWEEN lower bound must not exceed upper bound");
            arguments.addAll(values);
            return column + " BETWEEN ? AND ?";
        }
        arguments.add(values.getFirst());
        return column + comparison(operator);
    }

    private String referenceCondition(
            String column,
            String operator,
            JsonNode value,
            List<Object> arguments,
            String path,
            String arrayOperator
    ) {
        if (!arrayOperator.equals(operator)) {
            arguments.add(reference(value, path));
            return column + "=?";
        }
        var values = references(value, path);
        arguments.addAll(values);
        return column + " IN (" + placeholders(values.size()) + ")";
    }

    private static List<BigDecimal> decimalValues(
            JsonNode value,
            String operator,
            String path,
            String fieldType
    ) {
        return values(value, operator, path).stream().map(node -> {
            if (!node.isNumber()) {
                throw invalid(path + " must contain JSON numbers");
            }
            var number = node.decimalValue();
            if (number.precision() > 38 || switch (fieldType == null ? "NUMBER" : fieldType) {
                case "PERCENT" -> number.scale() > 4 || number.compareTo(BigDecimal.ZERO) < 0
                        || number.compareTo(new BigDecimal("100")) > 0;
                case "PROGRESS" -> number.scale() > 2 || number.compareTo(BigDecimal.ZERO) < 0
                        || number.compareTo(new BigDecimal("100")) > 0;
                case "RATING" -> number.scale() > 0 || number.compareTo(BigDecimal.ONE) < 0
                        || number.compareTo(new BigDecimal("5")) > 0;
                default -> number.scale() > 10;
            }) {
                throw invalid(path + " contains a numeric value outside the field range or scale");
            }
            return number;
        }).toList();
    }

    private static List<LocalDate> dateValues(JsonNode value, String operator, String path) {
        return values(value, operator, path).stream().map(node -> {
            try {
                return LocalDate.parse(stringValue(node, path, 32), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException exception) {
                throw invalid(path + " must contain ISO dates");
            }
        }).toList();
    }

    private static List<LocalDateTime> datetimeValues(JsonNode value, String operator, String path) {
        return values(value, operator, path).stream().map(node -> {
            try {
                return LocalDateTime.parse(stringValue(node, path, 40));
            } catch (DateTimeParseException exception) {
                throw invalid(path + " must contain ISO local date-times");
            }
        }).toList();
    }

    private static List<LocalTime> timeValues(JsonNode value, String operator, String path) {
        return values(value, operator, path).stream().map(node -> {
            var text = stringValue(node, path, 8);
            if (!text.matches("^[0-9]{2}:[0-9]{2}:[0-9]{2}$")) {
                throw invalid(path + " must contain times in HH:mm:ss format");
            }
            try {
                return LocalTime.parse(text, TIME_FORMAT);
            } catch (DateTimeParseException exception) {
                throw invalid(path + " must contain valid local times");
            }
        }).toList();
    }

    private static List<MoneyBound> moneyValues(QueryField field, JsonNode value, String operator, String path) {
        return values(value, operator, path).stream().map(node -> moneyValue(field, node, path)).toList();
    }

    private static MoneyBound moneyValue(QueryField field, JsonNode node, String path) {
        if (!node.isObject() || node.size() != 2 || !node.path("amount").isTextual()
                || !node.path("currency").isTextual()) {
            throw invalid(path + " money value must contain only amount and currency strings");
        }
        var currencyCode = node.path("currency").textValue();
        if (!currencyCode.matches("^[A-Z]{3}$") || !allowedCurrencies(field).contains(currencyCode)) {
            throw invalid(path + " contains a currency not enabled for this field");
        }
        final Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " contains an invalid ISO-4217 currency");
        }
        var amountText = node.path("amount").textValue();
        if (!amountText.matches("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$")) {
            throw invalid(path + " amount must be an exact decimal string");
        }
        var amount = new BigDecimal(amountText);
        if (currency.getDefaultFractionDigits() < 0 || amount.precision() > 38
                || amount.scale() != currency.getDefaultFractionDigits()) {
            throw invalid(path + " amount scale must match its currency");
        }
        return new MoneyBound(amount, currencyCode);
    }

    private static List<JsonNode> values(JsonNode value, String operator, String path) {
        if (!"BETWEEN".equals(operator)) {
            return List.of(value);
        }
        if (!value.isArray() || value.size() != 2) {
            throw invalid(path + " must contain exactly two bounds");
        }
        return List.of(value.get(0), value.get(1));
    }

    private static List<Long> references(JsonNode value, String path) {
        if (!value.isArray() || value.isEmpty() || value.size() > 100) {
            throw invalid(path + " must contain 1..100 reference ids");
        }
        var result = new ArrayList<Long>();
        value.forEach(node -> {
            var parsed = reference(node, path);
            if (result.contains(parsed)) {
                throw invalid(path + " reference ids must be unique");
            }
            result.add(parsed);
        });
        return List.copyOf(result);
    }

    private static List<String> referenceStrings(JsonNode value, String path) {
        if (!value.isArray() || value.isEmpty() || value.size() > 100) {
            throw invalid(path + " must contain 1..100 option ids");
        }
        var result = new ArrayList<String>();
        value.forEach(node -> {
            var parsed = referenceString(node, path);
            if (result.contains(parsed)) {
                throw invalid(path + " option ids must be unique");
            }
            result.add(parsed);
        });
        return List.copyOf(result);
    }

    private static List<String> tagValues(JsonNode value, String path) {
        if (!value.isArray() || value.isEmpty() || value.size() > 100) {
            throw invalid(path + " must contain 1..100 tags");
        }
        var result = new ArrayList<String>();
        value.forEach(node -> {
            var normalized = Normalizer.normalize(stringValue(node, path, 64), Normalizer.Form.NFKC).trim();
            if (normalized.isEmpty() || result.contains(normalized)) {
                throw invalid(path + " tags must be unique normalized strings");
            }
            result.add(normalized);
        });
        return List.copyOf(result);
    }

    private static long reference(JsonNode node, String path) {
        try {
            return Long.parseLong(referenceString(node, path));
        } catch (NumberFormatException exception) {
            throw invalid(path + " reference id is outside the supported range");
        }
    }

    private static String referenceString(JsonNode node, String path) {
        var value = stringValue(node, path, 19);
        if (!value.matches("^[1-9][0-9]{0,18}$")) {
            throw invalid(path + " must contain positive decimal reference ids");
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(path + " reference id is outside the supported range");
        }
        return value;
    }

    private static SortExpression sortExpression(QueryField field, SortItem sort) {
        if (!"MONEY".equals(field.type()) && sort.currency() != null) {
            throw invalid("sort.currency is valid only for MONEY fields");
        }
        if ("RECORD_UPDATED_AT".equals(field.type())) {
            return new SortExpression("r.updated_at", List.of());
        }
        var column = switch (field.type()) {
            case "TEXT", "TEXTAREA", "BARCODE", "STATUS" -> "string_value";
            case "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS" -> "decimal_value";
            case "DATE" -> "date_value";
            case "DATETIME" -> "datetime_value";
            case "TIME" -> "time_value";
            case "SWITCH" -> "boolean_value";
            case "RADIO", "MEMBER", "DEPARTMENT" -> "reference_value";
            default -> throw unavailable("sort." + field.code(), "Field type is not sortable");
        };
        var arguments = new ArrayList<Object>();
        var currencyPredicate = "";
        if ("MONEY".equals(field.type())) {
            var currency = sortCurrency(field, sort.currency());
            currencyPredicate = " AND i.currency_code=?";
            arguments.add(currency);
        }
        var sql = "(SELECT i." + column + " FROM un_module_record_index i WHERE "
                + "i.system_id=r.system_id AND i.tenant_id=r.tenant_id AND i.record_id=r.record_id "
                + "AND i.schema_version_id=r.schema_version_id AND i.module_snapshot_id=r.module_snapshot_id "
                + "AND i.logical_module_id=r.logical_module_id AND i.logical_field_id=" + field.id() + " "
                + "AND i.index_generation_id=1 AND i.record_status=r.status AND i.ordinal=0"
                + currencyPredicate + " LIMIT 1)";
        return new SortExpression(sql, arguments);
    }

    private static String sortValueType(QueryField field) {
        return switch (field.type()) {
            case "RECORD_UPDATED_AT" -> "DATETIME";
            case "TEXT", "TEXTAREA", "BARCODE", "STATUS" -> "STRING";
            case "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS" -> "DECIMAL";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "TIME" -> "TIME";
            case "SWITCH" -> "BOOLEAN";
            case "RADIO", "MEMBER", "DEPARTMENT" -> "LONG";
            default -> throw unavailable("sort." + field.code(), "Field type is not sortable");
        };
    }

    private static String sortCurrency(QueryField field, String supplied) {
        var allowed = allowedCurrencies(field);
        if (supplied != null) {
            if (!allowed.contains(supplied)) {
                throw invalid("sort.currency is not enabled for field " + field.code());
            }
            return supplied;
        }
        var schema = field.schema();
        for (var key : List.of("fixedCurrency", "currency")) {
            if (schema != null && schema.path(key).isTextual()) {
                return schema.path(key).textValue().toUpperCase(Locale.ROOT);
            }
        }
        if (allowed.size() == 1) {
            return allowed.iterator().next();
        }
        throw invalid("sort.currency is required for a multi-currency MONEY field");
    }

    private static Set<String> allowedCurrencies(QueryField field) {
        var result = new HashSet<String>();
        var schema = field.schema();
        if (schema != null && schema.path("currencies").isArray()) {
            schema.path("currencies").forEach(item -> result.add(item.asText().toUpperCase(Locale.ROOT)));
        }
        for (var key : List.of("currency", "fixedCurrency")) {
            if (schema != null && schema.path(key).isTextual()) {
                result.add(schema.path(key).textValue().toUpperCase(Locale.ROOT));
            }
        }
        if (result.isEmpty()) {
            result.add("CNY");
            result.add("USD");
        }
        return Set.copyOf(result);
    }

    private static String indexBase(String alias, QueryField field) {
        return "SELECT 1 FROM un_module_record_index " + alias + " WHERE "
                + alias + ".system_id=r.system_id AND " + alias + ".tenant_id=r.tenant_id AND "
                + alias + ".record_id=r.record_id AND " + alias + ".schema_version_id=r.schema_version_id AND "
                + alias + ".module_snapshot_id=r.module_snapshot_id AND "
                + alias + ".logical_module_id=r.logical_module_id AND "
                + alias + ".logical_field_id=" + field.id() + " AND "
                + alias + ".index_generation_id=1 AND " + alias + ".record_status=r.status";
    }

    private static String comparison(String operator) {
        return switch (operator) {
            case "EQ" -> "=?";
            case "NE" -> "<>?";
            case "GT", "AFTER" -> ">?";
            case "GTE" -> ">=?";
            case "LT", "BEFORE" -> "<?";
            case "LTE" -> "<=?";
            default -> throw unavailable("filter.operator", "Comparable operator is unavailable");
        };
    }

    private static <T> void requireOrdered(List<T> values, String message) {
        @SuppressWarnings("unchecked")
        var first = (Comparable<T>) values.get(0);
        if (first.compareTo(values.get(1)) > 0) {
            throw invalid(message);
        }
    }

    private static void requireExactObject(JsonNode value, Set<String> keys, String path) {
        if (value == null || !value.isObject() || value.size() != keys.size()) {
            throw invalid(path + " must contain exactly " + String.join(",", keys));
        }
        var supplied = new HashSet<String>();
        value.fieldNames().forEachRemaining(supplied::add);
        if (!supplied.equals(keys)) {
            throw invalid(path + " contains unknown or missing properties");
        }
    }

    private static BigDecimal coordinate(JsonNode node, String path, int minimum, int maximum) {
        if (node == null || !node.isNumber()) {
            throw invalid(path + " must be a finite JSON number");
        }
        var value = node.decimalValue();
        if (value.scale() > 10 || value.precision() > 20
                || value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw invalid(path + " is outside its supported range or precision");
        }
        return value;
    }

    private static BigDecimal jsonDecimal(JsonNode node, String path, boolean integer) {
        if (node == null || !node.isNumber()) {
            throw invalid(path + " must be a JSON number");
        }
        var value = node.decimalValue();
        if (value.precision() > 38 || value.scale() > 10 || integer && value.stripTrailingZeros().scale() > 0) {
            throw invalid(path + " is outside the declared JSON numeric type");
        }
        return value;
    }

    private static JsonPathDeclaration jsonPath(QueryField field, JsonNode node, String path) {
        final long id;
        try {
            var raw = node != null && node.isIntegralNumber() ? node.longValue()
                    : Long.parseLong(stringValue(node, path, 19));
            if (raw <= 0) {
                throw new NumberFormatException();
            }
            id = raw;
        } catch (NumberFormatException exception) {
            throw invalid(path + " must be a positive declared pathSnapshotId");
        }
        var declarations = field.schema() == null ? null : field.schema().path("queryPaths");
        if (declarations == null || !declarations.isArray()) {
            throw unavailable(path, "Field has no declared JSON query paths");
        }
        for (var declaration : declarations) {
            var candidate = declaration.path("pathSnapshotId");
            long candidateId = -1;
            if (candidate.isIntegralNumber()) {
                candidateId = candidate.longValue();
            } else if (candidate.asText("").matches("^[1-9][0-9]{0,18}$")) {
                try {
                    candidateId = Long.parseLong(candidate.asText());
                } catch (NumberFormatException ignored) {
                    candidateId = -1;
                }
            }
            if (candidateId == id) {
                var type = declaration.path("type").asText();
                if (!Set.of("STRING", "DECIMAL", "INTEGER", "BOOLEAN", "DATE", "DATETIME").contains(type)) {
                    throw unavailable(path, "Declared JSON path type is unavailable");
                }
                return new JsonPathDeclaration(id, declaration.path("path").asText(), type);
            }
        }
        throw unavailable(path, "JSON path is not declared in the active field snapshot");
    }

    private static String stringValue(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isEmpty() || node.textValue().length() > maximum) {
            throw invalid(path + " must be a non-empty string no longer than " + maximum);
        }
        return node.textValue();
    }

    private static boolean booleanValue(JsonNode node, String path) {
        if (node == null || !node.isBoolean()) {
            throw invalid(path + " must be boolean");
        }
        return node.booleanValue();
    }

    private static QueryField requireField(Map<String, QueryField> fields, String code, String path) {
        var field = fields.get(code);
        if (field == null) {
            throw unavailable(path, "Field is unknown, retired or unreadable");
        }
        return field;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("QUERY_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException unavailable(String path, String message) {
        return new BusinessException("QUERY_FIELD_UNAVAILABLE", path + ": " + message,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private record MoneyBound(BigDecimal amount, String currency) { }

    private record JsonPathDeclaration(long id, String path, String type) { }

    public record GeoCandidateWindow(
            long fieldId,
            BigDecimal south,
            BigDecimal west,
            BigDecimal north,
            BigDecimal east,
            boolean wrapsAntimeridian
    ) { }

    private record SortExpression(String sql, List<Object> arguments) {
        private SortExpression {
            arguments = List.copyOf(arguments);
        }
    }
}
