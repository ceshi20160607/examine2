package com.unique.examine.module.report.adapter.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcReportRepositoryContractTest {
    @Test
    void draftSaveUsesTenantScopedImmutableIdentityAndDualCasFacts() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var revised = expected.reviseDraft(
                "Orders v2", "Current orders",
                new ReportDraft(501, List.of("amount", "title")),
                expected.updatedAt().plusSeconds(1));

        assertThat(repository.saveDraft(expected, revised)).isEqualTo(revised);

        var call = jdbc.updates.getFirst();
        assertThat(normalize(call.sql()))
                .contains("where system_id=? and tenant_id=? and id=?")
                .contains("report_code=?")
                .contains("draft_version=? and version=?")
                .contains("active_version_id <=> ?")
                .contains("active_version_no <=> ?")
                .doesNotContain("set report_code=");
        assertThat(call.arguments())
                .contains(revised.draftVersion(), revised.version(),
                        expected.systemId(), expected.tenantId(), expected.id(),
                        expected.code(), expected.draftVersion(),
                        expected.version());
    }

    @Test
    void publishAtomicallyInsertsVersionOrderedPinsThenAdvancesPointer() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var version = version(expected, 200, 1);
        var activated = expected.activate(version, version.publishedAt());

        assertThat(repository.publish(expected, activated, version))
                .isEqualTo(version);

        assertThat(jdbc.updates).hasSize(2);
        var insert = jdbc.updates.getFirst();
        assertThat(normalize(insert.sql()))
                .startsWith("insert into un_module_report_version")
                .contains("source_draft_version")
                .contains("select source.module_id")
                .contains("source.system_id=? and source.tenant_id=?")
                .contains("source.data_source_id=? and source.id=?")
                .contains("source.version_no=?")
                .contains("source.module_id=?")
                .contains("snapshot_json", "snapshot_fingerprint");
        assertThat(insert.arguments()).hasSize(27);
        assertThat(insert.arguments()[5]).isEqualTo(expected.draftVersion());
        assertThat(insert.arguments()[19]).isEqualTo(701L);
        assertThat(insert.arguments()[22]).isEqualTo(2);

        assertThat(jdbc.batches).hasSize(1);
        var fields = jdbc.batches.getFirst();
        assertThat(normalize(fields.sql()))
                .startsWith("insert into un_module_report_version_field")
                .contains("field_ordinal", "field_id", "field_code",
                        "field_name", "field_type", "query_type");
        assertThat(fields.arguments()).hasSize(2);
        assertThat(fields.arguments().getFirst()[6]).isEqualTo(0);
        assertThat(fields.arguments().getFirst()[8]).isEqualTo("title");
        assertThat(fields.arguments().get(1)[6]).isEqualTo(1);
        assertThat(fields.arguments().get(1)[8]).isEqualTo("amount");

        assertThat(normalize(jdbc.updates.get(1).sql()))
                .startsWith("update un_module_report")
                .contains("set active_version_id=?,active_version_no=?")
                .contains("where system_id=? and tenant_id=? and id=?")
                .contains("draft_version=? and version=?")
                .contains("active_version_id <=> ?")
                .contains("active_version_no <=> ?");
    }

    @Test
    void staleDraftCasFailsWithoutReturningFalseSuccess() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.affected = 0;
        var repository = repository(jdbc);
        var expected = root();
        var revised = expected.reviseDraft(
                "Orders v2", null,
                new ReportDraft(501, List.of("title", "amount")),
                expected.updatedAt().plusSeconds(1));

        assertThatThrownBy(() -> repository.saveDraft(expected, revised))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_VERSION_CONFLICT"));
    }

    @Test
    void readsAndHistoryAreAlwaysTenantScopedAndStablyOrdered() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);

        assertThat(repository.findById(10, 20, 100)).isEmpty();
        assertThat(repository.findByCode(10, 20, "orders")).isEmpty();
        assertThat(repository.findAll(10, 20)).isEmpty();
        assertThat(repository.findActiveVersion(10, 20, 100)).isEmpty();
        assertThat(repository.findVersion(10, 20, 100, 1)).isEmpty();
        assertThat(repository.findVersionById(10, 20, 100, 200)).isEmpty();
        assertThat(repository.findVersions(10, 20, 100)).isEmpty();

        assertThat(jdbc.queries)
                .allSatisfy(call -> assertThat(normalize(call.sql()))
                        .contains("system_id=?", "tenant_id=?"));
        assertThat(normalize(jdbc.queries.get(2).sql()))
                .contains("order by updated_at desc,id desc");
        assertThat(normalize(jdbc.queries.get(3).sql()))
                .contains("join un_module_report_version version_row")
                .contains("version_row.system_id=root.system_id")
                .contains("version_row.tenant_id=root.tenant_id")
                .contains("version_row.id=root.active_version_id")
                .contains("version_row.version_no=root.active_version_no");
        assertThat(normalize(jdbc.queries.getLast().sql()))
                .contains("order by version_no desc,id desc");
    }

    @Test
    void persistedFieldRowsRoundTripInTheirPinnedOrdinalOrder() {
        var jdbc = new HydratingJdbcTemplate();
        var repository = repository(jdbc);

        var restored = repository.findVersion(10, 20, 100, 2)
                .orElseThrow();

        assertThat(restored.id()).isEqualTo(202);
        assertThat(restored.source().moduleId()).isEqualTo(701);
        assertThat(restored.source().fields())
                .extracting(ReportFieldPin::code)
                .containsExactly("amount", "title");
        assertThat(restored.source().fields().getFirst())
                .isEqualTo(new ReportFieldPin(
                        902, "amount", "Amount", "DECIMAL", "DECIMAL"));
        assertThat(jdbc.queries).hasSize(2);
        assertThat(normalize(jdbc.queries.get(1).sql()))
                .contains("where system_id=? and tenant_id=? and report_id=?")
                .contains("report_version_id=?")
                .contains("order by report_version_no desc,field_ordinal asc,id asc");
    }

    private static JdbcReportRepository repository(JdbcTemplate jdbc) {
        var sequence = new AtomicLong(900);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        return new JdbcReportRepository(
                jdbc, new ObjectMapper().findAndRegisterModules(), ids,
                transactions());
    }

    private static ReportDefinition root() {
        return ReportDefinition.create(
                100, 10, 20, "orders", "Orders", null,
                new ReportDraft(501, List.of("title", "amount")),
                Instant.parse("2026-08-03T00:00:00Z"));
    }

    private static ReportVersion version(
            ReportDefinition root, long id, int versionNumber
    ) {
        return new ReportVersion(
                id, root.id(), root.systemId(), root.tenantId(), versionNumber,
                root.draftVersion(), root.code(), root.name(),
                root.description(), new ReportSourcePin(
                501, "open_orders", "Open orders", 601, 3, 701,
                "orders", "schema-7", List.of(
                new ReportFieldPin(
                        901, "title", "Title", "TEXT", "TEXT"),
                new ReportFieldPin(
                        902, "amount", "Amount", "DECIMAL", "DECIMAL"))),
                "a".repeat(64), 30,
                root.updatedAt().plusSeconds(1));
    }

    private static PlatformTransactionManager transactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(
                    TransactionDefinition definition
            ) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static ResultSet row(Map<String, Object> values) {
        return (ResultSet) Proxy.newProxyInstance(
                JdbcReportRepositoryContractTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    var name = method.getName();
                    if ("getString".equals(name)) {
                        var value = values.get(arguments[0]);
                        return value == null ? null : value.toString();
                    }
                    if ("getLong".equals(name)) {
                        var value = (Number) values.get(arguments[0]);
                        return value == null ? 0L : value.longValue();
                    }
                    if ("getInt".equals(name)) {
                        var value = (Number) values.get(arguments[0]);
                        return value == null ? 0 : value.intValue();
                    }
                    if ("getTimestamp".equals(name)) {
                        return values.get(arguments[0]);
                    }
                    if ("getObject".equals(name)) {
                        return values.get(arguments[0]);
                    }
                    if ("wasNull".equals(name)) {
                        return false;
                    }
                    var returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == float.class) return 0F;
                    if (returnType == double.class) return 0D;
                    return null;
                });
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        final List<Call> updates = new ArrayList<>();
        final List<Call> queries = new ArrayList<>();
        final List<BatchCall> batches = new ArrayList<>();
        int affected = 1;

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            return affected;
        }

        @Override
        public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
            batches.add(new BatchCall(sql, batchArgs));
            return batchArgs.stream().mapToInt(ignored -> affected).toArray();
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> rowMapper, Object... arguments
        ) {
            queries.add(new Call(sql, arguments));
            return List.of();
        }
    }

    private static final class HydratingJdbcTemplate
            extends RecordingJdbcTemplate {
        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> mapper, Object... arguments
        ) {
            queries.add(new Call(sql, arguments));
            try {
                var normalized = normalize(sql);
                if (normalized.contains("from un_module_report_version_field")) {
                    return List.of(
                            mapper.mapRow(row(field(
                                    1, 0, 902, "amount", "Amount",
                                    "DECIMAL", "DECIMAL")), 0),
                            mapper.mapRow(row(field(
                                    2, 1, 901, "title", "Title",
                                    "TEXT", "TEXT")), 1));
                }
                return List.of(mapper.mapRow(row(versionRow()), 0));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static Map<String, Object> versionRow() {
            var values = new LinkedHashMap<String, Object>();
            values.put("id", 202L);
            values.put("report_id", 100L);
            values.put("system_id", 10L);
            values.put("tenant_id", 20L);
            values.put("version_no", 2);
            values.put("source_draft_version", 4L);
            values.put("report_code", "orders");
            values.put("report_name", "Orders");
            values.put("description", null);
            values.put("data_source_id", 501L);
            values.put("data_source_code", "open_orders");
            values.put("data_source_name", "Open orders");
            values.put("data_source_version_id", 601L);
            values.put("data_source_version_no", 3);
            values.put("module_id", 701L);
            values.put("module_code", "orders");
            values.put("schema_version_id", "schema-7");
            values.put("snapshot_fingerprint", "b".repeat(64));
            values.put("published_by_member_id", 30L);
            values.put("published_at", Timestamp.from(
                    Instant.parse("2026-08-03T01:00:00Z")));
            return values;
        }

        private static Map<String, Object> field(
                long id,
                int ordinal,
                long fieldId,
                String code,
                String name,
                String type,
                String queryType
        ) {
            var values = new LinkedHashMap<String, Object>();
            values.put("id", id);
            values.put("report_version_id", 202L);
            values.put("report_id", 100L);
            values.put("system_id", 10L);
            values.put("tenant_id", 20L);
            values.put("report_version_no", 2);
            values.put("field_ordinal", ordinal);
            values.put("field_id", fieldId);
            values.put("field_code", code);
            values.put("field_name", name);
            values.put("field_type", type);
            values.put("query_type", queryType);
            return values;
        }
    }

    private record Call(String sql, Object[] arguments) {
    }

    private record BatchCall(String sql, List<Object[]> arguments) {
    }
}
