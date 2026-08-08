package com.unique.examine.module.runtime.query;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.query.RecordQueryModels.QueryField;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordQueryParserCompilerTest {
    private final RecordQueryParser parser = new RecordQueryParser();
    private final RecordQueryCompiler compiler = new RecordQueryCompiler();

    @Test
    void canonicalizesAndCompilesTypedPredicatesWithoutUsingRequestFieldsAsSql() {
        var query = parser.parse("""
                {
                  "sort":[{"nulls":"LAST","direction":"DESC","fieldCode":"amount"}],
                  "filter":{"kind":"AND","children":[
                    {"kind":"PREDICATE","fieldCode":"amount","operator":"BETWEEN","value":[10.00,20]},
                    {"kind":"PREDICATE","fieldCode":"due","operator":"AFTER","value":"2026-07-01"},
                    {"kind":"PREDICATE","fieldCode":"owner","operator":"HAS_ANY","value":["11","12"]},
                    {"kind":"PREDICATE","fieldCode":"priority","operator":"IN","value":["21","22"]}
                  ]},
                  "recordScope":"active",
                  "size":50,
                  "q":null,
                  "columns":["amount","due"],
                  "schemaVersionId":"100",
                  "page":1,
                  "viewId":null
                }
                """);
        var compiled = compiler.compile(query, List.of(
                new QueryField(1, "amount", "NUMBER", false, true, true, JsonNodeFactory.instance.objectNode()),
                new QueryField(2, "due", "DATE", false, true, true, JsonNodeFactory.instance.objectNode()),
                new QueryField(3, "owner", "MEMBER", false, true, false, JsonNodeFactory.instance.objectNode()),
                new QueryField(4, "priority", "RADIO", false, true, false, JsonNodeFactory.instance.objectNode())
        ));

        assertThat(query.canonicalJson()).startsWith("{\"columns\"")
                .contains("\"value\":[10,20]");
        assertThat(compiled.predicateSql())
                .contains("i.logical_field_id=1", "i.logical_field_id=2", "i.logical_field_id=3",
                        "i.logical_field_id=4", "BETWEEN ? AND ?", "IN (?,?)")
                .doesNotContain("amount", "owner", "priority");
        assertThat((java.math.BigDecimal) compiled.arguments().get(0)).isEqualByComparingTo("10");
        assertThat((java.math.BigDecimal) compiled.arguments().get(1)).isEqualByComparingTo("20");
        assertThat(compiled.arguments().subList(2, compiled.arguments().size())).containsExactly(
                java.time.LocalDate.of(2026, 7, 1), 11L, 12L, 21L, 22L);
        assertThat(compiled.orderSql()).contains("i.logical_field_id=1", "DESC", "r.record_id ASC");
        assertThat(parser.sha256(query.canonicalJson())).matches("^[a-f0-9]{64}$");
    }

    @Test
    void compilesRecordNumberAsAnAllowlistedNativeColumn() {
        var query = parser.parse("""
                {"schemaVersionId":"100","page":1,"size":50,"recordScope":"active","q":null,
                 "filter":{"kind":"PREDICATE","fieldCode":"record_no","operator":"EQ","value":"R-0500000"},
                 "sort":[],"columns":[],"viewId":null}
                """);

        var compiled = compiler.compile(query, List.of(new QueryField(
                0L, "record_no", "RECORD_NO", false, true, false,
                JsonNodeFactory.instance.objectNode())));

        assertThat(compiled.predicateSql()).isEqualTo("r.record_no=?");
        assertThat(compiled.arguments()).containsExactly("R-0500000");
    }

    @Test
    void compilesDepartmentTreeAgainstTheTenantClosureTable() {
        var query = parser.parse("""
                {"schemaVersionId":"100","page":1,"size":50,"recordScope":"active","q":null,
                 "filter":{"kind":"PREDICATE","fieldCode":"department","operator":"IN_TREE","value":"42"},
                 "sort":[],"columns":[],"viewId":null}
                """);

        var compiled = compiler.compile(query, List.of(new QueryField(
                9L, "department", "DEPARTMENT", false, true, false,
                JsonNodeFactory.instance.objectNode())));

        assertThat(compiled.predicateSql())
                .contains("un_plat_department_closure", "dc.scope_key=r.system_id", "dc.tenant_key=r.tenant_id")
                .doesNotContain("42");
        assertThat(compiled.arguments()).containsExactly(42L);
    }

    @Test
    void sortsByAllowlistedNativeUpdatedAtWithoutAnIndexSubquery() {
        var query = parser.parse("""
                {"schemaVersionId":"100","page":1,"size":50,"recordScope":"active","q":null,
                 "filter":null,"sort":[{"fieldCode":"updated_at","direction":"DESC","nulls":"LAST"}],
                 "columns":[],"viewId":null}
                """);

        var compiled = compiler.compile(query, List.of(new QueryField(
                -1L, "updated_at", "RECORD_UPDATED_AT", false, false, true,
                JsonNodeFactory.instance.objectNode())));

        assertThat(compiled.orderSql()).startsWith("r.updated_at IS NULL ASC,r.updated_at DESC")
                .doesNotContain("un_module_record_index");
    }

    @Test
    void rejectsDuplicateKeysInvalidDepthAndUnavailableFields() {
        assertCode("QUERY_INVALID", () -> parser.parse("""
                {"schemaVersionId":"1","schemaVersionId":"2"}
                """));
        assertCode("QUERY_INVALID", () -> parser.parse("""
                {"schemaVersionId":"1","page":1,"size":50,"recordScope":"active","q":null,
                 "filter":{"kind":"NOT","children":[{"kind":"NOT","children":[{"kind":"NOT","children":[
                   {"kind":"NOT","children":[{"kind":"NOT","children":[{"kind":"NOT","children":[
                     {"kind":"PREDICATE","fieldCode":"title","operator":"EQ","value":"x"}]}]}]}]}]}]},
                 "sort":[],"columns":[],"viewId":null}
                """));
        var query = parser.parse("""
                {"schemaVersionId":"1","page":1,"size":50,"recordScope":"active","q":null,
                 "filter":{"kind":"PREDICATE","fieldCode":"hidden","operator":"EQ","value":"x"},
                 "sort":[],"columns":[],"viewId":null}
                """);
        assertCode("QUERY_FIELD_UNAVAILABLE", () -> compiler.compile(query, List.of()));
    }

    @Test
    void compilesEveryP4C1ExplicitOperatorAgainstTypedIndexes() {
        var moneySchema = JsonNodeFactory.instance.objectNode();
        moneySchema.putArray("currencies").add("CNY").add("USD");
        var emptySchema = JsonNodeFactory.instance.objectNode();
        var fields = Map.ofEntries(
                Map.entry("PERCENT", new QueryField(11, "tested", "PERCENT", false, true, true, emptySchema)),
                Map.entry("MONEY", new QueryField(12, "tested", "MONEY", false, true, true, moneySchema)),
                Map.entry("DATE_RANGE", new QueryField(13, "tested", "DATE_RANGE", false, true, false, emptySchema)),
                Map.entry("TIME", new QueryField(14, "tested", "TIME", false, true, true, emptySchema)),
                Map.entry("TIME_RANGE", new QueryField(15, "tested", "TIME_RANGE", false, true, false, emptySchema)),
                Map.entry("MULTI_SELECT", new QueryField(16, "tested", "MULTI_SELECT", false, true, false, emptySchema)),
                Map.entry("CASCADE", new QueryField(17, "tested", "CASCADE", false, true, false, emptySchema)),
                Map.entry("SWITCH", new QueryField(18, "tested", "SWITCH", false, true, true, emptySchema)),
                Map.entry("RATING", new QueryField(19, "tested", "RATING", false, true, true, emptySchema)),
                Map.entry("PROGRESS", new QueryField(20, "tested", "PROGRESS", false, true, true, emptySchema)),
                Map.entry("TAG", new QueryField(21, "tested", "TAG", false, true, false, emptySchema))
        );
        var operators = Map.ofEntries(
                Map.entry("PERCENT", Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY")),
                Map.entry("MONEY", Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY")),
                Map.entry("DATE_RANGE", Set.of("OVERLAPS", "CONTAINS", "BEFORE", "AFTER", "EMPTY")),
                Map.entry("TIME", Set.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY")),
                Map.entry("TIME_RANGE", Set.of("OVERLAPS", "CONTAINS", "EMPTY")),
                Map.entry("MULTI_SELECT", Set.of("HAS_ANY", "HAS_ALL", "NOT_ANY", "EMPTY")),
                Map.entry("CASCADE", Set.of("CONTAINS_NODE", "LEAF_EQ", "EMPTY")),
                Map.entry("SWITCH", Set.of("EQ", "EMPTY")),
                Map.entry("RATING", Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY")),
                Map.entry("PROGRESS", Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY")),
                Map.entry("TAG", Set.of("HAS_ANY", "HAS_ALL", "EMPTY"))
        );

        operators.forEach((type, typeOperators) -> typeOperators.forEach(operator -> {
            var compiled = compiler.compile(parser.parse(queryJson(type, operator, value(type, operator), "[]")),
                    List.of(fields.get(type)));
            assertThat(compiled.predicateSql()).contains("logical_field_id=" + fields.get(type).id());
        }));

        var notAny = compiler.compile(parser.parse(queryJson(
                "MULTI_SELECT", "NOT_ANY", "[\"101\",\"102\"]", "[]")),
                List.of(fields.get("MULTI_SELECT")));
        assertThat(notAny.predicateSql()).startsWith("NOT EXISTS");
        var hasAll = compiler.compile(parser.parse(queryJson(
                "TAG", "HAS_ALL", "[\"red\",\"blue\"]", "[]")), List.of(fields.get("TAG")));
        assertThat(hasAll.predicateSql()).contains(" AND ").contains("i.string_value=?");
        var overlaps = compiler.compile(parser.parse(queryJson(
                "DATE_RANGE", "OVERLAPS", "[\"2026-01-10\",\"2026-01-20\"]", "[]")),
                List.of(fields.get("DATE_RANGE")));
        assertThat(overlaps.predicateSql()).contains("i.ordinal=0", "j.ordinal=1", "i.date_value<=?", "j.date_value>=?");
    }

    @Test
    void enforcesP4C1SortCurrencyAndStrictValueRules() {
        var moneySchema = JsonNodeFactory.instance.objectNode();
        moneySchema.putArray("currencies").add("CNY").add("USD");
        var money = new QueryField(31, "tested", "MONEY", false, true, true, moneySchema);
        var sorted = compiler.compile(parser.parse(queryJson(
                "MONEY", "EQ", "{\"amount\":\"10.00\",\"currency\":\"CNY\"}",
                "[{\"fieldCode\":\"tested\",\"direction\":\"ASC\",\"nulls\":\"LAST\",\"currency\":\"CNY\"}]")),
                List.of(money));
        assertThat(sorted.orderSql()).contains("i.currency_code=?", "i.decimal_value");
        assertThat(sorted.orderArguments()).containsExactly("CNY", "CNY");

        assertCode("QUERY_INVALID", () -> compiler.compile(parser.parse(queryJson(
                "MONEY", "EQ", "{\"amount\":\"10.00\",\"currency\":\"CNY\"}",
                "[{\"fieldCode\":\"tested\",\"direction\":\"ASC\",\"nulls\":\"LAST\"}]")), List.of(money)));
        assertCode("QUERY_INVALID", () -> compiler.compile(parser.parse(queryJson(
                "MONEY", "BETWEEN", "[{\"amount\":\"9.00\",\"currency\":\"CNY\"},"
                        + "{\"amount\":\"10.00\",\"currency\":\"USD\"}]", "[]")), List.of(money)));
        var time = new QueryField(32, "tested", "TIME", false, true, true,
                JsonNodeFactory.instance.objectNode());
        assertCode("QUERY_INVALID", () -> compiler.compile(parser.parse(queryJson(
                "TIME", "EQ", "\"24:00:00\"", "[]")), List.of(time)));
        var percent = new QueryField(33, "tested", "PERCENT", false, true, true,
                JsonNodeFactory.instance.objectNode());
        assertCode("QUERY_INVALID", () -> compiler.compile(parser.parse(queryJson(
                "PERCENT", "GT", "100.0001", "[]")), List.of(percent)));
    }

    @Test
    void compilesEveryP4C2ExplicitOperatorAndSensitiveHashVersion() {
        var empty = JsonNodeFactory.instance.objectNode();
        var json = JsonNodeFactory.instance.objectNode();
        json.putArray("queryPaths").addObject()
                .put("pathSnapshotId", "101").put("path", "$.ticket").put("type", "STRING");
        var fields = Map.ofEntries(
                Map.entry("PHONE", p4c2(41, "PHONE", empty, false, false)),
                Map.entry("EMAIL", p4c2(42, "EMAIL", empty, false, false)),
                Map.entry("URL", p4c2(43, "URL", empty, false, false)),
                Map.entry("IDENTITY", p4c2(44, "IDENTITY", empty, true, false)),
                Map.entry("ADDRESS", p4c2(45, "ADDRESS", empty, false, false)),
                Map.entry("GEO", p4c2(46, "GEO", empty, false, false)),
                Map.entry("BARCODE", p4c2(47, "BARCODE", empty, false, true)),
                Map.entry("RICH_TEXT", p4c2(48, "RICH_TEXT", empty, false, false)),
                Map.entry("JSON", p4c2(49, "JSON", json, false, false)),
                Map.entry("SECRET", p4c2(50, "SECRET", empty, true, false)),
                Map.entry("STATUS", p4c2(51, "STATUS", empty, false, true))
        );
        var operators = Map.ofEntries(
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
                Map.entry("STATUS", Set.of("EQ", "IN", "EMPTY"))
        );
        RecordQueryModels.SensitiveHashProvider hashes = (field, value) -> List.of(
                new RecordQueryModels.SensitiveHash("hash-v1", "1".repeat(64)),
                new RecordQueryModels.SensitiveHash("hash-v2", "2".repeat(64)));

        operators.forEach((type, typeOperators) -> typeOperators.forEach(operator -> {
            var compiled = compiler.compile(parser.parse(queryJson(
                    type, operator, p4c2Value(type, operator), p4c2Sort(type))),
                    List.of(fields.get(type)), hashes);
            assertThat(compiled.predicateSql()).contains("logical_field_id=" + fields.get(type).id());
        }));

        var sensitive = compiler.compile(parser.parse(queryJson(
                "SECRET", "EQ", "\"exact secret\"", "[]")), List.of(fields.get("SECRET")), hashes);
        assertThat(sensitive.predicateSql()).contains("i.hash_key_version=?", "i.hash_value=?", " OR ");
        assertThat(sensitive.arguments()).containsExactly(
                "hash-v1", "1".repeat(64), "hash-v2", "2".repeat(64));
        assertThat(compiler.compile(parser.parse(queryJson(
                "GEO", "NEAR", "{\"lat\":39.9,\"lng\":116.4,\"radiusMeters\":5000}", "[]")),
                List.of(fields.get("GEO"))).predicateSql()).contains("ST_Distance_Sphere");
    }

    @Test
    void rejectsSensitiveFiltersWithoutPermissionAndUndeclaredJsonPaths() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.putArray("queryPaths").addObject()
                .put("pathSnapshotId", "101").put("path", "$.ticket").put("type", "STRING");
        var noSensitivePermission = new QueryField(
                61, "tested", "IDENTITY", false, true, false, false, schema);
        assertCode("QUERY_FIELD_UNAVAILABLE", () -> compiler.compile(parser.parse(queryJson(
                "IDENTITY", "EQ", "\"110101199001011237\"", "[]")), List.of(noSensitivePermission),
                (field, value) -> List.of(new RecordQueryModels.SensitiveHash("v1", "1".repeat(64)))));

        var json = p4c2(62, "JSON", schema, false, false);
        assertCode("QUERY_FIELD_UNAVAILABLE", () -> compiler.compile(parser.parse(queryJson(
                "JSON", "DECLARED_PATH_EXISTS", "\"999\"", "[]")), List.of(json)));
        assertCode("QUERY_INVALID", () -> compiler.compile(parser.parse(queryJson(
                "GEO", "WITHIN_BOX", "{\"south\":40,\"west\":116,\"north\":39,\"east\":117}", "[]")),
                List.of(p4c2(63, "GEO", schema, false, false))));
    }

    @Test
    void compilesP4C3RelationAndDeclaredSubtableAggregateWithoutClientTraversal() {
        var relation = new QueryField(71, "tested", "RELATION", false, true, false,
                JsonNodeFactory.instance.objectNode());
        var relationQuery = compiler.compile(parser.parse(queryJson(
                "RELATION", "HAS_ANY", "[\"101\",\"102\"]", "[]")), List.of(relation));
        assertThat(relationQuery.predicateSql())
                .contains("un_module_record_relation", "source_logical_field_id=71", "target_record_id IN (?,?)");
        assertThat(relationQuery.arguments()).containsExactly("101", "102");

        var subtableSchema = JsonNodeFactory.instance.objectNode();
        subtableSchema.putArray("aggregates").addObject()
                .put("id", "total_amount").put("function", "SUM").put("columnFieldId", "801");
        var subtable = new QueryField(72, "tested", "SUBTABLE", false, true, false, subtableSchema);
        var aggregateQuery = compiler.compile(parser.parse(queryJson(
                "SUBTABLE", "DECLARED_AGGREGATE",
                "{\"aggregateId\":\"total_amount\",\"operator\":\"GT\",\"value\":10}", "[]")),
                List.of(subtable));
        assertThat(aggregateQuery.predicateSql())
                .contains("SUM(sv.decimal_value)", "parent_field_snapshot_id=72", "sv.source_field_id=801", ">?")
                .doesNotContain("total_amount");
        assertThat((java.math.BigDecimal) aggregateQuery.arguments().getFirst()).isEqualByComparingTo("10");
    }

    private static String queryJson(String type, String operator, String value, String sort) {
        return """
                {"schemaVersionId":"1","page":1,"size":50,"recordScope":"active","q":null,
                 "filter":{"kind":"PREDICATE","fieldCode":"tested","operator":"%s","value":%s},
                 "sort":%s,"columns":[],"viewId":null}
                """.formatted(operator, value, sort);
    }

    private static String value(String type, String operator) {
        if ("EMPTY".equals(operator)) return "true";
        return switch (type) {
            case "PERCENT", "PROGRESS" -> "BETWEEN".equals(operator) ? "[10,20]" : "10";
            case "RATING" -> "BETWEEN".equals(operator) ? "[2,4]" : "3";
            case "MONEY" -> "BETWEEN".equals(operator)
                    ? "[{\"amount\":\"9.00\",\"currency\":\"CNY\"},{\"amount\":\"10.00\",\"currency\":\"CNY\"}]"
                    : "{\"amount\":\"10.00\",\"currency\":\"CNY\"}";
            case "DATE_RANGE" -> "OVERLAPS".equals(operator)
                    ? "[\"2026-01-01\",\"2026-01-31\"]" : "\"2026-01-15\"";
            case "TIME" -> "BETWEEN".equals(operator)
                    ? "[\"09:00:00\",\"18:00:00\"]" : "\"12:00:00\"";
            case "TIME_RANGE" -> "OVERLAPS".equals(operator)
                    ? "[\"09:00:00\",\"18:00:00\"]" : "\"12:00:00\"";
            case "MULTI_SELECT" -> "[\"101\",\"102\"]";
            case "CASCADE" -> "\"101\"";
            case "SWITCH" -> "true";
            case "TAG" -> "[\"red\",\"blue\"]";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static QueryField p4c2(
            long id,
            String type,
            com.fasterxml.jackson.databind.node.ObjectNode schema,
            boolean sensitiveQueryable,
            boolean sortable
    ) {
        return new QueryField(id, "tested", type, "RICH_TEXT".equals(type), true, sortable,
                sensitiveQueryable, schema);
    }

    private static String p4c2Value(String type, String operator) {
        if ("EMPTY".equals(operator)) return "true";
        return switch (type) {
            case "PHONE" -> "\"+8613812345678\"";
            case "EMAIL" -> "\"Owner@example.com\"";
            case "URL" -> "\"https://example.com/\"";
            case "IDENTITY" -> "\"110101199001011237\"";
            case "ADDRESS" -> "EQ_REGION".equals(operator)
                    ? "{\"countryCode\":\"CN\",\"regionCode\":\"BJ\"}" : "\"Beijing\"";
            case "GEO" -> "WITHIN_BOX".equals(operator)
                    ? "{\"south\":39,\"west\":116,\"north\":40,\"east\":117}"
                    : "{\"lat\":39.9,\"lng\":116.4,\"radiusMeters\":5000}";
            case "BARCODE" -> "\"EAN13:4006381333931\"";
            case "RICH_TEXT" -> "\"hello world\"";
            case "JSON" -> "DECLARED_PATH_EXISTS".equals(operator)
                    ? "\"101\"" : "{\"pathSnapshotId\":\"101\",\"value\":\"P4-C2\"}";
            case "SECRET" -> "\"exact secret\"";
            case "STATUS" -> "IN".equals(operator) ? "[\"101\",\"102\"]" : "\"101\"";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String p4c2Sort(String type) {
        return Set.of("BARCODE", "STATUS").contains(type)
                ? "[{\"fieldCode\":\"tested\",\"direction\":\"ASC\",\"nulls\":\"LAST\"}]" : "[]";
    }

    private static void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
