package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.query.RecordQueryCompiler;
import com.unique.examine.module.runtime.query.RecordQueryParser;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordRuntimeServiceHistoricalAggregateTest {
    @Test
    void historicalRelationQueryUsesPinnedMetadataAndAllowsAnUpgradedTargetRecord() {
        var jdbc = new HistoricalJdbc(false);
        var records = service(jdbc);

        var plan = records.prepareActiveAggregate(
                session(true), "orders_v1", "40", query());

        assertThat(plan.schemaVersionId()).isEqualTo(40);
        assertThat(plan.moduleSnapshotId()).isEqualTo(31);
        assertThat(plan.logicalModuleId()).isEqualTo(30);
        assertThat(plan.field("customer"))
                .satisfies(field -> {
                    assertThat(field.logicalFieldId()).isEqualTo(11);
                    assertThat(field.queryType()).isEqualTo("RELATION");
                });
        assertThat(plan.whereArguments())
                .containsExactly(10L, 20L, 30L, 40L, 31L,
                        "ACTIVE", 2L, "900");
        assertThat(jdbc.visibilityArguments)
                .containsExactly(10L, 20L, 50L, 900L, 2L);
        assertThat(jdbc.visibilitySql)
                .contains("r.logical_module_id=?", "r.status='ACTIVE'")
                .doesNotContain("r.schema_version_id", "r.module_snapshot_id");
        assertThat(jdbc.activeVersionQueries).isZero();
    }

    @Test
    void historicalFieldReadPermissionIsAppliedFromThePinnedDeclaration() {
        var records = service(new HistoricalJdbc(false));

        assertThatThrownBy(() -> records.prepareActiveAggregate(
                session(false), "orders_v1", "40", query()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("QUERY_FIELD_UNAVAILABLE"));
    }

    @Test
    void missingHistoricalTargetProjectionFailsClosedWithoutActiveFallback() {
        var jdbc = new HistoricalJdbc(true);
        var records = service(jdbc);

        assertThatThrownBy(() -> records.prepareActiveAggregate(
                session(true), "orders_v1", "40", query()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("FIELD_RUNTIME_UNAVAILABLE"));
        assertThat(jdbc.activeVersionQueries).isZero();
    }

    private static RecordRuntimeService service(HistoricalJdbc jdbc) {
        RuntimeAuthorizationFacade authorization =
                new RuntimeAuthorizationFacade() {
                    @Override
                    public RuntimeGrant resolve(
                            RuntimeAuthorizationRequest request
                    ) {
                        return new RuntimeGrant(
                                false, 7, false, Set.of(2L),
                                Set.of(), List.of());
                    }

                    @Override
                    public long currentSystemEpoch(long systemId) {
                        return 7;
                    }
                };
        return new RecordRuntimeService(
                jdbc, new ObjectMapper(), authorization,
                null, null, null, null, null,
                new RecordQueryParser(), new RecordQueryCompiler(),
                null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static RuntimeSession session(boolean fieldRead) {
        var permissions = fieldRead
                ? Set.of("module.orders_v1.view",
                "module.orders_v1.field.customer.read",
                "module.customers_v1.view")
                : Set.of("module.orders_v1.view",
                "module.customers_v1.view");
        return new RuntimeSession(1, 10, 2, 20L, permissions);
    }

    private static String query() {
        return """
                {"schemaVersionId":"40","page":1,"size":1,
                 "recordScope":"active","q":null,
                 "filter":{"kind":"PREDICATE","fieldCode":"customer",
                 "operator":"HAS_ANY","value":["900"]},
                 "sort":[],"columns":[],"viewId":null}
                """;
    }

    private static final class HistoricalJdbc extends JdbcTemplate {
        private final boolean omitTargetProjection;
        private int activeVersionQueries;
        private List<Object> visibilityArguments = List.of();
        private String visibilitySql;

        private HistoricalJdbc(boolean omitTargetProjection) {
            this.omitTargetProjection = omitTargetProjection;
        }

        @Override
        public <T> List<T> query(
                String sql,
                RowMapper<T> mapper,
                Object... arguments
        ) {
            if (sql.contains("un_module_config_root")) {
                activeVersionQueries++;
                throw new AssertionError(
                        "Historical execution must not load the active schema");
            }
            if (sql.contains("FROM un_module_config_version")) {
                return one(mapper, Map.of(
                        "id", 40L,
                        "snapshot_checksum", "a".repeat(64),
                        "snapshot_json", snapshot()));
            }
            if (sql.contains("SELECT module_code FROM "
                    + "un_module_runtime_schema_module")) {
                return one(mapper, Map.of(
                        "module_code", "customers_v1"));
            }
            if (sql.contains("SELECT module_snapshot_id,logical_module_id")) {
                var code = String.valueOf(arguments[2]);
                if (omitTargetProjection && "customers_v1".equals(code)) {
                    return List.of();
                }
                return "orders_v1".equals(code)
                        ? one(mapper, Map.of(
                        "module_snapshot_id", 31L,
                        "logical_module_id", 30L))
                        : one(mapper, Map.of(
                        "module_snapshot_id", 32L,
                        "logical_module_id", 50L));
            }
            if (sql.contains("FROM un_module_runtime_schema_field")) {
                var snapshotId = ((Number) arguments[2]).longValue();
                return snapshotId == 31
                        ? one(mapper, Map.of(
                        "logical_field_id", 11L,
                        "field_code", "customer",
                        "field_type", "RELATION"))
                        : one(mapper, Map.of(
                        "logical_field_id", 51L,
                        "field_code", "name",
                        "field_type", "TEXT"));
            }
            throw new AssertionError("Unexpected historical query: " + sql);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(
                String sql,
                Class<T> requiredType,
                Object... arguments
        ) {
            if (sql.contains("Relation query target")
                    || !sql.contains("FROM un_module_record r")) {
                throw new AssertionError("Unexpected scalar query: " + sql);
            }
            visibilitySql = sql;
            visibilityArguments = List.of(arguments);
            return (T) Long.valueOf(1);
        }

        private static <T> List<T> one(
                RowMapper<T> mapper,
                Map<String, Object> values
        ) {
            var result = (ResultSet) Proxy.newProxyInstance(
                    RecordRuntimeServiceHistoricalAggregateTest.class
                            .getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> {
                        if ((method.getName().equals("getLong")
                                || method.getName().equals("getString"))
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof String column) {
                            var value = values.get(column);
                            if (method.getName().equals("getLong")) {
                                return value == null
                                        ? 0L : ((Number) value).longValue();
                            }
                            return value == null ? null : value.toString();
                        }
                        if (method.getName().equals("wasNull")) {
                            return false;
                        }
                        if (method.getReturnType().equals(boolean.class)) {
                            return false;
                        }
                        if (method.getReturnType().equals(int.class)) {
                            return 0;
                        }
                        if (method.getReturnType().equals(long.class)) {
                            return 0L;
                        }
                        return null;
                    });
            try {
                return List.of(mapper.mapRow(result, 0));
            } catch (SQLException exception) {
                throw new AssertionError(exception);
            }
        }

        private static String snapshot() {
            var root = new LinkedHashMap<String, Object>();
            root.put("modules", List.of(
                    module("30", "orders_v1", "Orders v1"),
                    module("50", "customers_v1", "Customers v1")));
            root.put("fields", List.of(
                    field("11", "30", "customer", "Customer", "RELATION", "50"),
                    field("51", "50", "name", "Name", "TEXT", null)));
            root.put("permissions", List.of(Map.of(
                    "id", "71", "resource_type", "FIELD",
                    "resource_id", "11",
                    "permission_code", "module.orders_v1.field.customer.read",
                    "desired_status", "ENABLED")));
            try {
                return new ObjectMapper().writeValueAsString(root);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        private static Map<String, Object> module(
                String id,
                String code,
                String name
        ) {
            return Map.of(
                    "id", id, "module_code", code,
                    "module_name", name, "sort_order", 1,
                    "desired_status", "ENABLED");
        }

        private static Map<String, Object> field(
                String id,
                String moduleId,
                String code,
                String name,
                String type,
                String targetModuleId
        ) {
            var field = new LinkedHashMap<String, Object>();
            field.put("id", id);
            field.put("module_id", moduleId);
            field.put("field_code", code);
            field.put("field_name", name);
            field.put("field_type", type);
            field.put("desired_status", "ENABLED");
            field.put("sort_order", 1);
            field.put("index_mode", "STATISTIC");
            field.put("is_hidden", false);
            field.put("is_required", false);
            field.put("is_readonly", false);
            field.put("is_filterable", true);
            field.put("is_searchable", false);
            field.put("show_in_list", true);
            field.put("show_in_detail", true);
            field.put("property_json", Map.of());
            if (targetModuleId != null) {
                field.put("target_module_id", targetModuleId);
            }
            return Map.copyOf(field);
        }
    }
}
