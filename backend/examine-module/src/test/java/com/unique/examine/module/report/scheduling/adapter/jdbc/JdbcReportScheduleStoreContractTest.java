package com.unique.examine.module.report.scheduling.adapter.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.scheduling.ReportScheduleStore;
import com.unique.examine.module.report.scheduling.ReportScheduleTiming;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcReportScheduleStoreContractTest {
    private static final Instant CREATED = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void insertsScheduleRootAndDeterministicallyOrderedCurrentRecipients() {
        var jdbc = new RecordingJdbcTemplate();
        var store = store(jdbc);

        store.insertSchedule(schedule(101, true, 1));

        assertThat(jdbc.updates).hasSize(3);
        var root = jdbc.updates.getFirst();
        assertThat(normalize(root.sql()))
                .startsWith("insert into un_module_report_schedule")
                .contains("days_of_week_json", "recipient_count", "next_fire_at");
        assertThat(root.arguments()[10]).isEqualTo("[1,3]");
        assertThat(root.arguments()[11]).isEqualTo(2);
        assertThat(root.arguments()[15]).isEqualTo(db(CREATED.plusSeconds(3600)));
        assertThat(jdbc.updates.get(1).arguments()[4]).isEqualTo(11L);
        assertThat(jdbc.updates.get(2).arguments()[4]).isEqualTo(22L);
    }

    @Test
    void dueAndRunningClaimsSkipLockedAndRecoverNullOrExpiredLeases() {
        var jdbc = new RecordingJdbcTemplate();
        var store = store(jdbc);
        var now = CREATED.plusSeconds(7200);

        assertThat(store.lockDueSchedules(now, 10)).isEmpty();
        assertThat(store.claimRunning(now, now.plusSeconds(30), 10)).isEmpty();

        assertThat(normalize(jdbc.queries.get(0).sql()))
                .contains("enabled=true and next_fire_at<=?")
                .contains("limit ? for update skip locked");
        assertThat(normalize(jdbc.queries.get(1).sql()))
                .contains("status='running' and available_at<=?")
                .contains("(lease_until is null or lease_until<=?)")
                .contains("limit ? for update skip locked");
        assertThat(jdbc.queries.get(1).arguments())
                .containsExactly(db(now), db(now), 10);
    }

    @Test
    void scheduleUpdateCarriesScopeVersionAndExpectedFireCas() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.updateResult = 0;
        var store = store(jdbc);

        assertThatThrownBy(() -> store.updateSchedule(schedule(101, true, 2), 1))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("concurrently");
        assertThat(normalize(jdbc.updates.getFirst().sql()))
                .contains("where id=? and system_id=? and tenant_id=? and report_id=? and version=?");

        jdbc.updateResult = 1;
        assertThatThrownBy(() -> store.advanceSchedule(
                101, 2, CREATED.plusSeconds(3600), CREATED.plusSeconds(3600),
                CREATED.plusSeconds(7200), CREATED.plusSeconds(3601)))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("not found");
        assertThat(normalize(jdbc.updates.getLast().sql()))
                .contains("where id=? and version=? and enabled=true")
                .contains("and next_fire_at=?");
    }

    @Test
    void runningRetryIncrementsAttemptWhileKeepingExportColumnsUntouched() {
        var jdbc = new RecordingJdbcTemplate();
        var store = store(jdbc);

        assertThatThrownBy(() -> store.retry(
                201, 4, ReportScheduleStore.OccurrenceStatus.RUNNING,
                "EXPORT_POLL_TIMEOUT", "temporary", CREATED.plusSeconds(90),
                CREATED.plusSeconds(60)))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("not found");

        var sql = normalize(jdbc.updates.getFirst().sql());
        assertThat(sql)
                .contains("attempt_count=attempt_count+if(?='running',1,0)")
                .contains("lease_until=null")
                .doesNotContain("export_id=", "export_status=");
        assertThat(jdbc.updates.getFirst().arguments()[0]).isEqualTo("RUNNING");
        assertThat(jdbc.updates.getFirst().arguments()[1]).isEqualTo("RUNNING");
    }

    @Test
    void deliveryHistoryIsAuthorizedOnlyByImmutableDeliveryFacts() {
        var jdbc = new RecordingJdbcTemplate();
        var store = store(jdbc);

        assertThat(store.countDelivered(1, 2, "monthly_sales", 11)).isZero();
        assertThat(store.pageDelivered(1, 2, "monthly_sales", 11, 20, 0))
                .isEmpty();
        assertThat(store.findDelivered(1, 2, "monthly_sales", 201, 11))
                .isEmpty();

        assertThat(normalize(jdbc.counts.getFirst().sql()))
                .contains("join un_module_report_schedule_occurrence o")
                .contains("d.recipient_member_id=?");
        assertThat(normalize(jdbc.queries.get(0).sql()))
                .contains("join un_module_report_schedule_delivery d")
                .contains("d.recipient_member_id=?")
                .doesNotContain("un_module_report_schedule_recipient");
    }

    @Test
    void occurrenceReplayIgnoresNewSnowflakeButPreservesLogicalIdentity() {
        var existing = occurrence(201, CREATED, CREATED);
        var replay = occurrence(999, CREATED.plusSeconds(20), CREATED);
        var jdbc = new ReplayJdbcTemplate(existing, null);

        var returned = store(jdbc).insertOccurrenceIfAbsent(replay);

        assertThat(returned.id()).isEqualTo(existing.id());
        assertThat(returned.occurrenceKey()).isEqualTo(replay.occurrenceKey());
        assertThat(jdbc.updates).hasSize(1);
    }

    @Test
    void deliveryReplayIgnoresNewFactIdAndTimestamp() {
        var existing = new ReportScheduleStore.Delivery(
                301, 1, 2, 201, 11, 401, "schedule:201:11", CREATED);
        var replay = new ReportScheduleStore.Delivery(
                999, 1, 2, 201, 11, 401, "schedule:201:11",
                CREATED.plusSeconds(20));
        var jdbc = new ReplayJdbcTemplate(null, existing);

        var returned = store(jdbc).recordDeliveryIfAbsent(replay);

        assertThat(returned.id()).isEqualTo(existing.id());
        assertThat(returned.deliveredAt()).isEqualTo(existing.deliveredAt());
    }

    private static JdbcReportScheduleStore store(JdbcTemplate jdbc) {
        return new JdbcReportScheduleStore(jdbc, new ObjectMapper());
    }

    private static ReportScheduleStore.Schedule schedule(
            long id, boolean enabled, long version) {
        return new ReportScheduleStore.Schedule(id, 1, 2, 3,
                "monthly_sales", "weekday_digest", "Weekday digest",
                "Asia/Shanghai", ReportScheduleTiming.Kind.WEEKLY,
                LocalTime.of(9, 30), List.of(DayOfWeek.WEDNESDAY,
                DayOfWeek.MONDAY), List.of(22L, 11L), enabled, 5, 7,
                enabled ? CREATED.plusSeconds(3600) : null, null,
                CREATED, CREATED.plusSeconds(version), version);
    }

    private static ReportScheduleStore.Occurrence occurrence(
            long id, Instant createdAt, Instant scheduledAt) {
        return new ReportScheduleStore.Occurrence(id, 1, 2, 101, 1,
                "weekday_digest", "Weekday digest", 3, "monthly_sales",
                5, 7, List.of(11L, 22L), scheduledAt,
                "weekday_digest:2026-08-04T01:00:00Z",
                ReportScheduleStore.OccurrenceStatus.PENDING, 0, 3,
                null, null, null, null, null, 0, false, 0,
                null, null, createdAt, null, "request-1", "trace-1",
                null, null, createdAt, createdAt, 0);
    }

    private static Map<String, Object> values(
            ReportScheduleStore.Occurrence value) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", value.id());
        row.put("system_id", value.systemId());
        row.put("tenant_id", value.tenantId());
        row.put("schedule_id", value.scheduleId());
        row.put("schedule_version", value.scheduleVersion());
        row.put("schedule_code", value.scheduleCode());
        row.put("schedule_name", value.scheduleName());
        row.put("report_id", value.reportId());
        row.put("report_code", value.reportCode());
        row.put("owner_account_id", value.ownerAccountId());
        row.put("owner_member_id", value.ownerMemberId());
        row.put("scheduled_at", db(value.scheduledAt()));
        row.put("occurrence_key", value.occurrenceKey());
        row.put("status", value.status().name());
        row.put("attempt_count", value.attemptCount());
        row.put("max_attempts", value.maxAttempts());
        row.put("export_id", value.exportId());
        row.put("export_status", value.exportStatus());
        row.put("result_filename", value.resultFilename());
        row.put("result_size", value.resultSize());
        row.put("total_rows", value.totalRows());
        row.put("processed_rows", value.processedRows());
        row.put("truncated", value.truncated());
        row.put("delivered_recipient_count", value.deliveredRecipientCount());
        row.put("failure_code", value.failureCode());
        row.put("failure_message", value.failureMessage());
        row.put("available_at", db(value.availableAt()));
        row.put("lease_until", db(value.leaseUntil()));
        row.put("request_id", value.requestId());
        row.put("trace_id", value.traceId());
        row.put("started_at", db(value.startedAt()));
        row.put("finished_at", db(value.finishedAt()));
        row.put("created_at", db(value.createdAt()));
        row.put("updated_at", db(value.updatedAt()));
        row.put("version", value.version());
        return row;
    }

    private static Map<String, Object> values(
            ReportScheduleStore.Delivery value) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", value.id());
        row.put("system_id", value.systemId());
        row.put("tenant_id", value.tenantId());
        row.put("occurrence_id", value.occurrenceId());
        row.put("recipient_member_id", value.recipientMemberId());
        row.put("message_id", value.messageId());
        row.put("delivery_key", value.deliveryKey());
        row.put("delivered_at", db(value.deliveredAt()));
        return row;
    }

    private static ResultSet row(Map<String, Object> values) {
        return (ResultSet) Proxy.newProxyInstance(
                JdbcReportScheduleStoreContractTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, arguments) -> {
                    var name = method.getName();
                    var key = arguments == null || arguments.length == 0
                            ? null : String.valueOf(arguments[0]);
                    var value = values.get(key);
                    if ("getString".equals(name)) {
                        return value == null ? null : String.valueOf(value);
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
                    if ("getObject".equals(name)) return value;
                    if ("wasNull".equals(name)) return value == null;
                    var type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    return null;
                });
    }

    private static LocalDateTime db(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private record Call(String sql, Object[] arguments) { }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        final List<Call> updates = new ArrayList<>();
        final List<Call> queries = new ArrayList<>();
        final List<Call> counts = new ArrayList<>();
        int updateResult = 1;

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            return updateResult;
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> mapper, Object... arguments) {
            queries.add(new Call(sql, arguments));
            return List.of();
        }

        @Override
        public <T> T queryForObject(
                String sql, Class<T> requiredType, Object... arguments) {
            counts.add(new Call(sql, arguments));
            return requiredType.cast(0L);
        }
    }

    private static final class ReplayJdbcTemplate
            extends RecordingJdbcTemplate {
        private final ReportScheduleStore.Occurrence occurrence;
        private final ReportScheduleStore.Delivery delivery;

        private ReplayJdbcTemplate(
                ReportScheduleStore.Occurrence occurrence,
                ReportScheduleStore.Delivery delivery) {
            this.occurrence = occurrence;
            this.delivery = delivery;
        }

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            throw new DuplicateKeyException("replay");
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> mapper, Object... arguments) {
            queries.add(new Call(sql, arguments));
            try {
                var normalized = normalize(sql);
                if (normalized.contains(
                        "from un_module_report_schedule_occurrence_recipient")) {
                    var rows = new ArrayList<T>();
                    for (var memberId : occurrence.configuredRecipientMemberIds()) {
                        rows.add(mapper.mapRow(row(Map.of("1", memberId)), 0));
                    }
                    return rows;
                }
                if (normalized.contains(
                        "from un_module_report_schedule_delivery")) {
                    return List.of(mapper.mapRow(row(values(delivery)), 0));
                }
                return List.of(mapper.mapRow(row(values(occurrence)), 0));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
