package com.unique.examine.module.report.exporting.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.exporting.ReportExportStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository("jdbcReportExportStore")
public class JdbcReportExportStore implements ReportExportStore {
    private static final int MAX_RESULT_BYTES = 128 * 1024 * 1024;
    private static final TypeReference<List<ReportFieldPin>> FIELDS =
            new TypeReference<>() {
            };
    static final String COLUMNS = """
            id,system_id,tenant_id,report_id,report_code,report_name,
            report_version_id,report_version_no,data_source_id,
            data_source_code,data_source_name,data_source_version_id,
            data_source_version_no,module_id,module_code,schema_version_id,
            fields_json,request_key_hash,job_id,requested_by_account_id,
            requested_by_member_id,status,total_rows,processed_rows,truncated,
            result_filename,result_size,error_code,error_message,request_id,
            trace_id,started_at,finished_at,created_at,updated_at,version
            """;
    static final String INSERT = """
            INSERT INTO un_module_report_export_run (
                id,system_id,tenant_id,report_id,report_version_id,
                report_version_no,report_code,report_name,data_source_id,
                data_source_version_id,data_source_version_no,data_source_code,
                data_source_name,module_id,module_code,schema_version_id,
                fields_json,fields_fingerprint,field_count,request_key_hash,
                requested_by_account_id,requested_by_member_id,job_id,status,
                total_rows,processed_rows,truncated,result_filename,
                result_content,result_size,error_code,error_message,request_id,
                trace_id,started_at,finished_at,created_at,updated_at,version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcReportExportStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<Run> findByRequestKey(
            long systemId,
            long tenantId,
            long requestedByMemberId,
            long reportId,
            String requestKeyHash
    ) {
        return one(jdbc.query(
                "SELECT " + COLUMNS
                        + " FROM un_module_report_export_run "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND requested_by_member_id=? AND report_id=? "
                        + "AND request_key_hash=?",
                this::run, systemId, tenantId, requestedByMemberId, reportId,
                requestKeyHash));
    }

    @Override
    public Optional<Run> findById(
            long systemId, long tenantId, long exportId
    ) {
        return one(jdbc.query(
                "SELECT " + COLUMNS
                        + " FROM un_module_report_export_run "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                this::run, systemId, tenantId, exportId));
    }

    @Override
    public long countOwned(
            long systemId,
            long tenantId,
            long reportId,
            long requestedByMemberId
    ) {
        var value = jdbc.queryForObject("""
                        SELECT COUNT(*)
                          FROM un_module_report_export_run
                         WHERE system_id=? AND tenant_id=? AND report_id=?
                           AND requested_by_member_id=?
                        """, Long.class,
                systemId, tenantId, reportId, requestedByMemberId);
        return value == null ? 0L : value;
    }

    @Override
    public List<Run> pageOwned(
            long systemId,
            long tenantId,
            long reportId,
            long requestedByMemberId,
            int limit,
            long offset
    ) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException(
                    "Report export page bounds are invalid");
        }
        return jdbc.query(
                "SELECT " + COLUMNS
                        + " FROM un_module_report_export_run "
                        + "WHERE system_id=? AND tenant_id=? AND report_id=? "
                        + "AND requested_by_member_id=? "
                        + "ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                this::run, systemId, tenantId, reportId,
                requestedByMemberId, limit, offset);
    }

    @Override
    public void insert(Run run) {
        Objects.requireNonNull(run, "run");
        if (run.status() != Status.QUEUED || run.version() != 0
                || run.totalRows() != null || run.processedRows() != 0
                || run.truncated() || run.startedAt() != null
                || run.finishedAt() != null) {
            throw invalid("A new report export must be queued at version zero");
        }
        var fieldsJson = writeFields(run.source().fields());
        try {
            requireOne(jdbc.update(INSERT,
                            run.id(), run.systemId(), run.tenantId(),
                            run.reportId(), run.reportVersionId(),
                            run.reportVersionNumber(), run.reportCode(),
                            run.reportName(), run.source().dataSourceId(),
                            run.source().dataSourceVersionId(),
                            run.source().dataSourceVersionNumber(),
                            run.source().dataSourceCode(),
                            run.source().dataSourceName(),
                            run.source().moduleId(),
                            run.source().moduleCode(),
                            run.source().schemaVersionId(), fieldsJson,
                            sha256(fieldsJson), run.source().fields().size(),
                            run.requestKeyHash(), run.requestedByAccountId(),
                            run.requestedByMemberId(), run.jobId(),
                            run.status().name(), run.totalRows(),
                            run.processedRows(), run.truncated(), null, null,
                            null, null, null, run.requestId(), run.traceId(),
                            run.startedAt(), run.finishedAt(), run.createdAt(),
                            run.updatedAt(), run.version()),
                    "Report export insert did not affect one row");
        } catch (DuplicateKeyException exception) {
            var replay = findByRequestKey(
                    run.systemId(), run.tenantId(),
                    run.requestedByMemberId(), run.reportId(),
                    run.requestKeyHash());
            if (replay.isPresent()
                    && sameAcceptedRun(replay.orElseThrow(), run)) {
                return;
            }
            throw conflict("Report export request or job is already in use");
        } catch (DataIntegrityViolationException exception) {
            throw invalid(
                    "Report export violated an immutable scoped reference");
        }
    }

    @Override
    public Run start(
            long exportId, long expectedVersion, LocalDateTime now
    ) {
        requiredTime(now);
        mutate(jdbc.update("""
                        UPDATE un_module_report_export_run
                           SET status='RUNNING',
                               started_at=COALESCE(started_at,?),
                               updated_at=?,version=version+1
                         WHERE id=? AND version=? AND status='QUEUED'
                        """, now, now, exportId, expectedVersion),
                "Report export could not start");
        return requireByGlobalId(exportId);
    }

    @Override
    public Run requeue(
            long exportId, long expectedVersion, LocalDateTime now
    ) {
        requiredTime(now);
        mutate(jdbc.update("""
                        UPDATE un_module_report_export_run
                           SET status='QUEUED',updated_at=?,version=version+1
                         WHERE id=? AND version=? AND status='RUNNING'
                           AND finished_at IS NULL
                        """, now, exportId, expectedVersion),
                "Report export could not be requeued");
        return requireByGlobalId(exportId);
    }

    @Override
    public Run complete(
            long exportId,
            long expectedVersion,
            long totalRows,
            int processedRows,
            boolean truncated,
            String resultFilename,
            byte[] content,
            LocalDateTime now
    ) {
        requiredTime(now);
        if (totalRows < 0 || processedRows < 0 || processedRows > MAX_ROWS
                || totalRows < processedRows
                || truncated != (totalRows > processedRows)
                || resultFilename == null
                || !resultFilename.endsWith(".xlsx")
                || resultFilename.isBlank() || resultFilename.length() > 180
                || content == null || content.length == 0
                || content.length > MAX_RESULT_BYTES) {
            throw invalid("Report export result is invalid");
        }
        try {
            mutate(jdbc.update("""
                            UPDATE un_module_report_export_run
                               SET status='SUCCEEDED',total_rows=?,
                                   processed_rows=?,truncated=?,
                                   result_filename=?,result_content=?,
                                   result_size=?,finished_at=?,updated_at=?,
                                   version=version+1
                             WHERE id=? AND version=? AND status='RUNNING'
                            """, totalRows, processedRows, truncated,
                            resultFilename, content, content.length, now, now,
                            exportId, expectedVersion),
                    "Report export could not complete");
            return requireByGlobalId(exportId);
        } catch (DataIntegrityViolationException exception) {
            throw invalid("Report export result violated its bounded shape");
        }
    }

    @Override
    public Run fail(
            long exportId,
            long expectedVersion,
            String failureCode,
            String failureMessage,
            LocalDateTime now
    ) {
        requiredTime(now);
        var code = failureCode(failureCode);
        var message = failureMessage(failureMessage);
        mutate(jdbc.update("""
                        UPDATE un_module_report_export_run
                           SET status='FAILED',total_rows=NULL,
                               processed_rows=0,truncated=FALSE,
                               result_filename=NULL,result_content=NULL,
                               result_size=NULL,error_code=?,error_message=?,
                               finished_at=?,updated_at=?,version=version+1
                         WHERE id=? AND version=?
                           AND status IN ('QUEUED','RUNNING')
                        """, code, message, now, now,
                exportId, expectedVersion),
                "Report export could not fail");
        return requireByGlobalId(exportId);
    }

    @Override
    public byte[] result(
            long systemId, long tenantId, long exportId
    ) {
        var rows = jdbc.query("""
                        SELECT result_content
                          FROM un_module_report_export_run
                         WHERE system_id=? AND tenant_id=? AND id=?
                           AND status='SUCCEEDED'
                        """,
                (result, rowNumber) -> result.getBytes("result_content"),
                systemId, tenantId, exportId);
        if (rows.size() != 1 || rows.getFirst() == null) {
            throw notFound("Report export result was not found");
        }
        return rows.getFirst();
    }

    private Run requireByGlobalId(long exportId) {
        return one(jdbc.query(
                "SELECT " + COLUMNS
                        + " FROM un_module_report_export_run WHERE id=?",
                this::run, exportId))
                .orElseThrow(() -> notFound("Report export was not found"));
    }

    private Run run(ResultSet result, int rowNumber) throws SQLException {
        var source = new ReportSourcePin(
                result.getLong("data_source_id"),
                result.getString("data_source_code"),
                result.getString("data_source_name"),
                result.getLong("data_source_version_id"),
                result.getInt("data_source_version_no"),
                result.getLong("module_id"),
                result.getString("module_code"),
                result.getString("schema_version_id"),
                readFields(result.getString("fields_json")));
        return new Run(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("report_id"),
                result.getString("report_code"),
                result.getString("report_name"),
                result.getLong("report_version_id"),
                result.getInt("report_version_no"), source,
                result.getString("request_key_hash"),
                result.getLong("job_id"),
                result.getLong("requested_by_account_id"),
                result.getLong("requested_by_member_id"),
                Status.valueOf(result.getString("status")),
                result.getObject("total_rows", Long.class),
                result.getInt("processed_rows"),
                result.getBoolean("truncated"),
                result.getString("result_filename"),
                result.getObject("result_size", Long.class),
                result.getString("error_code"),
                result.getString("error_message"),
                result.getString("request_id"),
                result.getString("trace_id"),
                result.getObject("started_at", LocalDateTime.class),
                result.getObject("finished_at", LocalDateTime.class),
                result.getObject("created_at", LocalDateTime.class),
                result.getObject("updated_at", LocalDateTime.class),
                result.getLong("version"));
    }

    private String writeFields(List<ReportFieldPin> fields) {
        try {
            return json.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw invalid("Report export fields are not JSON serializable");
        }
    }

    private List<ReportFieldPin> readFields(String value) {
        try {
            return List.copyOf(json.readValue(value, FIELDS));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored report export fields JSON is invalid", exception);
        }
    }

    private static boolean sameAcceptedRun(Run existing, Run requested) {
        return existing.id() == requested.id()
                && existing.systemId() == requested.systemId()
                && existing.tenantId() == requested.tenantId()
                && existing.reportId() == requested.reportId()
                && existing.reportCode().equals(requested.reportCode())
                && existing.reportName().equals(requested.reportName())
                && existing.reportVersionId() == requested.reportVersionId()
                && existing.reportVersionNumber()
                == requested.reportVersionNumber()
                && existing.source().equals(requested.source())
                && existing.requestKeyHash().equals(requested.requestKeyHash())
                && existing.jobId() == requested.jobId()
                && existing.requestedByAccountId()
                == requested.requestedByAccountId()
                && existing.requestedByMemberId()
                == requested.requestedByMemberId()
                && existing.requestId().equals(requested.requestId())
                && existing.traceId().equals(requested.traceId());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void mutate(int affected, String message) {
        if (affected != 1) {
            throw conflict(message);
        }
    }

    private static void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static void requiredTime(LocalDateTime value) {
        if (value == null) {
            throw invalid("Report export transition time is required");
        }
    }

    private static String failureCode(String value) {
        if (value == null) {
            return "REPORT_EXPORT_FAILED";
        }
        value = value.strip();
        return value.matches("^[A-Z][A-Z0-9_]{1,99}$")
                ? value : "REPORT_EXPORT_FAILED";
    }

    private static String failureMessage(String value) {
        value = value == null ? "Report export failed" : value.strip();
        if (value.isEmpty()) {
            value = "Report export failed";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped report export lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }

    private static ReportException conflict(String message) {
        return new ReportException("REPORT_EXPORT_CONFLICT", message);
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_EXPORT_INVALID", message);
    }

    private static ReportException notFound(String message) {
        return new ReportException("REPORT_EXPORT_NOT_FOUND", message);
    }
}
