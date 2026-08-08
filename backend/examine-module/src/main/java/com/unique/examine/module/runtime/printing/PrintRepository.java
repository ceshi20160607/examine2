package com.unique.examine.module.runtime.printing;

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

@Repository
public class PrintRepository {
    private static final String TEMPLATE_COLUMNS = "t.id,t.system_id,t.logical_module_id,t.module_code,"
            + "t.template_code,t.template_name,t.desired_status,t.paper_size,t.orientation,t.definition_json,"
            + "t.published_version_id,t.created_at,t.created_by,t.updated_at,t.updated_by,t.version,"
            + "v.version_no published_version_no,v.schema_version_id published_schema_version_id";
    private static final String VERSION_COLUMNS = "id,system_id,template_id,logical_module_id,module_code,"
            + "template_code,template_name,version_no,schema_version_id,module_snapshot_id,paper_size,orientation,"
            + "definition_json,definition_checksum,published_at,published_by";
    private static final String TASK_COLUMNS = "id,system_id,tenant_id,logical_module_id,module_code,record_id,"
            + "record_version,record_no,template_id,template_version_id,template_version_no,template_code,"
            + "template_name,schema_version_id,module_snapshot_id,snapshot_json,status,result_filename,result_size,"
            + "failure_code,failure_message,job_id,requested_by_account_id,requested_by_member_id,request_id,trace_id,"
            + "started_at,finished_at,created_at,updated_at,version";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PrintRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public ModuleRef requireModule(long systemId, long moduleId) {
        var rows = jdbc.query("SELECT id,module_code,module_name FROM un_module_definition "
                        + "WHERE system_id=? AND id=? AND deleted_at IS NULL",
                (result, row) -> new ModuleRef(result.getLong("id"), result.getString("module_code"),
                        result.getString("module_name")), systemId, moduleId);
        if (rows.isEmpty()) throw notFound("PRINT_MODULE_NOT_FOUND", "Module was not found");
        return rows.getFirst();
    }

    public List<FieldRef> draftFields(long systemId, long moduleId) {
        return jdbc.query("SELECT field_code,field_name,field_type,is_hidden FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? AND desired_status='ENABLED' AND deleted_at IS NULL "
                        + "ORDER BY sort_order,id",
                (result, row) -> new FieldRef(result.getString("field_code"), result.getString("field_name"),
                        result.getString("field_type"), result.getBoolean("is_hidden")), systemId, moduleId);
    }

    public ActiveSchema requireActiveSchema(long systemId, long moduleId) {
        var rows = jdbc.query("SELECT r.active_version_id,m.module_snapshot_id,m.module_code,m.module_name "
                        + "FROM un_module_config_root r JOIN un_module_runtime_schema_module m "
                        + "ON m.system_id=r.system_id AND m.schema_version_id=r.active_version_id "
                        + "WHERE r.system_id=? AND m.logical_module_id=?",
                (result, row) -> new ActiveSchema(result.getLong("active_version_id"),
                        result.getLong("module_snapshot_id"), result.getString("module_code"),
                        result.getString("module_name")), systemId, moduleId);
        if (rows.isEmpty()) throw conflict("PRINT_MODULE_NOT_PUBLISHED", "Module must be published before template publication");
        return rows.getFirst();
    }

    public List<FieldRef> publishedFields(long systemId, long schemaVersionId, long moduleSnapshotId) {
        return jdbc.query("SELECT field_code,field_name,field_type,FALSE is_hidden "
                        + "FROM un_module_runtime_schema_field WHERE system_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND field_scope='RECORD' ORDER BY field_snapshot_id",
                (result, row) -> new FieldRef(result.getString("field_code"), result.getString("field_name"),
                        result.getString("field_type"), false), systemId, schemaVersionId, moduleSnapshotId);
    }

    public void insertTemplate(TemplateRecord value) {
        jdbc.update("INSERT INTO un_module_print_template (id,system_id,logical_module_id,module_code,template_code,"
                        + "template_name,desired_status,paper_size,orientation,definition_json,published_version_id,"
                        + "created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,NULL,?,?,?,?,0)",
                value.id(), value.systemId(), value.moduleId(), value.moduleCode(), value.code(), value.name(),
                value.status(), value.paperSize(), value.orientation(), value.definitionJson(), value.createdAt(),
                value.createdBy(), value.updatedAt(), value.updatedBy());
    }

