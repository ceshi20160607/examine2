package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SavedViewRepository {
    private final JdbcTemplate jdbc;
    private final IdService ids;

    public SavedViewRepository(JdbcTemplate jdbc, IdService ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public List<StoredView> list(RuntimeSession session, long moduleId) {
        return jdbc.query("SELECT v.*,m.module_code FROM un_module_saved_view v JOIN un_module_definition m "
                        + "ON m.system_id=v.system_id AND m.id=v.logical_module_id "
                        + "WHERE v.system_id=? AND v.tenant_id=? AND v.member_id=? AND v.logical_module_id=? "
                        + "AND v.deleted_at IS NULL ORDER BY v.updated_at DESC,v.id DESC LIMIT 100",
                (result, row) -> row(result), session.systemId(), tenant(session), session.memberId(), moduleId);
    }

    public StoredView require(RuntimeSession session, long moduleId, long viewId) {
        var rows = jdbc.query("SELECT v.*,m.module_code FROM un_module_saved_view v JOIN un_module_definition m "
                        + "ON m.system_id=v.system_id AND m.id=v.logical_module_id "
                        + "WHERE v.id=? AND v.system_id=? AND v.tenant_id=? AND v.member_id=? "
                        + "AND v.logical_module_id=? AND v.deleted_at IS NULL",
                (result, row) -> row(result), viewId, session.systemId(), tenant(session), session.memberId(), moduleId);
        if (rows.isEmpty()) {
            throw new BusinessException("SAVED_VIEW_NOT_FOUND", "Saved view does not exist", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    public StoredView requireOwned(RuntimeSession session, long viewId) {
        var rows = jdbc.query("SELECT v.*,m.module_code FROM un_module_saved_view v JOIN un_module_definition m "
                        + "ON m.system_id=v.system_id AND m.id=v.logical_module_id "
                        + "WHERE v.id=? AND v.system_id=? AND v.tenant_id=? AND v.member_id=? "
                        + "AND v.deleted_at IS NULL",
                (result, row) -> row(result), viewId, session.systemId(), tenant(session), session.memberId());
        if (rows.isEmpty()) {
            throw new BusinessException("SAVED_VIEW_NOT_FOUND", "Saved view does not exist", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    public StoredView create(
            RuntimeSession session,
            long moduleId,
            long schemaVersionId,
            String name,
            String queryJson,
            String columnsJson
    ) {
        jdbc.queryForObject("SELECT id FROM un_plat_member WHERE system_id=? AND id=? FOR UPDATE",
                Long.class, session.systemId(), session.memberId());
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_saved_view WHERE system_id=? "
                        + "AND tenant_id=? AND member_id=? AND logical_module_id=? AND deleted_at IS NULL",
                Long.class, session.systemId(), tenant(session), session.memberId(), moduleId);
        if (count != null && count >= 100) {
            throw new BusinessException("SAVED_VIEW_LIMIT_REACHED",
                    "A member may save at most 100 views per module", HttpStatus.CONFLICT);
        }
        var id = ids.nextId();
        var now = LocalDateTime.now();
        jdbc.update("INSERT INTO un_module_saved_view "
                        + "(id,system_id,tenant_id,member_id,logical_module_id,schema_version_id,module_snapshot_id,"
                        + "name,query_json,columns_json,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?, ?,?,?,0)",
                id, session.systemId(), tenant(session), session.memberId(), moduleId, schemaVersionId, moduleId,
                name, queryJson, columnsJson, now, session.accountId(), now, session.accountId());
        return require(session, moduleId, id);
    }

    public StoredView update(
            RuntimeSession session,
            long moduleId,
            long viewId,
            long schemaVersionId,
            long expectedVersion,
            String name,
            String queryJson,
            String columnsJson
    ) {
        var updated = jdbc.update("UPDATE un_module_saved_view SET schema_version_id=?,module_snapshot_id=?,"
                        + "name=?,query_json=?,columns_json=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND tenant_id=? AND member_id=? AND logical_module_id=? "
                        + "AND deleted_at IS NULL AND version=?",
                schemaVersionId, moduleId, name, queryJson, columnsJson, LocalDateTime.now(), session.accountId(),
                viewId, session.systemId(), tenant(session), session.memberId(), moduleId, expectedVersion);
        if (updated != 1) {
            require(session, moduleId, viewId);
            throw versionConflict();
        }
        return require(session, moduleId, viewId);
    }

    public long delete(RuntimeSession session, long moduleId, long viewId, long expectedVersion) {
        var updated = jdbc.update("UPDATE un_module_saved_view SET deleted_at=?,deleted_by=?,updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE id=? AND system_id=? AND tenant_id=? AND member_id=? "
                        + "AND logical_module_id=? AND deleted_at IS NULL AND version=?",
                LocalDateTime.now(), session.accountId(), LocalDateTime.now(), session.accountId(), viewId,
                session.systemId(), tenant(session), session.memberId(), moduleId, expectedVersion);
        if (updated != 1) {
            require(session, moduleId, viewId);
            throw versionConflict();
        }
        return expectedVersion + 1;
    }

    private static StoredView row(ResultSet result) throws SQLException {
        return new StoredView(result.getLong("id"), result.getLong("logical_module_id"),
                result.getString("module_code"),
                result.getLong("schema_version_id"), result.getString("name"),
                result.getString("query_json"), result.getString("columns_json"), result.getLong("version"));
    }

    private static long tenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED", "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    private static BusinessException versionConflict() {
        return new BusinessException("SAVED_VIEW_VERSION_CONFLICT",
                "Saved view version has changed", HttpStatus.CONFLICT);
    }

    public record StoredView(
            long id,
            long moduleId,
            String moduleCode,
            long schemaVersionId,
            String name,
            String queryJson,
            String columnsJson,
            long version
    ) { }
}
