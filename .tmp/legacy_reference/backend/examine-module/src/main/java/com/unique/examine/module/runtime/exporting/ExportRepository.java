package com.unique.examine.module.runtime.exporting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class ExportRepository {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final String COLUMNS = "id,system_id,tenant_id,logical_module_id,module_code,schema_version_id,"
            + "module_snapshot_id,schema_checksum,query_json,query_hash,field_codes_json,permission_snapshot_json,"
            + "status,total_rows,processed_rows,result_filename,result_size,failure_code,failure_message,job_id,"
            + "requested_by_account_id,requested_by_member_id,request_id,trace_id,started_at,finished_at,created_at,"
            + "updated_at,version";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ExportRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void insert(TaskRecord task) {
        jdbc.update("INSERT INTO un_module_export_task (id,system_id,tenant_id,logical_module_id,module_code,"
                        + "schema_version_id,module_snapshot_id,schema_checksum,query_json,query_hash,field_codes_json,"
                        + "permission_snapshot_json,status,total_rows,processed_rows,result_filename,result_content,"
                        + "result_size,failure_code,failure_message,job_id,requested_by_account_id,"
                        + "requested_by_member_id,request_id,trace_id,started_at,finished_at,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'QUEUED',NULL,0,NULL,NULL,NULL,NULL,NULL,?,?,?,?,?,NULL,NULL,?,?,0)",
                task.id(), task.systemId(), task.tenantId(), task.moduleId(), task.moduleCode(),
                task.schemaVersionId(), task.moduleSnapshotId(), task.schemaChecksum(), task.queryJson(),
                task.queryHash(), write(task.fieldCodes()), write(task.permissions().stream().sorted().toList()),
                task.jobId(), task.accountId(), task.memberId(), task.requestId(), task.traceId(),
                task.createdAt(), task.updatedAt());
    }

    public TaskRecord require(long id) {
        return find(id).orElseThrow(ExportRepository::notFound);
    }

    public Optional<TaskRecord> find(long id) {
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM un_module_export_task WHERE id=?",
                (result, row) -> task(result), id);
        return rows.stream().findFirst();
    }

    public long countOwned(long systemId, long tenantId, String moduleCode, long memberId) {
        var value = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_export_task WHERE system_id=? "
                        + "AND tenant_id=? AND module_code=? AND requested_by_member_id=?",
                Long.class, systemId, tenantId, moduleCode, memberId);
        return value == null ? 0L : value;
    }

    public List<TaskRecord> pageOwned(long systemId, long tenantId, String moduleCode, long memberId,
                                      int limit, long offset) {
        return jdbc.query("SELECT " + COLUMNS + " FROM un_module_export_task WHERE system_id=? AND tenant_id=? "
                        + "AND module_code=? AND requested_by_member_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (result, row) -> task(result), systemId, tenantId, moduleCode, memberId, limit, offset);
    }

    public void start(long id) {
        if (jdbc.update("UPDATE un_module_export_task SET status='RUNNING',started_at=?,updated_at=?,version=version+1 "
                + "WHERE id=? AND status='QUEUED'", LocalDateTime.now(), LocalDateTime.now(), id) != 1) {
            throw conflict();
        }
    }

    public void total(long id, int total) {
        if (jdbc.update("UPDATE un_module_export_task SET total_rows=?,updated_at=?,version=version+1 "
                + "WHERE id=? AND status='RUNNING'", total, LocalDateTime.now(), id) != 1) throw conflict();
    }

    public void complete(long id, int processed, String filename, byte[] content) {
        var now = LocalDateTime.now();
        if (jdbc.update("UPDATE un_module_export_task SET status='SUCCEEDED',processed_rows=?,result_filename=?,"
                        + "result_content=?,result_size=?,finished_at=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND status='RUNNING'",
                processed, filename, content, content.length, now, now, id) != 1) throw conflict();
    }

    public void fail(long id, String code, String message) {
        var now = LocalDateTime.now();
        jdbc.update("UPDATE un_module_export_task SET status='FAILED',failure_code=?,failure_message=?,"
                        + "finished_at=?,updated_at=?,version=version+1 WHERE id=? AND status IN ('QUEUED','RUNNING')",
                truncate(code, 64), truncate(message, 500), now, now, id);
    }

    public byte[] result(long id) {
        var rows = jdbc.query("SELECT result_content FROM un_module_export_task WHERE id=? AND status='SUCCEEDED'",
                (result, row) -> result.getBytes("result_content"), id);
        if (rows.isEmpty() || rows.getFirst() == null) throw notFound();
        return rows.getFirst();
    }

    private TaskRecord task(ResultSet result) throws SQLException {
        return new TaskRecord(result.getLong("id"), result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("logical_module_id"), result.getString("module_code"),
                result.getLong("schema_version_id"), result.getLong("module_snapshot_id"),
                result.getString("schema_checksum"), result.getString("query_json"),
                result.getString("query_hash"), readList(result.getString("field_codes_json")),
                Set.copyOf(readList(result.getString("permission_snapshot_json"))), result.getString("status"),
                nullableInt(result, "total_rows"), result.getInt("processed_rows"),
                result.getString("result_filename"), nullableLong(result, "result_size"),
                result.getString("failure_code"), result.getString("failure_message"), result.getLong("job_id"),
                result.getLong("requested_by_account_id"), result.getLong("requested_by_member_id"),
                result.getString("request_id"), result.getString("trace_id"),
                time(result, "started_at"), time(result, "finished_at"), time(result, "created_at"),
                time(result, "updated_at"), result.getLong("version"));
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Export task JSON is invalid", exception); }
    }

    private List<String> readList(String value) {
        try { return mapper.readValue(value, STRINGS); }
        catch (Exception exception) { throw new IllegalStateException("Stored export task JSON is invalid", exception); }
    }

    private static Integer nullableInt(ResultSet result, String column) throws SQLException {
        var value = result.getInt(column); return result.wasNull() ? null : value;
    }
    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        var value = result.getLong(column); return result.wasNull() ? null : value;
    }
    private static LocalDateTime time(ResultSet result, String column) throws SQLException {
        var value = result.getTimestamp(column); return value == null ? null : value.toLocalDateTime();
    }
    private static String truncate(String value, int size) {
        if (value == null) return null; return value.length() <= size ? value : value.substring(0, size);
    }
    private static BusinessException notFound() {
        return new BusinessException("EXPORT_NOT_FOUND", "Export task was not found", HttpStatus.NOT_FOUND);
    }
    private static BusinessException conflict() {
        return new BusinessException("EXPORT_TASK_CONFLICT", "Export task state changed", HttpStatus.CONFLICT);
    }

    public record TaskRecord(
            long id, long systemId, long tenantId, long moduleId, String moduleCode,
            long schemaVersionId, long moduleSnapshotId, String schemaChecksum,
            String queryJson, String queryHash, List<String> fieldCodes, Set<String> permissions,
            String status, Integer totalRows, int processedRows, String resultFilename, Long resultSize,
            String failureCode, String failureMessage, long jobId, long accountId, long memberId,
            String requestId, String traceId, LocalDateTime startedAt, LocalDateTime finishedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt, long version
    ) {
        public TaskRecord {
            fieldCodes = List.copyOf(fieldCodes);
            permissions = Set.copyOf(permissions);
        }
    }
}