    public List<TemplateRecord> templates(long systemId, long moduleId) {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM un_module_print_template t "
                        + "LEFT JOIN un_module_print_template_version v ON v.system_id=t.system_id "
                        + "AND v.id=t.published_version_id WHERE t.system_id=? AND t.logical_module_id=? "
                        + "AND t.deleted_at IS NULL ORDER BY t.updated_at DESC,t.id DESC",
                (result, row) -> template(result), systemId, moduleId);
    }

    public TemplateRecord requireTemplate(long systemId, long templateId) {
        var rows = jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM un_module_print_template t "
                        + "LEFT JOIN un_module_print_template_version v ON v.system_id=t.system_id "
                        + "AND v.id=t.published_version_id WHERE t.system_id=? AND t.id=? AND t.deleted_at IS NULL",
                (result, row) -> template(result), systemId, templateId);
        if (rows.isEmpty()) throw notFound("PRINT_TEMPLATE_NOT_FOUND", "Print template was not found");
        return rows.getFirst();
    }

    public void updateTemplate(long systemId, long templateId, String name, String status, String paperSize,
                               String orientation, String definitionJson, long accountId, long expectedVersion) {
        var now = LocalDateTime.now();
        if (jdbc.update("UPDATE un_module_print_template SET template_name=?,desired_status=?,paper_size=?,"
                        + "orientation=?,definition_json=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE system_id=? AND id=? AND deleted_at IS NULL AND version=?",
                name, status, paperSize, orientation, definitionJson, now, accountId, systemId, templateId,
                expectedVersion) != 1) throw conflict("PRINT_TEMPLATE_CONFLICT", "Print template changed; reload and retry");
    }

    public long nextVersionNo(long systemId, long templateId) {
        var value = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM un_module_print_template_version "
                + "WHERE system_id=? AND template_id=?", Long.class, systemId, templateId);
        return value == null ? 1L : value;
    }

    public void insertVersion(TemplateVersionRecord value) {
        jdbc.update("INSERT INTO un_module_print_template_version (id,system_id,template_id,logical_module_id,"
                        + "module_code,template_code,template_name,version_no,schema_version_id,module_snapshot_id,"
                        + "paper_size,orientation,definition_json,definition_checksum,published_at,published_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                value.id(), value.systemId(), value.templateId(), value.moduleId(), value.moduleCode(), value.code(),
                value.name(), value.versionNo(), value.schemaVersionId(), value.moduleSnapshotId(), value.paperSize(),
                value.orientation(), value.definitionJson(), value.definitionChecksum(), value.publishedAt(),
                value.publishedBy());
    }

    public void pointPublishedVersion(long systemId, long templateId, long versionId, long accountId,
                                      long expectedVersion) {
        if (jdbc.update("UPDATE un_module_print_template SET published_version_id=?,updated_at=?,updated_by=?,"
                        + "version=version+1 WHERE system_id=? AND id=? AND deleted_at IS NULL AND version=?",
                versionId, LocalDateTime.now(), accountId, systemId, templateId, expectedVersion) != 1) {
            throw conflict("PRINT_TEMPLATE_CONFLICT", "Print template changed; reload and retry");
        }
    }

    public List<TemplateVersionRecord> runtimeTemplates(long systemId, String moduleCode, long schemaVersionId) {
        return jdbc.query("SELECT " + prefixedVersionColumns("v") + " FROM un_module_print_template t "
                        + "JOIN un_module_print_template_version v ON v.system_id=t.system_id "
                        + "AND v.id=t.published_version_id WHERE t.system_id=? AND t.module_code=? "
                        + "AND t.desired_status='ENABLED' AND t.deleted_at IS NULL AND v.schema_version_id=? "
                        + "ORDER BY t.template_name,t.id",
                (result, row) -> version(result), systemId, moduleCode, schemaVersionId);
    }

    public TemplateVersionRecord requireRuntimeTemplate(long systemId, String moduleCode, String templateCode,
                                                         long schemaVersionId) {
        var rows = jdbc.query("SELECT " + prefixedVersionColumns("v") + " FROM un_module_print_template t "
                        + "JOIN un_module_print_template_version v ON v.system_id=t.system_id "
                        + "AND v.id=t.published_version_id WHERE t.system_id=? AND t.module_code=? "
                        + "AND t.template_code=? AND t.desired_status='ENABLED' AND t.deleted_at IS NULL "
                        + "AND v.schema_version_id=?",
                (result, row) -> version(result), systemId, moduleCode, templateCode, schemaVersionId);
        if (rows.isEmpty()) throw notFound("PRINT_TEMPLATE_NOT_FOUND", "Published print template was not found");
        return rows.getFirst();
    }

    public void insertTask(TaskRecord value) {
        jdbc.update("INSERT INTO un_module_print_task (id,system_id,tenant_id,logical_module_id,module_code,record_id,"
                        + "record_version,record_no,template_id,template_version_id,template_version_no,template_code,"
                        + "template_name,schema_version_id,module_snapshot_id,snapshot_json,status,result_filename,"
                        + "result_content,result_size,failure_code,failure_message,job_id,requested_by_account_id,"
                        + "requested_by_member_id,request_id,trace_id,started_at,finished_at,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'QUEUED',NULL,NULL,NULL,NULL,NULL,?,?,?,?,?,NULL,NULL,?,?,0)",
                value.id(), value.systemId(), value.tenantId(), value.moduleId(), value.moduleCode(), value.recordId(),
                value.recordVersion(), value.recordNo(), value.templateId(), value.templateVersionId(),
                value.templateVersionNo(), value.templateCode(), value.templateName(), value.schemaVersionId(),
                value.moduleSnapshotId(), value.snapshotJson(), value.jobId(), value.accountId(), value.memberId(),
                value.requestId(), value.traceId(), value.createdAt(), value.updatedAt());
    }

    public TaskRecord requireTask(long id) {
        var rows = jdbc.query("SELECT " + TASK_COLUMNS + " FROM un_module_print_task WHERE id=?",
                (result, row) -> task(result), id);
        if (rows.isEmpty()) throw notFound("PRINT_NOT_FOUND", "Print record was not found");
        return rows.getFirst();
    }

    public long countTasks(long systemId, long tenantId, String moduleCode, long recordId, long memberId) {
        var value = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_print_task WHERE system_id=? AND tenant_id=? "
                        + "AND module_code=? AND record_id=? AND requested_by_member_id=?",
                Long.class, systemId, tenantId, moduleCode, recordId, memberId);
        return value == null ? 0L : value;
    }

    public List<TaskRecord> pageTasks(long systemId, long tenantId, String moduleCode, long recordId, long memberId,
                                      int limit, long offset) {
        return jdbc.query("SELECT " + TASK_COLUMNS + " FROM un_module_print_task WHERE system_id=? AND tenant_id=? "
                        + "AND module_code=? AND record_id=? AND requested_by_member_id=? "
                        + "ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (result, row) -> task(result), systemId, tenantId, moduleCode, recordId, memberId, limit, offset);
    }

    public void startTask(long id) {
        if (jdbc.update("UPDATE un_module_print_task SET status='RUNNING',started_at=?,updated_at=?,version=version+1 "
                + "WHERE id=? AND status='QUEUED'", LocalDateTime.now(), LocalDateTime.now(), id) != 1) {
            throw conflict("PRINT_TASK_CONFLICT", "Print task state changed");
        }
    }

    public void completeTask(long id, String filename, byte[] content) {
        var now = LocalDateTime.now();
        if (jdbc.update("UPDATE un_module_print_task SET status='SUCCEEDED',result_filename=?,result_content=?,"
                        + "result_size=?,finished_at=?,updated_at=?,version=version+1 WHERE id=? AND status='RUNNING'",
                filename, content, content.length, now, now, id) != 1) {
            throw conflict("PRINT_TASK_CONFLICT", "Print task state changed");
        }
    }

    public void failTask(long id, String code, String message) {
        var now = LocalDateTime.now();
        jdbc.update("UPDATE un_module_print_task SET status='FAILED',failure_code=?,failure_message=?,finished_at=?,"
                        + "updated_at=?,version=version+1 WHERE id=? AND status IN ('QUEUED','RUNNING')",
                truncate(code, 64), truncate(message, 500), now, now, id);
    }

    public byte[] result(long id) {
        var rows = jdbc.query("SELECT result_content FROM un_module_print_task WHERE id=? AND status='SUCCEEDED'",
                (result, row) -> result.getBytes("result_content"), id);
        if (rows.isEmpty() || rows.getFirst() == null) throw notFound("PRINT_NOT_FOUND", "Print result was not found");
        return rows.getFirst();
    }

    public PrintViews.TemplateDefinition definition(String json) {
        return read(json, PrintViews.TemplateDefinition.class, "Stored print template definition is invalid");
    }

    public PrintViews.Snapshot snapshot(String json) {
        return read(json, PrintViews.Snapshot.class, "Stored print snapshot is invalid");
    }

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Print payload is invalid", exception); }
    }

    private <T> T read(String value, Class<T> type, String message) {
        try { return mapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException(message, exception); }
    }

    private TemplateRecord template(ResultSet result) throws SQLException {
        return new TemplateRecord(result.getLong("id"), result.getLong("system_id"),
                result.getLong("logical_module_id"), result.getString("module_code"),
                result.getString("template_code"), result.getString("template_name"),
                result.getString("desired_status"), result.getString("paper_size"),
                result.getString("orientation"), result.getString("definition_json"),
                nullableLong(result, "published_version_id"), nullableLong(result, "published_version_no"),
                nullableLong(result, "published_schema_version_id"), time(result, "created_at"),
                result.getLong("created_by"), time(result, "updated_at"), result.getLong("updated_by"),
                result.getLong("version"));
    }

    private TemplateVersionRecord version(ResultSet result) throws SQLException {
        return new TemplateVersionRecord(result.getLong("id"), result.getLong("system_id"),
                result.getLong("template_id"), result.getLong("logical_module_id"),
                result.getString("module_code"), result.getString("template_code"),
                result.getString("template_name"), result.getLong("version_no"),
                result.getLong("schema_version_id"), result.getLong("module_snapshot_id"),
                result.getString("paper_size"), result.getString("orientation"),
                result.getString("definition_json"), result.getString("definition_checksum"),
                time(result, "published_at"), result.getLong("published_by"));
    }

    private TaskRecord task(ResultSet result) throws SQLException {
        return new TaskRecord(result.getLong("id"), result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("logical_module_id"), result.getString("module_code"), result.getLong("record_id"),
                result.getLong("record_version"), result.getString("record_no"), result.getLong("template_id"),
                result.getLong("template_version_id"), result.getLong("template_version_no"),
                result.getString("template_code"), result.getString("template_name"),
                result.getLong("schema_version_id"), result.getLong("module_snapshot_id"),
                result.getString("snapshot_json"), result.getString("status"), result.getString("result_filename"),
                nullableLong(result, "result_size"), result.getString("failure_code"),
                result.getString("failure_message"), result.getLong("job_id"),
                result.getLong("requested_by_account_id"), result.getLong("requested_by_member_id"),
                result.getString("request_id"), result.getString("trace_id"), time(result, "started_at"),
                time(result, "finished_at"), time(result, "created_at"), time(result, "updated_at"),
                result.getLong("version"));
    }

    private static String prefixedVersionColumns(String alias) {
        return VERSION_COLUMNS.replaceAll("(^|,)([a-z_]+)", "$1" + alias + ".$2");
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
    private static BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }
    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public record ModuleRef(long id, String code, String name) { }
    public record FieldRef(String code, String name, String type, boolean hidden) { }
    public record ActiveSchema(long schemaVersionId, long moduleSnapshotId, String moduleCode, String moduleName) { }
    public record TemplateRecord(
            long id, long systemId, long moduleId, String moduleCode, String code, String name, String status,
            String paperSize, String orientation, String definitionJson, Long publishedVersionId,
            Long publishedVersionNo, Long publishedSchemaVersionId, LocalDateTime createdAt, long createdBy,
            LocalDateTime updatedAt, long updatedBy, long version
    ) { }
    public record TemplateVersionRecord(
            long id, long systemId, long templateId, long moduleId, String moduleCode, String code, String name,
            long versionNo, long schemaVersionId, long moduleSnapshotId, String paperSize, String orientation,
            String definitionJson, String definitionChecksum, LocalDateTime publishedAt, long publishedBy
    ) { }
    public record TaskRecord(
            long id, long systemId, long tenantId, long moduleId, String moduleCode, long recordId,
            long recordVersion, String recordNo, long templateId, long templateVersionId, long templateVersionNo,
            String templateCode, String templateName, long schemaVersionId, long moduleSnapshotId,
            String snapshotJson, String status, String resultFilename, Long resultSize, String failureCode,
            String failureMessage, long jobId, long accountId, long memberId, String requestId, String traceId,
            LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            long version
    ) { }
}
