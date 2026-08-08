package com.unique.examine.module.report.exporting.adapter.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.exporting.ReportExportStore;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcReportExportStoreContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDateTime CREATED =
            LocalDateTime.parse("2026-08-04T01:00:00");

    @Test
    void insertPersistsExactPinsAndOrderedFieldsAtQueuedVersionZero()
            throws Exception {
        var jdbc = new RecordingJdbcTemplate();
        var store = new JdbcReportExportStore(jdbc, JSON);
        var run = queued();

        store.insert(run);

        var call = jdbc.updates.getFirst();
        assertThat(normalize(call.sql()))
                .startsWith("insert into un_module_report_export_run")
                .contains("report_version_id", "report_version_no")
                .contains("data_source_version_id", "data_source_version_no")
                .contains("fields_json", "fields_fingerprint", "field_count")
                .contains("requested_by_account_id", "requested_by_member_id")
                .contains("total_rows", "processed_rows", "truncated")
                .contains("result_content", "result_size");
        assertThat(call.arguments()).hasSize(39);
        assertThat(call.sql().chars().filter(value -> value == '?').count())
                .isEqualTo(call.arguments().length);
        assertThat(call.arguments()[4]).isEqualTo(run.reportVersionId());
        assertThat(call.arguments()[13]).isEqualTo(run.source().moduleId());
        assertThat(call.arguments()[17].toString())
                .matches("^[0-9a-f]{64}$");
        assertThat(call.arguments()[18]).isEqualTo(2);
        assertThat(call.arguments()[19]).isEqualTo(run.requestKeyHash());
        assertThat(call.arguments()[23]).isEqualTo("QUEUED");
        assertThat(call.arguments()[38]).isEqualTo(0L);

        var restoredFields = JSON.readValue(
                call.arguments()[16].toString(),
                new TypeReference<List<ReportFieldPin>>() {
                });
        assertThat(restoredFields).isEqualTo(run.source().fields());
        assertThat(restoredFields).extracting(ReportFieldPin::code)
                .containsExactly("amount", "title");
    }

    @Test
    void exactInsertReplayIsANoOpButConflictingRunFailsClosed() {
        var jdbc = new StatefulJdbcTemplate(queued());
        jdbc.duplicateInsert = true;
        var store = new JdbcReportExportStore(jdbc, JSON);

        store.insert(queued());

        var conflict = copy(
                queued(), 999, ReportExportStore.Status.QUEUED,
                null, 0, false, null, null, null, null,
                null, null, CREATED, CREATED, 0);
        assertThatThrownBy(() -> store.insert(conflict))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_EXPORT_CONFLICT"));
        assertThat(jdbc.queries).allSatisfy(call ->
                assertThat(normalize(call.sql()))
                        .contains("system_id=? and tenant_id=?")
                        .contains("requested_by_member_id=? and report_id=?")
                        .contains("request_key_hash=?"));
    }

    @Test
    void stateCasSupportsStartRequeueRetryCompleteAndScopedResult() {
        var jdbc = new StatefulJdbcTemplate(queued());
        var store = new JdbcReportExportStore(jdbc, JSON);
        var firstStart = CREATED.plusSeconds(1);

        var running = store.start(1000, 0, firstStart);
        assertThat(running.status()).isEqualTo(ReportExportStore.Status.RUNNING);
        assertThat(running.startedAt()).isEqualTo(firstStart);
        assertThat(running.version()).isEqualTo(1);

        var queuedAgain = store.requeue(1000, 1, CREATED.plusSeconds(2));
        assertThat(queuedAgain.status()).isEqualTo(ReportExportStore.Status.QUEUED);
        assertThat(queuedAgain.startedAt()).isEqualTo(firstStart);
        assertThat(queuedAgain.version()).isEqualTo(2);

        var runningAgain = store.start(1000, 2, CREATED.plusSeconds(3));
        assertThat(runningAgain.startedAt()).isEqualTo(firstStart);
        var bytes = new byte[]{80, 75, 3, 4};
        var completed = store.complete(
                1000, 3, 6_000L, 5_000, true,
                "orders.xlsx", bytes, CREATED.plusSeconds(4));

        assertThat(completed.status())
                .isEqualTo(ReportExportStore.Status.SUCCEEDED);
        assertThat(completed.totalRows()).isEqualTo(6_000L);
        assertThat(completed.processedRows()).isEqualTo(5_000);
        assertThat(completed.truncated()).isTrue();
        assertThat(completed.resultSize()).isEqualTo(bytes.length);
        assertThat(store.result(10, 20, 1000)).isEqualTo(bytes);

        assertThat(normalize(jdbc.updates.get(1).sql()))
                .contains("where id=? and version=? and status='running'");
        assertThat(normalize(jdbc.updates.getLast().sql()))
                .contains("where id=? and version=? and status='running'")
                .contains("processed_rows=?", "result_content=?");
        assertThat(normalize(jdbc.queries.getLast().sql()))
                .contains("system_id=? and tenant_id=? and id=?")
                .contains("status='succeeded'");
    }

    @Test
    void staleStateFailsAndFailureClearsAnyResultShape() {
        var jdbc = new StatefulJdbcTemplate(queued());
        var store = new JdbcReportExportStore(jdbc, JSON);
        jdbc.rejectMutation = true;

        assertThatThrownBy(() -> store.start(
                1000, 9, CREATED.plusSeconds(1)))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_EXPORT_CONFLICT"));

        jdbc.rejectMutation = false;
        store.start(1000, 0, CREATED.plusSeconds(1));
        var failed = store.fail(
                1000, 1, "invalid code", "  workbook failed  ",
                CREATED.plusSeconds(2));
        assertThat(failed.status()).isEqualTo(ReportExportStore.Status.FAILED);
        assertThat(failed.failureCode()).isEqualTo("REPORT_EXPORT_FAILED");
        assertThat(failed.failureMessage()).isEqualTo("workbook failed");
        assertThat(failed.resultFilename()).isNull();
        assertThat(failed.resultSize()).isNull();
        assertThat(failed.processedRows()).isZero();
    }

    @Test
    void ownerHistoryAndIdentityReadsCannotEscapeSystemTenantReportMember() {
        var jdbc = new RecordingJdbcTemplate();
        var store = new JdbcReportExportStore(jdbc, JSON);

        assertThat(store.findById(10, 21, 1000)).isEmpty();
        assertThat(store.findByRequestKey(
                10, 21, 30, 100, "a".repeat(64))).isEmpty();
        assertThat(store.countOwned(10, 21, 100, 30)).isZero();
        assertThat(store.pageOwned(10, 21, 100, 30, 20, 0)).isEmpty();

        assertThat(jdbc.queries).allSatisfy(call ->
                assertThat(normalize(call.sql()))
                        .contains("system_id=?", "tenant_id=?"));
        assertThat(normalize(jdbc.queries.get(1).sql()))
                .contains("requested_by_member_id=?", "report_id=?",
                        "request_key_hash=?");
        assertThat(normalize(jdbc.counts.getFirst().sql()))
                .contains("system_id=? and tenant_id=? and report_id=?")
                .contains("requested_by_member_id=?");
        assertThat(normalize(jdbc.queries.getLast().sql()))
                .contains("order by created_at desc,id desc limit ? offset ?");
    }

    @Test
    void storedFieldSnapshotRoundTripsWithoutChangingColumnOrder() {
        var jdbc = new StatefulJdbcTemplate(queued());
        var store = new JdbcReportExportStore(jdbc, JSON);

        var restored = store.findById(10, 20, 1000).orElseThrow();

        assertThat(restored.source()).isEqualTo(queued().source());
        assertThat(restored.source().fields())
                .extracting(ReportFieldPin::code)
                .containsExactly("amount", "title");
    }

    private static ReportExportStore.Run queued() {
        return new ReportExportStore.Run(
                1000, 10, 20, 100, "orders", "Orders report", 200, 3,
                new ReportSourcePin(
                        501, "open_orders", "Open orders", 601, 7, 701,
                        "orders", "schema-9", List.of(
                        new ReportFieldPin(
                                902, "amount", "Amount", "DECIMAL", "DECIMAL"),
                        new ReportFieldPin(
                                901, "title", "Title", "TEXT", "TEXT"))),
                "a".repeat(64), 300, 40, 30,
                ReportExportStore.Status.QUEUED, null, 0, false,
                null, null, null, null, "request-1", "trace-1",
                null, null, CREATED, CREATED, 0);
    }

    private static ReportExportStore.Run copy(
            ReportExportStore.Run source,
            long id,
            ReportExportStore.Status status,
            Long totalRows,
            int processedRows,
            boolean truncated,
            String filename,
            Long resultSize,
            String failureCode,
            String failureMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version
    ) {
        return new ReportExportStore.Run(
                id, source.systemId(), source.tenantId(), source.reportId(),
                source.reportCode(), source.reportName(),
                source.reportVersionId(), source.reportVersionNumber(),
                source.source(), source.requestKeyHash(), source.jobId(),
                source.requestedByAccountId(), source.requestedByMemberId(),
                status, totalRows, processedRows, truncated, filename,
                resultSize, failureCode, failureMessage, source.requestId(),
                source.traceId(), startedAt, finishedAt, createdAt, updatedAt,
                version);
    }

    private static ResultSet row(Map<String, Object> values) {
        return (ResultSet) Proxy.newProxyInstance(
                JdbcReportExportStoreContractTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    var name = method.getName();
                    var value = arguments == null || arguments.length == 0
                            ? null : values.get(arguments[0]);
                    if ("getString".equals(name)) {
                        return value == null ? null : value.toString();
                    }
                    if ("getLong".equals(name)) {
                        return value == null ? 0L : ((Number) value).longValue();
                    }
                    if ("getInt".equals(name)) {
                        return value == null ? 0 : ((Number) value).intValue();
                    }
                    if ("getBoolean".equals(name)) {
                        return value != null && (Boolean) value;
                    }
                    if ("getBytes".equals(name)) {
                        return value;
                    }
                    if ("getObject".equals(name)) {
                        return value;
                    }
                    if ("wasNull".equals(name)) return value == null;
                    var type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    return null;
                });
    }

    private static Map<String, Object> values(
            ReportExportStore.Run run, byte[] content
    ) {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", run.id());
        values.put("system_id", run.systemId());
        values.put("tenant_id", run.tenantId());
        values.put("report_id", run.reportId());
        values.put("report_code", run.reportCode());
        values.put("report_name", run.reportName());
        values.put("report_version_id", run.reportVersionId());
        values.put("report_version_no", run.reportVersionNumber());
        values.put("data_source_id", run.source().dataSourceId());
        values.put("data_source_code", run.source().dataSourceCode());
        values.put("data_source_name", run.source().dataSourceName());
        values.put("data_source_version_id", run.source().dataSourceVersionId());
        values.put("data_source_version_no",
                run.source().dataSourceVersionNumber());
        values.put("module_id", run.source().moduleId());
        values.put("module_code", run.source().moduleCode());
        values.put("schema_version_id", run.source().schemaVersionId());
        try {
            values.put("fields_json", JSON.writeValueAsString(
                    run.source().fields()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        values.put("request_key_hash", run.requestKeyHash());
        values.put("job_id", run.jobId());
        values.put("requested_by_account_id", run.requestedByAccountId());
        values.put("requested_by_member_id", run.requestedByMemberId());
        values.put("status", run.status().name());
        values.put("total_rows", run.totalRows());
        values.put("processed_rows", run.processedRows());
        values.put("truncated", run.truncated());
        values.put("result_filename", run.resultFilename());
        values.put("result_size", run.resultSize());
        values.put("result_content", content);
        values.put("error_code", run.failureCode());
        values.put("error_message", run.failureMessage());
        values.put("request_id", run.requestId());
        values.put("trace_id", run.traceId());
        values.put("started_at", run.startedAt());
        values.put("finished_at", run.finishedAt());
        values.put("created_at", run.createdAt());
        values.put("updated_at", run.updatedAt());
        values.put("version", run.version());
        return values;
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        final List<Call> updates = new ArrayList<>();
        final List<Call> queries = new ArrayList<>();
        final List<Call> counts = new ArrayList<>();

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            return 1;
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> mapper, Object... arguments
        ) {
            queries.add(new Call(sql, arguments));
            return List.of();
        }

        @Override
        public <T> T queryForObject(
                String sql, Class<T> requiredType, Object... arguments
        ) {
            counts.add(new Call(sql, arguments));
            return requiredType.cast(0L);
        }
    }

    private static final class StatefulJdbcTemplate
            extends RecordingJdbcTemplate {
        private ReportExportStore.Run current;
        private byte[] content;
        private boolean duplicateInsert;
        private boolean rejectMutation;

        private StatefulJdbcTemplate(ReportExportStore.Run current) {
            this.current = current;
        }

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            var normalized = normalize(sql);
            if (normalized.startsWith(
                    "insert into un_module_report_export_run")) {
                if (duplicateInsert) {
                    throw new DuplicateKeyException("duplicate");
                }
                return 1;
            }
            if (rejectMutation) return 0;
            if (normalized.contains("set status='running'")) {
                var now = (LocalDateTime) arguments[0];
                current = copy(current, current.id(),
                        ReportExportStore.Status.RUNNING, null, 0, false,
                        null, null, null, null,
                        current.startedAt() == null ? now : current.startedAt(),
                        null, current.createdAt(),
                        (LocalDateTime) arguments[1], current.version() + 1);
            } else if (normalized.contains("set status='queued'")) {
                current = copy(current, current.id(),
                        ReportExportStore.Status.QUEUED, null, 0, false,
                        null, null, null, null, current.startedAt(), null,
                        current.createdAt(), (LocalDateTime) arguments[0],
                        current.version() + 1);
            } else if (normalized.contains("set status='succeeded'")) {
                content = (byte[]) arguments[4];
                current = copy(current, current.id(),
                        ReportExportStore.Status.SUCCEEDED,
                        ((Number) arguments[0]).longValue(),
                        ((Number) arguments[1]).intValue(),
                        (Boolean) arguments[2], (String) arguments[3],
                        ((Number) arguments[5]).longValue(), null, null,
                        current.startedAt(), (LocalDateTime) arguments[6],
                        current.createdAt(), (LocalDateTime) arguments[7],
                        current.version() + 1);
            } else if (normalized.contains("set status='failed'")) {
                content = null;
                current = copy(current, current.id(),
                        ReportExportStore.Status.FAILED, null, 0, false,
                        null, null, (String) arguments[0],
                        (String) arguments[1], current.startedAt(),
                        (LocalDateTime) arguments[2], current.createdAt(),
                        (LocalDateTime) arguments[3], current.version() + 1);
            }
            return 1;
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> mapper, Object... arguments
        ) {
            queries.add(new Call(sql, arguments));
            try {
                return List.of(mapper.mapRow(row(values(current, content)), 0));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private record Call(String sql, Object[] arguments) {
    }
}
