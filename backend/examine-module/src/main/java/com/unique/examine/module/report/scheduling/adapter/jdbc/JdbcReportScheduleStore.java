package com.unique.examine.module.report.scheduling.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.scheduling.ReportScheduleStore;
import com.unique.examine.module.report.scheduling.ReportScheduleTiming;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository("jdbcReportScheduleStore")
public class JdbcReportScheduleStore implements ReportScheduleStore {
    private static final TypeReference<List<Integer>> WEEKDAYS =
            new TypeReference<>() { };
    private static final String SCHEDULE_COLUMNS = """
            id,system_id,tenant_id,report_id,report_code,schedule_code,
            schedule_name,time_zone,cadence_type,local_time,
            days_of_week_json,enabled,owner_account_id,owner_member_id,
            next_fire_at,last_scheduled_at,created_at,updated_at,version
            """;
    private static final String OCCURRENCE_COLUMNS = """
            id,system_id,tenant_id,schedule_id,schedule_version,schedule_code,
            schedule_name,report_id,report_code,owner_account_id,
            owner_member_id,scheduled_at,occurrence_key,status,attempt_count,
            max_attempts,export_id,export_status,result_filename,result_size,
            total_rows,processed_rows,truncated,delivered_recipient_count,
            failure_code,failure_message,available_at,lease_until,request_id,
            trace_id,started_at,finished_at,created_at,updated_at,version
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcReportScheduleStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<Schedule> findSchedule(
            long systemId, long tenantId, long reportId, long scheduleId) {
        return one(jdbc.query("SELECT " + SCHEDULE_COLUMNS + " FROM "
                        + "un_module_report_schedule WHERE system_id=? "
                        + "AND tenant_id=? AND report_id=? AND id=?",
                this::schedule, systemId, tenantId, reportId, scheduleId),
                "schedule");
    }

    @Override
    public Optional<Schedule> findScheduleByCode(
            long systemId, long tenantId, long reportId, String code) {
        return one(jdbc.query("SELECT " + SCHEDULE_COLUMNS + " FROM "
                        + "un_module_report_schedule WHERE system_id=? "
                        + "AND tenant_id=? AND report_id=? AND schedule_code=?",
                this::schedule, systemId, tenantId, reportId, code), "schedule");
    }

    @Override
    public long countSchedules(long systemId, long tenantId, long reportId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_module_report_schedule
                 WHERE system_id=? AND tenant_id=? AND report_id=?
                """, Long.class, systemId, tenantId, reportId);
        return count == null ? 0 : count;
    }

    @Override
    public List<Schedule> pageSchedules(
            long systemId, long tenantId, long reportId, int limit, long offset) {
        page(limit, offset);
        return jdbc.query("SELECT " + SCHEDULE_COLUMNS + " FROM "
                        + "un_module_report_schedule WHERE system_id=? "
                        + "AND tenant_id=? AND report_id=? "
                        + "ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
                this::schedule, systemId, tenantId, reportId, limit, offset);
    }

    @Override
    @Transactional
    public void insertSchedule(Schedule value) {
        Objects.requireNonNull(value, "schedule");
        try {
            changed(jdbc.update("""
                    INSERT INTO un_module_report_schedule (
                        id,system_id,tenant_id,report_id,report_code,
                        schedule_code,schedule_name,time_zone,cadence_type,
                        local_time,days_of_week_json,recipient_count,enabled,
                        owner_account_id,owner_member_id,next_fire_at,
                        last_scheduled_at,created_at,updated_at,version
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, value.id(), value.systemId(), value.tenantId(),
                    value.reportId(), value.reportCode(), value.code(),
                    value.name(), value.timeZone(), value.cadenceKind().name(),
                    value.localTime(), weekdays(value.daysOfWeek()),
                    value.recipientMemberIds().size(), value.enabled(),
                    value.ownerAccountId(), value.ownerMemberId(),
                    db(value.nextFireAt()), db(value.lastScheduledAt()),
                    db(value.createdAt()), db(value.updatedAt()), value.version()),
                    "Report schedule insert failed");
            insertScheduleRecipients(value);
        } catch (DuplicateKeyException exception) {
            throw conflict("Report schedule id or code is already in use");
        } catch (DataIntegrityViolationException exception) {
            throw invalid("Report schedule violated a scoped reference");
        }
    }

    @Override
    @Transactional
    public Schedule updateSchedule(Schedule value, long expectedVersion) {
        Objects.requireNonNull(value, "schedule");
        try {
            mutate(jdbc.update("""
                    UPDATE un_module_report_schedule
                       SET report_code=?,schedule_code=?,schedule_name=?,
                           time_zone=?,cadence_type=?,local_time=?,
                           days_of_week_json=?,recipient_count=?,enabled=?,
                           owner_account_id=?,owner_member_id=?,next_fire_at=?,
                           last_scheduled_at=?,updated_at=?,version=version+1
                     WHERE id=? AND system_id=? AND tenant_id=? AND report_id=?
                       AND version=?
                    """, value.reportCode(), value.code(), value.name(),
                    value.timeZone(), value.cadenceKind().name(),
                    value.localTime(), weekdays(value.daysOfWeek()),
                    value.recipientMemberIds().size(), value.enabled(),
                    value.ownerAccountId(), value.ownerMemberId(),
                    db(value.nextFireAt()), db(value.lastScheduledAt()),
                    db(value.updatedAt()), value.id(), value.systemId(),
                    value.tenantId(), value.reportId(), expectedVersion),
                    "Report schedule changed concurrently");
            jdbc.update("""
                    DELETE FROM un_module_report_schedule_recipient
                     WHERE system_id=? AND tenant_id=? AND schedule_id=?
                    """, value.systemId(), value.tenantId(), value.id());
            insertScheduleRecipients(value);
            return requireSchedule(value.id());
        } catch (DuplicateKeyException exception) {
            throw conflict("Report schedule code is already in use");
        } catch (DataIntegrityViolationException exception) {
            throw invalid("Report schedule violated a scoped reference");
        }
    }

    @Override
    @Transactional
    public List<Schedule> lockDueSchedules(Instant now, int limit) {
        required(now, "due time");
        claimLimit(limit);
        return jdbc.query("SELECT " + SCHEDULE_COLUMNS + " FROM "
                        + "un_module_report_schedule WHERE enabled=TRUE "
                        + "AND next_fire_at<=? ORDER BY next_fire_at,id "
                        + "LIMIT ? FOR UPDATE SKIP LOCKED",
                this::schedule, db(now), limit);
    }

    @Override
    public Schedule advanceSchedule(
            long scheduleId, long expectedVersion, Instant expectedFireAt,
            Instant firedAt, Instant nextFireAt, Instant now) {
        required(expectedFireAt, "expected fire time");
        required(firedAt, "scheduled fire time");
        required(now, "update time");
        mutate(jdbc.update("""
                UPDATE un_module_report_schedule
                   SET last_scheduled_at=?,next_fire_at=?,enabled=?,
                       updated_at=?,version=version+1
                 WHERE id=? AND version=? AND enabled=TRUE
                   AND next_fire_at=?
                """, db(firedAt), db(nextFireAt), nextFireAt != null, db(now),
                scheduleId, expectedVersion, db(expectedFireAt)),
                "Report schedule could not advance");
        return requireSchedule(scheduleId);
    }

    @Override
    @Transactional
    public Occurrence insertOccurrenceIfAbsent(Occurrence value) {
        Objects.requireNonNull(value, "occurrence");
        if (value.status() != OccurrenceStatus.PENDING
                || value.attemptCount() != 0 || value.version() != 0) {
            throw invalid("A new report schedule occurrence must be pending");
        }
        try {
            changed(jdbc.update("""
                    INSERT INTO un_module_report_schedule_occurrence (
                        id,system_id,tenant_id,schedule_id,schedule_version,
                        schedule_code,schedule_name,report_id,report_code,
                        owner_account_id,owner_member_id,
                        configured_recipient_count,scheduled_at,occurrence_key,
                        status,attempt_count,max_attempts,export_id,export_status,
                        result_filename,result_size,total_rows,processed_rows,
                        truncated,delivered_recipient_count,failure_code,
                        failure_message,available_at,lease_until,request_id,
                        trace_id,started_at,finished_at,created_at,updated_at,version
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, occurrenceArguments(value)),
                    "Report schedule occurrence insert failed");
            insertOccurrenceRecipients(value);
            return requireOccurrence(value.id());
        } catch (DuplicateKeyException exception) {
            var replay = findReplay(value.systemId(), value.tenantId(),
                    value.scheduleId(), value.scheduledAt(), value.occurrenceKey());
            if (replay.isPresent() && sameOccurrence(replay.orElseThrow(), value)) {
                return replay.orElseThrow();
            }
            throw conflict("Report schedule occurrence key is already in use");
        } catch (DataIntegrityViolationException exception) {
            throw invalid("Report schedule occurrence violated a scoped reference");
        }
    }

    @Override
    public Optional<Occurrence> findOccurrence(
            long systemId, long tenantId, long occurrenceId) {
        return one(jdbc.query("SELECT " + OCCURRENCE_COLUMNS + " FROM "
                        + "un_module_report_schedule_occurrence "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                this::occurrence, systemId, tenantId, occurrenceId), "occurrence");
    }

    @Override
    @Transactional
    public List<Occurrence> claimPending(
            Instant now, Instant leaseUntil, int limit) {
        lease(now, leaseUntil, limit);
        var rows = lockOccurrences("""
                status='PENDING' AND available_at<=?
                AND attempt_count<max_attempts
                """, now, limit);
        return rows.stream().map(value -> {
            mutate(jdbc.update("""
                    UPDATE un_module_report_schedule_occurrence
                       SET status='RUNNING',attempt_count=attempt_count+1,
                           lease_until=?,started_at=COALESCE(started_at,?),
                           failure_code=NULL,failure_message=NULL,
                           updated_at=?,version=version+1
                     WHERE id=? AND version=? AND status='PENDING'
                    """, db(leaseUntil), db(now), db(now), value.id(),
                    value.version()), "Pending occurrence could not be claimed");
            return requireOccurrence(value.id());
        }).toList();
    }

    @Override
    @Transactional
    public List<Occurrence> claimRunning(
            Instant now, Instant leaseUntil, int limit) {
        lease(now, leaseUntil, limit);
        var rows = jdbc.query("SELECT " + OCCURRENCE_COLUMNS + " FROM "
                        + "un_module_report_schedule_occurrence WHERE "
                        + "status='RUNNING' AND available_at<=? "
                        + "AND (lease_until IS NULL OR lease_until<=?) "
                        + "AND attempt_count<=max_attempts "
                        + "ORDER BY available_at,scheduled_at,id "
                        + "LIMIT ? FOR UPDATE SKIP LOCKED",
                this::occurrence, db(now), db(now), limit);
        return rows.stream().map(value -> {
            mutate(jdbc.update("""
                    UPDATE un_module_report_schedule_occurrence
                       SET lease_until=?,updated_at=?,version=version+1
                     WHERE id=? AND version=? AND status='RUNNING'
                       AND available_at<=?
                       AND (lease_until IS NULL OR lease_until<=?)
                    """, db(leaseUntil), db(now), value.id(), value.version(),
                    db(now), db(now)),
                    "Running occurrence could not be reclaimed");
            return requireOccurrence(value.id());
        }).toList();
    }

    @Override
    public Occurrence attachExport(
            long occurrenceId, long expectedVersion, long exportId,
            String exportStatus, Instant availableAt, Instant now) {
        if (exportId <= 0) throw invalid("Report schedule export id is invalid");
        required(availableAt, "poll time");
        required(now, "update time");
        mutate(jdbc.update("""
                UPDATE un_module_report_schedule_occurrence
                   SET export_id=?,export_status=?,available_at=?,
                       lease_until=NULL,updated_at=?,version=version+1
                 WHERE id=? AND version=? AND status='RUNNING'
                   AND export_id IS NULL
                """, exportId, state(exportStatus), db(availableAt), db(now),
                occurrenceId, expectedVersion),
                "Report schedule export could not be attached");
        return requireOccurrence(occurrenceId);
    }

    @Override
    public Occurrence awaitExport(
            long occurrenceId, long expectedVersion, String exportStatus,
            Instant availableAt, Instant now) {
        required(availableAt, "poll time");
        required(now, "update time");
        mutate(jdbc.update("""
                UPDATE un_module_report_schedule_occurrence
                   SET export_status=?,available_at=?,lease_until=NULL,
                       updated_at=?,version=version+1
                 WHERE id=? AND version=? AND status='RUNNING'
                   AND export_id IS NOT NULL
                """, state(exportStatus), db(availableAt), db(now),
                occurrenceId, expectedVersion),
                "Report schedule export wait could not be saved");
        return requireOccurrence(occurrenceId);
    }

    @Override
    public Occurrence retry(
            long occurrenceId, long expectedVersion, OccurrenceStatus status,
            String failureCode, String failureMessage, Instant availableAt,
            Instant now) {
        if (status != OccurrenceStatus.PENDING
                && status != OccurrenceStatus.RUNNING
                && status != OccurrenceStatus.FAILED) {
            throw invalid("Report schedule retry state is invalid");
        }
        required(now, "update time");
        if (status != OccurrenceStatus.FAILED) required(availableAt, "retry time");
        if (status == OccurrenceStatus.FAILED && availableAt != null) {
            throw invalid("A terminal occurrence cannot be available");
        }
        mutate(jdbc.update("""
                UPDATE un_module_report_schedule_occurrence
                   SET status=?,
                       attempt_count=attempt_count+IF(?='RUNNING',1,0),
                       failure_code=?,failure_message=?,
                       available_at=?,lease_until=NULL,finished_at=?,
                       updated_at=?,version=version+1
                 WHERE id=? AND version=? AND status='RUNNING'
                   AND (?='FAILED' OR attempt_count<max_attempts)
                   AND (?<>'PENDING' OR export_id IS NULL)
                """, status.name(), status.name(), failure(failureCode),
                message(failureMessage), db(availableAt),
                status == OccurrenceStatus.FAILED ? db(now) : null, db(now),
                occurrenceId, expectedVersion, status.name(), status.name()),
                "Report schedule occurrence could not retry");
        return requireOccurrence(occurrenceId);
    }

    @Override
    public Occurrence succeed(
            long occurrenceId, long expectedVersion, String exportStatus,
            String resultFilename, long resultSize, long totalRows,
            int processedRows, boolean truncated, int deliveredRecipientCount,
            Instant now) {
        required(now, "finish time");
        if (!"SUCCEEDED".equals(exportStatus) || resultFilename == null
                || !resultFilename.endsWith(".xlsx") || resultFilename.isBlank()
                || resultFilename.length() > 180 || resultSize <= 0
                || totalRows < 0 || processedRows < 0 || processedRows > 5_000
                || totalRows < processedRows
                || truncated != (totalRows > processedRows)
                || deliveredRecipientCount < 0
                || deliveredRecipientCount > MAX_RECIPIENTS) {
            throw invalid("Report schedule occurrence result is invalid");
        }
        mutate(jdbc.update("""
                UPDATE un_module_report_schedule_occurrence o
                   SET status='SUCCEEDED',export_status='SUCCEEDED',
                       result_filename=?,result_size=?,total_rows=?,
                       processed_rows=?,truncated=?,delivered_recipient_count=?,
                       failure_code=NULL,failure_message=NULL,available_at=NULL,
                       lease_until=NULL,finished_at=?,updated_at=?,version=version+1
                 WHERE o.id=? AND o.version=? AND o.status='RUNNING'
                   AND o.export_id IS NOT NULL
                   AND o.configured_recipient_count>=?
                   AND ?=(SELECT COUNT(*)
                            FROM un_module_report_schedule_delivery d
                           WHERE d.system_id=o.system_id
                             AND d.tenant_id=o.tenant_id
                             AND d.occurrence_id=o.id)
                """, resultFilename.strip(), resultSize, totalRows, processedRows,
                truncated, deliveredRecipientCount, db(now), db(now), occurrenceId,
                expectedVersion, deliveredRecipientCount,
                deliveredRecipientCount),
                "Report schedule occurrence could not succeed");
        return requireOccurrence(occurrenceId);
    }

    @Override
    public Occurrence fail(
            long occurrenceId, long expectedVersion, String failureCode,
            String failureMessage, int deliveredRecipientCount, Instant now) {
        required(now, "finish time");
        if (deliveredRecipientCount < 0
                || deliveredRecipientCount > MAX_RECIPIENTS) {
            throw invalid("Report schedule delivery count is invalid");
        }
        mutate(jdbc.update("""
                UPDATE un_module_report_schedule_occurrence o
                   SET status='FAILED',result_filename=NULL,result_size=NULL,
                       total_rows=NULL,processed_rows=0,truncated=FALSE,
                       delivered_recipient_count=?,failure_code=?,
                       failure_message=?,available_at=NULL,lease_until=NULL,
                       finished_at=?,updated_at=?,version=version+1
                 WHERE o.id=? AND o.version=? AND o.status='RUNNING'
                   AND o.configured_recipient_count>=?
                   AND ?=(SELECT COUNT(*)
                            FROM un_module_report_schedule_delivery d
                           WHERE d.system_id=o.system_id
                             AND d.tenant_id=o.tenant_id
                             AND d.occurrence_id=o.id)
                """, deliveredRecipientCount, failure(failureCode),
                message(failureMessage), db(now), db(now), occurrenceId,
                expectedVersion, deliveredRecipientCount,
                deliveredRecipientCount),
                "Report schedule occurrence could not fail");
        return requireOccurrence(occurrenceId);
    }

    @Override
    public Delivery recordDeliveryIfAbsent(Delivery value) {
        Objects.requireNonNull(value, "delivery");
        try {
            changed(jdbc.update("""
                    INSERT INTO un_module_report_schedule_delivery (
                        id,system_id,tenant_id,occurrence_id,
                        recipient_member_id,message_id,delivery_key,delivered_at
                    ) VALUES (?,?,?,?,?,?,?,?)
                    """, value.id(), value.systemId(), value.tenantId(),
                    value.occurrenceId(), value.recipientMemberId(),
                    value.messageId(), value.deliveryKey(), db(value.deliveredAt())),
                    "Report schedule delivery insert failed");
            return requireDelivery(value.id());
        } catch (DuplicateKeyException exception) {
            var replay = findDelivery(value.systemId(), value.tenantId(),
                    value.occurrenceId(), value.recipientMemberId(),
                    value.deliveryKey());
            if (replay.isPresent()
                    && sameDelivery(replay.orElseThrow(), value)) {
                return replay.orElseThrow();
            }
            throw conflict("Report schedule delivery key is already in use");
        } catch (DataIntegrityViolationException exception) {
            throw invalid("Report schedule delivery violated its recipient snapshot");
        }
    }

    @Override
    public long countDeliveries(long systemId, long tenantId, long occurrenceId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_module_report_schedule_delivery
                 WHERE system_id=? AND tenant_id=? AND occurrence_id=?
                """, Long.class, systemId, tenantId, occurrenceId);
        return count == null ? 0 : count;
    }

    @Override
    public long countDelivered(
            long systemId, long tenantId, String reportCode,
            long recipientMemberId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM un_module_report_schedule_delivery d
                  JOIN un_module_report_schedule_occurrence o
                    ON o.system_id=d.system_id AND o.tenant_id=d.tenant_id
                   AND o.id=d.occurrence_id
                 WHERE d.system_id=? AND d.tenant_id=?
                   AND o.report_code=? AND d.recipient_member_id=?
                """, Long.class, systemId, tenantId, reportCode,
                recipientMemberId);
        return count == null ? 0 : count;
    }

    @Override
    public List<Occurrence> pageDelivered(
            long systemId, long tenantId, String reportCode,
            long recipientMemberId, int limit, long offset) {
        page(limit, offset);
        return jdbc.query("SELECT " + columns("o") + " FROM "
                        + "un_module_report_schedule_occurrence o JOIN "
                        + "un_module_report_schedule_delivery d ON "
                        + "d.system_id=o.system_id AND d.tenant_id=o.tenant_id "
                        + "AND d.occurrence_id=o.id WHERE o.system_id=? "
                        + "AND o.tenant_id=? AND o.report_code=? "
                        + "AND d.recipient_member_id=? "
                        + "ORDER BY d.delivered_at DESC,o.id DESC LIMIT ? OFFSET ?",
                this::occurrence, systemId, tenantId, reportCode,
                recipientMemberId, limit, offset);
    }

    @Override
    public Optional<Occurrence> findDelivered(
            long systemId, long tenantId, String reportCode,
            long occurrenceId, long recipientMemberId) {
        return one(jdbc.query("SELECT " + columns("o") + " FROM "
                        + "un_module_report_schedule_occurrence o JOIN "
                        + "un_module_report_schedule_delivery d ON "
                        + "d.system_id=o.system_id AND d.tenant_id=o.tenant_id "
                        + "AND d.occurrence_id=o.id WHERE o.system_id=? "
                        + "AND o.tenant_id=? AND o.report_code=? AND o.id=? "
                        + "AND d.recipient_member_id=?",
                this::occurrence, systemId, tenantId, reportCode,
                occurrenceId, recipientMemberId), "delivered occurrence");
    }

    private void insertScheduleRecipients(Schedule value) {
        for (var ordinal = 0; ordinal < value.recipientMemberIds().size(); ordinal++) {
            changed(jdbc.update("""
                    INSERT INTO un_module_report_schedule_recipient (
                        system_id,tenant_id,schedule_id,recipient_ordinal,
                        recipient_member_id,created_at) VALUES (?,?,?,?,?,?)
                    """, value.systemId(), value.tenantId(), value.id(), ordinal,
                    value.recipientMemberIds().get(ordinal), db(value.updatedAt())),
                    "Report schedule recipient insert failed");
        }
    }

    private void insertOccurrenceRecipients(Occurrence value) {
        for (var ordinal = 0;
             ordinal < value.configuredRecipientMemberIds().size(); ordinal++) {
            changed(jdbc.update("""
                    INSERT INTO un_module_report_schedule_occurrence_recipient (
                        system_id,tenant_id,occurrence_id,recipient_ordinal,
                        recipient_member_id,created_at) VALUES (?,?,?,?,?,?)
                    """, value.systemId(), value.tenantId(), value.id(), ordinal,
                    value.configuredRecipientMemberIds().get(ordinal),
                    db(value.createdAt())),
                    "Report schedule occurrence recipient insert failed");
        }
    }

    private Schedule requireSchedule(long id) {
        return one(jdbc.query("SELECT " + SCHEDULE_COLUMNS
                        + " FROM un_module_report_schedule WHERE id=?",
                this::schedule, id), "schedule")
                .orElseThrow(() -> notFound("Report schedule was not found"));
    }

    private Occurrence requireOccurrence(long id) {
        return one(jdbc.query("SELECT " + OCCURRENCE_COLUMNS
                        + " FROM un_module_report_schedule_occurrence WHERE id=?",
                this::occurrence, id), "occurrence")
                .orElseThrow(() -> notFound(
                        "Report schedule occurrence was not found"));
    }

    private Optional<Occurrence> findReplay(
            long systemId, long tenantId, long scheduleId, Instant scheduledAt,
            String occurrenceKey) {
        var rows = jdbc.query("SELECT " + OCCURRENCE_COLUMNS + " FROM "
                        + "un_module_report_schedule_occurrence WHERE system_id=? "
                        + "AND tenant_id=? AND ((schedule_id=? AND scheduled_at=?) "
                        + "OR occurrence_key=?)",
                this::occurrence, systemId, tenantId, scheduleId,
                db(scheduledAt), occurrenceKey);
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    private List<Occurrence> lockOccurrences(
            String predicate, Instant now, int limit) {
        return jdbc.query("SELECT " + OCCURRENCE_COLUMNS + " FROM "
                        + "un_module_report_schedule_occurrence WHERE "
                        + predicate + " ORDER BY available_at,scheduled_at,id "
                        + "LIMIT ? FOR UPDATE SKIP LOCKED",
                this::occurrence, db(now), limit);
    }

    private Optional<Delivery> findDelivery(
            long systemId, long tenantId, long occurrenceId,
            long recipientMemberId, String deliveryKey) {
        var rows = jdbc.query("""
                SELECT id,system_id,tenant_id,occurrence_id,recipient_member_id,
                       message_id,delivery_key,delivered_at
                  FROM un_module_report_schedule_delivery
                 WHERE system_id=? AND tenant_id=?
                   AND ((occurrence_id=? AND recipient_member_id=?)
                     OR delivery_key=?)
                """, this::delivery, systemId, tenantId, occurrenceId,
                recipientMemberId, deliveryKey);
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    private Delivery requireDelivery(long id) {
        return one(jdbc.query("""
                SELECT id,system_id,tenant_id,occurrence_id,recipient_member_id,
                       message_id,delivery_key,delivered_at
                  FROM un_module_report_schedule_delivery WHERE id=?
                """, this::delivery, id), "delivery")
                .orElseThrow(() -> notFound(
                        "Report schedule delivery was not found"));
    }

    private Schedule schedule(ResultSet row, int ignored) throws SQLException {
        var systemId = row.getLong("system_id");
        var tenantId = row.getLong("tenant_id");
        var id = row.getLong("id");
        return new Schedule(id, systemId, tenantId, row.getLong("report_id"),
                row.getString("report_code"), row.getString("schedule_code"),
                row.getString("schedule_name"), row.getString("time_zone"),
                ReportScheduleTiming.Kind.valueOf(row.getString("cadence_type")),
                row.getObject("local_time", LocalTime.class),
                readWeekdays(row.getString("days_of_week_json")),
                scheduleRecipients(systemId, tenantId, id),
                row.getBoolean("enabled"), row.getLong("owner_account_id"),
                row.getLong("owner_member_id"), instant(row, "next_fire_at"),
                instant(row, "last_scheduled_at"), instant(row, "created_at"),
                instant(row, "updated_at"), row.getLong("version"));
    }

    private Occurrence occurrence(ResultSet row, int ignored) throws SQLException {
        var systemId = row.getLong("system_id");
        var tenantId = row.getLong("tenant_id");
        var id = row.getLong("id");
        return new Occurrence(id, systemId, tenantId, row.getLong("schedule_id"),
                row.getLong("schedule_version"), row.getString("schedule_code"),
                row.getString("schedule_name"), row.getLong("report_id"),
                row.getString("report_code"), row.getLong("owner_account_id"),
                row.getLong("owner_member_id"),
                occurrenceRecipients(systemId, tenantId, id),
                instant(row, "scheduled_at"), row.getString("occurrence_key"),
                OccurrenceStatus.valueOf(row.getString("status")),
                row.getInt("attempt_count"), row.getInt("max_attempts"),
                row.getObject("export_id", Long.class),
                row.getString("export_status"), row.getString("result_filename"),
                row.getObject("result_size", Long.class),
                row.getObject("total_rows", Long.class),
                row.getInt("processed_rows"), row.getBoolean("truncated"),
                row.getInt("delivered_recipient_count"),
                row.getString("failure_code"), row.getString("failure_message"),
                instant(row, "available_at"), instant(row, "lease_until"),
                row.getString("request_id"), row.getString("trace_id"),
                instant(row, "started_at"), instant(row, "finished_at"),
                instant(row, "created_at"), instant(row, "updated_at"),
                row.getLong("version"));
    }

    private Delivery delivery(ResultSet row, int ignored) throws SQLException {
        return new Delivery(row.getLong("id"), row.getLong("system_id"),
                row.getLong("tenant_id"), row.getLong("occurrence_id"),
                row.getLong("recipient_member_id"), row.getLong("message_id"),
                row.getString("delivery_key"), instant(row, "delivered_at"));
    }

    private List<Long> scheduleRecipients(
            long systemId, long tenantId, long scheduleId) {
        return jdbc.query("""
                SELECT recipient_member_id
                  FROM un_module_report_schedule_recipient
                 WHERE system_id=? AND tenant_id=? AND schedule_id=?
                 ORDER BY recipient_ordinal
                """, (row, ignored) -> row.getLong(1),
                systemId, tenantId, scheduleId);
    }

    private List<Long> occurrenceRecipients(
            long systemId, long tenantId, long occurrenceId) {
        return jdbc.query("""
                SELECT recipient_member_id
                  FROM un_module_report_schedule_occurrence_recipient
                 WHERE system_id=? AND tenant_id=? AND occurrence_id=?
                 ORDER BY recipient_ordinal
                """, (row, ignored) -> row.getLong(1),
                systemId, tenantId, occurrenceId);
    }

    private Object[] occurrenceArguments(Occurrence value) {
        return new Object[]{value.id(), value.systemId(), value.tenantId(),
                value.scheduleId(), value.scheduleVersion(), value.scheduleCode(),
                value.scheduleName(), value.reportId(), value.reportCode(),
                value.ownerAccountId(), value.ownerMemberId(),
                value.configuredRecipientMemberIds().size(), db(value.scheduledAt()),
                value.occurrenceKey(), value.status().name(), value.attemptCount(),
                value.maxAttempts(), value.exportId(), value.exportStatus(),
                value.resultFilename(), value.resultSize(), value.totalRows(),
                value.processedRows(), value.truncated(),
                value.deliveredRecipientCount(), value.failureCode(),
                value.failureMessage(), db(value.availableAt()),
                db(value.leaseUntil()), value.requestId(), value.traceId(),
                db(value.startedAt()), db(value.finishedAt()), db(value.createdAt()),
                db(value.updatedAt()), value.version()};
    }

    private String weekdays(List<DayOfWeek> values) {
        try {
            return json.writeValueAsString(
                    values.stream().map(DayOfWeek::getValue).sorted().toList());
        } catch (JsonProcessingException exception) {
            throw invalid("Report schedule weekdays are not serializable");
        }
    }

    private List<DayOfWeek> readWeekdays(String value) {
        try {
            return json.readValue(value, WEEKDAYS).stream()
                    .map(DayOfWeek::of).toList();
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored report schedule weekdays are invalid", exception);
        }
    }

    private static boolean sameOccurrence(Occurrence a, Occurrence b) {
        return a.systemId() == b.systemId() && a.tenantId() == b.tenantId()
                && a.scheduleId() == b.scheduleId()
                && a.scheduleVersion() == b.scheduleVersion()
                && a.scheduleCode().equals(b.scheduleCode())
                && a.scheduleName().equals(b.scheduleName())
                && a.reportId() == b.reportId()
                && a.reportCode().equals(b.reportCode())
                && a.ownerAccountId() == b.ownerAccountId()
                && a.ownerMemberId() == b.ownerMemberId()
                && a.configuredRecipientMemberIds()
                .equals(b.configuredRecipientMemberIds())
                && a.scheduledAt().equals(b.scheduledAt())
                && a.occurrenceKey().equals(b.occurrenceKey())
                && a.requestId().equals(b.requestId())
                && a.traceId().equals(b.traceId());
    }

    private static boolean sameDelivery(Delivery a, Delivery b) {
        return a.systemId() == b.systemId()
                && a.tenantId() == b.tenantId()
                && a.occurrenceId() == b.occurrenceId()
                && a.recipientMemberId() == b.recipientMemberId()
                && a.messageId() == b.messageId()
                && a.deliveryKey().equals(b.deliveryKey());
    }

    private static String columns(String alias) {
        return SCHEDULED_COLUMNS(alias, OCCURRENCE_COLUMNS);
    }

    private static String SCHEDULED_COLUMNS(String alias, String columns) {
        return columns.lines().flatMap(line -> List.of(line.split(",")).stream())
                .map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> alias + "." + value)
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private static LocalDateTime db(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String column)
            throws SQLException {
        var value = row.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String state(String value) {
        if (value == null || !value.matches("^[A-Z][A-Z0-9_]{1,31}$")) {
            throw invalid("Report schedule export state is invalid");
        }
        return value;
    }

    private static String failure(String value) {
        value = value == null ? "REPORT_SCHEDULE_FAILED" : value.strip();
        return value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "REPORT_SCHEDULE_FAILED";
    }

    private static String message(String value) {
        value = value == null ? "Report schedule failed" : value.strip();
        if (value.isEmpty()) value = "Report schedule failed";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static void lease(Instant now, Instant leaseUntil, int limit) {
        required(now, "claim time");
        required(leaseUntil, "lease time");
        if (!leaseUntil.isAfter(now)) {
            throw invalid("Report schedule lease must end after claim time");
        }
        claimLimit(limit);
    }

    private static void claimLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw invalid("Report schedule claim limit is invalid");
        }
    }

    private static void page(int limit, long offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw invalid("Report schedule page bounds are invalid");
        }
    }

    private static void required(Instant value, String label) {
        if (value == null) throw invalid("Report schedule " + label + " is required");
    }

    private static void changed(int count, String message) {
        if (count != 1) throw new IllegalStateException(message);
    }

    private static void mutate(int count, String message) {
        if (count != 1) throw conflict(message);
    }

    private static <T> Optional<T> one(List<T> rows, String label) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped report " + label + " lookup returned duplicates");
        }
        return rows.stream().findFirst();
    }

    private static ReportException conflict(String message) {
        return new ReportException("REPORT_SCHEDULE_CONFLICT", message);
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_SCHEDULE_INVALID", message);
    }

    private static ReportException notFound(String message) {
        return new ReportException("REPORT_SCHEDULE_NOT_FOUND", message);
    }
}
