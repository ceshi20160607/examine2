package com.unique.examine.module.datasource.adapter.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog.FieldCapability;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDataSourceModuleCatalogTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JdbcDataSourceModuleCatalog catalog =
            new JdbcDataSourceModuleCatalog(propertyJdbc(), json);

    @Test
    void catalogQueryScopesTenantUsesActiveSchemaAndHasStableDisplayOrder() {
        var sql = normalize(
                JdbcDataSourceModuleCatalog.CATALOG_SQL);

        assertThat(sql)
                .contains("tenant_row.system_id=config_root.system_id")
                .contains("tenant_row.id=?")
                .contains("config_root.system_id=?")
                .contains("module_row.schema_version_id=config_root.active_version_id")
                .contains("json_table(")
                .contains("field_row.logical_field_id")
                .contains("field_row.result_schema")
                .contains("parent_field.field_code as parent_field_code")
                .contains("parent_field.field_snapshot_id=field_row.parent_field_snapshot_id")
                .contains("reference_target.field_type as reference_target_type")
                .contains("json_extract(field_row.property_json,'$.targetfieldid')")
                .contains("published_field.is_filterable")
                .contains("published_field.index_mode")
                .contains("config_root.active_version_id is not null")
                .doesNotContain("un_module_record");
        assertThat(normalize(JdbcDataSourceModuleCatalog.FIELD_PROPERTY_SQL))
                .contains("tenant_row.id=?")
                .contains("module_row.logical_module_id=?")
                .contains("field_row.field_code=?")
                .contains("config_root.system_id=?")
                .contains("config_root.active_version_id is not null")
                .doesNotContain("un_module_record");
    }

    @Test
    void mapsPublishedNamesNativeOperatorsAndStableFieldCapabilities() {
        var modules = JdbcDataSourceModuleCatalog.modules(List.of(
                new JdbcDataSourceModuleCatalog.CatalogRow(
                        10, "orders", "Orders", "501",
                        "createdAt", "Created at", "DATETIME", "RECORD",
                        true, "SORT"),
                new JdbcDataSourceModuleCatalog.CatalogRow(
                        10, "orders", "Orders", "501",
                        "line", "Record line", "TEXT", "RECORD",
                        true, "FILTER"),
                new JdbcDataSourceModuleCatalog.CatalogRow(
                        10, "orders", "Orders", "501",
                        -91, "line", "Line", "TEXT", "SUBTABLE_COLUMN",
                        null, "lines", "Lines", null, true, "SORT"),
                new JdbcDataSourceModuleCatalog.CatalogRow(
                        20, "people", "People", "501",
                        null, null, null, null, false, null)));

        assertThat(modules)
                .extracting(value -> value.moduleCode() + ":" + value.moduleName())
                .containsExactly("orders:Orders", "people:People");
        assertThat(modules.getFirst().fields())
                .extracting(field -> field.code() + ":" + field.fieldName())
                .containsExactly(
                        "createdAt:Created at", "line:Record line",
                        "lines.line:Lines / Line");
        assertThat(modules.getFirst().fields().getFirst().operators())
                .containsExactlyInAnyOrder("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY");
        assertThat(modules.getFirst().fields().getFirst().sortable()).isTrue();
        assertThat(modules.getFirst().fields().getFirst().temporal()).isTrue();
        assertThat(modules.getFirst().fields().getFirst().logicalFieldId())
                .isPositive();
        assertThat(modules.getFirst().fields().getFirst().queryType())
                .isEqualTo("DATETIME");
        assertThat(modules.getFirst().fields().get(1).operators())
                .containsExactlyInAnyOrder("EQ", "CONTAINS", "PREFIX", "EMPTY");
        assertThat(modules.getFirst().fields().get(1).sortable()).isFalse();
        assertThat(modules.getFirst().fields().get(2).available()).isFalse();
        assertThat(modules.getFirst().fields().get(2).logicalFieldId())
                .isEqualTo(-91);
        assertThat(modules.getFirst().fields().get(2).operators()).isEmpty();
    }

    @Test
    void mapsReferenceDerivedAndSystemFieldsToTheirEffectiveQueryTypes() {
        var modules = JdbcDataSourceModuleCatalog.modules(List.of(
                row(101, "customerNumber", "Customer number", "REFERENCE",
                        null, "NUMBER", true, "STATISTIC"),
                row(102, "calculatedTotal", "Calculated total", "CALCULATED",
                        "DECIMAL", null, true, "SORT"),
                row(103, "lookupDate", "Lookup date", "LOOKUP",
                        "DATE", null, true, "SORT"),
                row(104, "createdAt", "Created at", "CREATED_AT",
                        null, null, true, "SORT"),
                row(105, "createdBy", "Created by", "CREATED_BY",
                        null, null, true, "SORT")));

        var fields = modules.getFirst().fields();
        assertThat(fields)
                .extracting(FieldCapability::logicalFieldId)
                .containsExactly(101L, 102L, 103L, 104L, 105L);
        assertThat(fields)
                .extracting(field -> field.type() + ":" + field.queryType())
                .containsExactly(
                        "REFERENCE:NUMBER", "CALCULATED:NUMBER", "LOOKUP:DATE",
                        "CREATED_AT:DATETIME", "CREATED_BY:MEMBER");
        assertThat(fields.get(0).operators()).contains("BETWEEN");
        assertThat(fields.get(0).sortable()).isTrue();
        assertThat(fields.get(2).operators()).contains("BEFORE", "AFTER");
        assertThat(fields.get(2).temporal()).isTrue();
        assertThat(fields.get(3).temporal()).isTrue();
        assertThat(fields.get(4).operators()).contains("HAS_ANY");
    }

    @Test
    void canonicalizesPublishedDerivedFieldsByEffectiveQueryType() throws Exception {
        var derived = new FieldCapability(
                101, "total", "Total", "CALCULATED", "NUMBER",
                Set.of("EQ"), true, false, true);

        var result = catalog.canonicalizeFilterValue(
                10, 20, 30, derived, "EQ", "007.500");

        assertThat(result.valid()).isTrue();
        assertThat(json.readTree(result.canonicalValue()).decimalValue())
                .isEqualByComparingTo("7.5");
    }

    @Test
    void canonicalizesEveryScalarAsRuntimeReadableTypedJson() throws Exception {
        assertThat(canonical("NUMBER", "EQ", "007.500").decimalValue())
                .isEqualByComparingTo("7.5");
        assertThat(canonical("SWITCH", "EQ", "FALSE").isBoolean()).isTrue();
        assertThat(canonical("SWITCH", "EQ", "FALSE").booleanValue()).isFalse();
        assertText("DATE", "EQ", "\"2026-08-01\"", "2026-08-01");
        assertText("DATETIME", "EQ", "\"2026-08-01T09:30:00\"",
                "2026-08-01T09:30");
        assertText("TIME", "EQ", "\"09:30:00\"", "09:30:00");
        assertText("RADIO", "EQ", "\"42\"", "42");
        assertText("TEXT", "EQ", "\"  title  \"", "title");

        assertThat(result("PERCENT", "EQ", "100.00001").valid()).isFalse();
        assertThat(result("SWITCH", "EQ", "yes").valid()).isFalse();
        assertThat(result("TIME", "EQ", "\"25:00:00\"").valid()).isFalse();
    }

    @Test
    void canonicalizesCollectionsRangesMoneyAndStructuredOperators()
            throws Exception {
        var members = canonical("MEMBER", "HAS_ANY", "[\"20\",\"10\"]");
        assertThat(members.isArray()).isTrue();
        assertThat(members).extracting(JsonNode::asText)
                .containsExactly("10", "20");

        var between = canonical("NUMBER", "BETWEEN", "[1.00,9.500]");
        assertThat(between.get(0).decimalValue()).isEqualByComparingTo("1");
        assertThat(between.get(1).decimalValue()).isEqualByComparingTo("9.5");

        var money = canonical("MONEY", "EQ",
                "{\"currency\":\"usd\",\"amount\":\"10.00\"}");
        assertThat(money.path("currency").textValue()).isEqualTo("USD");
        assertThat(money.path("amount").textValue()).isEqualTo("10.00");

        var address = canonical("ADDRESS", "EQ_REGION",
                "{\"regionCode\":\"CA\",\"countryCode\":\"us\"}");
        assertThat(address.path("countryCode").textValue()).isEqualTo("US");
        assertThat(address.path("regionCode").textValue()).isEqualTo("CA");

        var geo = canonical("GEO", "WITHIN_BOX",
                "{\"west\":-120,\"south\":30,\"east\":-110,\"north\":40}");
        assertThat(geo.isObject()).isTrue();
        assertThat(geo.path("south").isNumber()).isTrue();

        var declared = canonical("JSON", "DECLARED_PATH_EQ",
                "{\"value\":7,\"pathSnapshotId\":\"81\"}");
        assertThat(declared.isObject()).isTrue();
        assertThat(declared.path("pathSnapshotId").textValue()).isEqualTo("81");
        assertThat(declared.path("value").isNumber()).isTrue();

        assertThat(result("NUMBER", "BETWEEN", "[9,1]").valid()).isFalse();
        assertThat(result("MONEY", "EQ",
                "{\"currency\":\"USD\",\"amount\":\"10.0\"}").valid())
                .isFalse();
        assertThat(result("GEO", "WITHIN_BOX",
                "{\"south\":40,\"west\":-120,\"north\":30,\"east\":-110}")
                .valid()).isFalse();
    }

    private void assertText(
            String type,
            String operator,
            String supplied,
            String expected
    ) throws Exception {
        var node = canonical(type, operator, supplied);
        assertThat(node.isTextual()).isTrue();
        assertThat(node.textValue()).isEqualTo(expected);
    }

    private JsonNode canonical(String type, String operator, String supplied)
            throws Exception {
        var result = result(type, operator, supplied);
        assertThat(result.valid()).isTrue();
        return json.readTree(result.canonicalValue());
    }

    private com.unique.examine.module.datasource.port.DataSourceModuleCatalog
            .CanonicalFilterValue result(
            String type,
            String operator,
            String supplied
    ) {
        return catalog.canonicalizeFilterValue(
                10, 20, 30, field(type, operator), operator, supplied);
    }

    private static FieldCapability field(String type, String operator) {
        return new FieldCapability(
                "field", "Field", type, Set.of(operator), false, false, true);
    }

    private static JdbcDataSourceModuleCatalog.CatalogRow row(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String resultSchema,
            String referenceTargetType,
            boolean filterable,
            String indexMode
    ) {
        return new JdbcDataSourceModuleCatalog.CatalogRow(
                10, "orders", "Orders", "501", logicalFieldId,
                code, name, type, "RECORD", resultSchema,
                null, null, referenceTargetType, filterable, indexMode);
    }

    private static JdbcTemplate propertyJdbc() {
        return new JdbcTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> query(
                    String sql,
                    RowMapper<T> rowMapper,
                    Object... arguments
            ) {
                return (List<T>) List.of("""
                        {"currencies":["USD"],
                         "queryPaths":[{"pathSnapshotId":"81","type":"DECIMAL"}],
                         "aggregates":[{"id":"sum","function":"SUM"}]}
                        """);
            }
        };
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
