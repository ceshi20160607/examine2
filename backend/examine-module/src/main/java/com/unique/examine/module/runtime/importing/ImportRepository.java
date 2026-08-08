package com.unique.examine.module.runtime.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ImportRepository {
    private static final TypeReference<Map<String, JsonNode>> ROW_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ImportRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void insert(BatchRecord batch, List<RowRecord> rows) {
        jdbc.update("INSERT INTO un_module_import_batch (id,system_id,tenant_id,logical_module_id,module_code,"
                        + "schema_version_id,module_snapshot_id,schema_checksum,import_mode,match_field_code,"
                        + "request_hash,status,total_rows,new_rows,update_rows,failed_rows,preview_job_id,"
                        + "commit_job_id,rollback_job_id,requested_by_account_id,requested_by_member_id,request_id,"
                        + "trace_id,committed_at,rolled_back_at,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'PREVIEW_QUEUED',?,0,0,0,?,NULL,NULL,?,?,?,?,NULL,NULL,?,?,0)",
                batch.id(), batch.systemId(), batch.tenantId(), batch.moduleId(), batch.moduleCode(),
                batch.schemaVersionId(), batch.moduleSnapshotId(), batch.schemaChecksum(), batch.mode(),
                batch.matchFieldCode(), batch.requestHash(), batch.totalRows(), batch.previewJobId(),
                batch.accountId(), batch.memberId(), batch.requestId(), batch.traceId(),
                batch.createdAt(), batch.updatedAt());
        for (var row : rows) {
            jdbc.update("INSERT INTO un_module_import_row (id,batch_id,system_id,tenant_id,source_row_number,planned_action,"
                            + "row_status,input_json,unique_fingerprint_json,error_code,error_message,target_record_id,"
                            + "target_before_json,target_after_version,created_at,updated_at,version) "
                            + "VALUES (?,?,?,?,?,NULL,'PREVIEW_PENDING',?,NULL,NULL,NULL,NULL,NULL,NULL,?,?,0)",
                    row.id(), row.batchId(), row.systemId(), row.tenantId(), row.rowNumber(), write(row.input()),
                    row.createdAt(), row.updatedAt());
        }
    }

    public BatchRecord require(long batchId) {
        return find(batchId).orElseThrow(ImportRepository::notFound);
    }

    public Optional<BatchRecord> find(long batchId) {
        var rows = jdbc.query("SELECT * FROM un_module_import_batch WHERE id=?",
                (result, row) -> batch(result), batchId);
        return rows.stream().findFirst();
    }

    public List<RowRecord> rows(long batchId) {
        return jdbc.query("SELECT * FROM un_module_import_row WHERE batch_id=? ORDER BY source_row_number",
                (result, row) -> row(result), batchId);
    }

    public long countOwned(long systemId, long tenantId, String moduleCode, long memberId) {
        var value = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_import_batch WHERE system_id=? "
                        + "AND tenant_id=? AND module_code=? AND requested_by_member_id=?",
                Long.class, systemId, tenantId, moduleCode, memberId);
        return value == null ? 0L : value;
    }

    public List<BatchRecord> pageOwned(long systemId, long tenantId, String moduleCode, long memberId,
                                       int limit, long offset) {
        return jdbc.query("SELECT * FROM un_module_import_batch WHERE system_id=? AND tenant_id=? "
                        + "AND module_code=? AND requested_by_member_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (result, row) -> batch(result), systemId, tenantId, moduleCode, memberId, limit, offset);
    }

    public void transition(long batchId, String from, String to) {
        var changed = jdbc.update("UPDATE un_module_import_batch SET status=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND status=?", to, LocalDateTime.now(), batchId, from);
        if (changed != 1) throw conflict();
    }

    public void savePreview(RowPreview preview) {
        jdbc.update("UPDATE un_module_import_row SET planned_action=?,row_status=?,unique_fingerprint_json=?,"
                        + "error_code=?,error_message=?,target_record_id=?,target_after_version=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND row_status='PREVIEW_PENDING'",
                preview.action(), preview.status(), writeAny(preview.fingerprints()), preview.errorCode(),
                truncate(preview.errorMessage(), 500), preview.targetRecordId(), preview.targetVersion(),
                LocalDateTime.now(), preview.rowId());
    }

    public void finishPreview(long batchId, int newRows, int updateRows, int failedRows) {
        var status = failedRows == 0 ? "READY" : "INVALID";
        var changed = jdbc.update("UPDATE un_module_import_batch SET status=?,new_rows=?,update_rows=?,failed_rows=?,"
                        + "updated_at=?,version=version+1 WHERE id=? AND status='PREVIEWING'",
                status, newRows, updateRows, failedRows, LocalDateTime.now(), batchId);
        if (changed != 1) throw conflict();
    }

    public boolean attachJob(long batchId, String expectedStatus, String queuedStatus, String column, long jobId) {
        if (!List.of("commit_job_id", "rollback_job_id").contains(column)) throw new IllegalArgumentException();
        return jdbc.update("UPDATE un_module_import_batch SET " + column + "=?,status=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND status=? AND " + column + " IS NULL",
                jobId, queuedStatus, LocalDateTime.now(), batchId, expectedStatus) == 1;
    }

    public void saveCommittedRow(long rowId, long targetRecordId, JsonNode before, long afterVersion) {
        var changed = jdbc.update("UPDATE un_module_import_row SET row_status='COMMITTED',target_record_id=?,"
                        + "target_before_json=?,target_after_version=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND row_status='VALID'",
                targetRecordId, before == null ? null : writeAny(before), afterVersion, LocalDateTime.now(), rowId);
        if (changed != 1) throw conflict();
    }

    public void finishCommit(long batchId) {
        var now = LocalDateTime.now();
        var changed = jdbc.update("UPDATE un_module_import_batch SET status='COMMITTED',committed_at=?,updated_at=?,"
                        + "version=version+1 WHERE id=? AND status='COMMITTING'", now, now, batchId);
        if (changed != 1) throw conflict();
    }

    public void saveRolledBackRow(long rowId, long afterVersion) {
        var changed = jdbc.update("UPDATE un_module_import_row SET row_status='ROLLED_BACK',target_after_version=?,"
                        + "updated_at=?,version=version+1 WHERE id=? AND row_status='COMMITTED'",
                afterVersion, LocalDateTime.now(), rowId);
        if (changed != 1) throw conflict();
    }

    public void finishRollback(long batchId) {
        var now = LocalDateTime.now();
        var changed = jdbc.update("UPDATE un_module_import_batch SET status='ROLLED_BACK',rolled_back_at=?,updated_at=?,"
                        + "version=version+1 WHERE id=? AND status='ROLLING_BACK'", now, now, batchId);
        if (changed != 1) throw conflict();
    }

    public void markFailed(long batchId, String expectedStatus) {
        jdbc.update("UPDATE un_module_import_batch SET status='FAILED',updated_at=?,version=version+1 "
                + "WHERE id=? AND status=?", LocalDateTime.now(), batchId, expectedStatus);
    }

    private BatchRecord batch(ResultSet result) throws SQLException {
        return new BatchRecord(result.getLong("id"), result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("logical_module_id"), result.getString("module_code"),
                result.getLong("schema_version_id"), result.getLong("module_snapshot_id"),
                result.getString("schema_checksum"), result.getString("import_mode"),
                result.getString("match_field_code"), result.getString("request_hash"), result.getString("status"),
                result.getInt("total_rows"), result.getInt("new_rows"), result.getInt("update_rows"),
                result.getInt("failed_rows"), result.getLong("preview_job_id"),
                nullableLong(result, "commit_job_id"), nullableLong(result, "rollback_job_id"),
                result.getLong("requested_by_account_id"), result.getLong("requested_by_member_id"),
                result.getString("request_id"), result.getString("trace_id"),
                result.getObject("created_at", LocalDateTime.class), result.getObject("updated_at", LocalDateTime.class));
    }

    private RowRecord row(ResultSet result) throws SQLException {
        return new RowRecord(result.getLong("id"), result.getLong("batch_id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getInt("source_row_number"), result.getString("planned_action"),
                result.getString("row_status"), readRow(result.getString("input_json")),
                result.getString("unique_fingerprint_json"), result.getString("error_code"),
                result.getString("error_message"), nullableLong(result, "target_record_id"),
                readNode(result.getString("target_before_json")), nullableLong(result, "target_after_version"),
                result.getObject("created_at", LocalDateTime.class), result.getObject("updated_at", LocalDateTime.class));
    }

    private Map<String, JsonNode> readRow(String json) {
        try { return Map.copyOf(mapper.readValue(json, ROW_MAP)); }
        catch (Exception exception) { throw new IllegalStateException("Import row JSON is invalid", exception); }
    }

    private JsonNode readNode(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); }
        catch (Exception exception) { throw new IllegalStateException("Import snapshot JSON is invalid", exception); }
    }

    private String write(Object value) { return writeAny(value); }
    private String writeAny(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Import data must be JSON serializable", exception); }
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        var value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static BusinessException notFound() {
        return new BusinessException("IMPORT_BATCH_NOT_FOUND", "Import batch was not found", HttpStatus.NOT_FOUND);
    }
    private static BusinessException conflict() {
        return new BusinessException("IMPORT_BATCH_CONFLICT", "Import batch state changed", HttpStatus.CONFLICT);
    }
    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record BatchRecord(long id, long systemId, long tenantId, long moduleId, String moduleCode,
                              long schemaVersionId, long moduleSnapshotId, String schemaChecksum, String mode,
                              String matchFieldCode, String requestHash, String status, int totalRows,
                              int newRows, int updateRows, int failedRows, long previewJobId, Long commitJobId,
                              Long rollbackJobId, long accountId, long memberId, String requestId, String traceId,
                              LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record RowRecord(long id, long batchId, long systemId, long tenantId, int rowNumber, String action,
                            String status, Map<String, JsonNode> input, String fingerprintsJson, String errorCode,
                            String errorMessage, Long targetRecordId, JsonNode before, Long targetAfterVersion,
                            LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record RowPreview(long rowId, String action, String status, Object fingerprints,
                             String errorCode, String errorMessage, Long targetRecordId, Long targetVersion) { }
}
