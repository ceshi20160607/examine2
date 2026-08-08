package com.unique.examine.module.runtime.recent;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Repository
public class RecentRepository {
    private static final int RETENTION_LIMIT = 200;

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public RecentRepository(JdbcTemplate jdbc, IdService ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public List<StoredRecent> list(RuntimeSession session) {
        return jdbc.query("SELECT recent.*,module.module_code FROM un_module_recent recent "
                        + "JOIN un_module_config_root root ON root.system_id=recent.system_id "
                        + "JOIN un_module_runtime_schema_module module ON module.system_id=root.system_id "
                        + "AND module.schema_version_id=root.active_version_id "
                        + "AND module.logical_module_id=recent.logical_module_id "
                        + "WHERE recent.system_id=? AND recent.tenant_id=? AND recent.member_id=? "
                        + "ORDER BY recent.last_accessed_at DESC,recent.id DESC LIMIT 200",
                (result, row) -> row(result, result.getString("module_code")),
                session.systemId(), tenant(session), session.memberId());
    }

    public StoredRecent touch(RuntimeSession session, RecentTargetResolver.ResolvedTarget target) {
        lockOwner(session);
        var accessedAt = nextAccessTime(session);
        var existing = find(session, target.logicalModuleId(), target.recordId(), target.moduleCode());
        final long recentId;
        if (existing.isEmpty()) {
            recentId = ids.nextId();
            jdbc.update("INSERT INTO un_module_recent "
                            + "(id,system_id,tenant_id,member_id,logical_module_id,module_schema_version_id,"
                            + "module_snapshot_id,record_id,record_schema_version_id,record_module_snapshot_id,"
                            + "access_count,last_accessed_at,created_at,created_by,updated_at,updated_by,version) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?,0)",
                    recentId, session.systemId(), tenant(session), session.memberId(),
                    target.logicalModuleId(), target.moduleSchemaVersionId(), target.moduleSnapshotId(),
                    target.recordId(), target.recordSchemaVersionId(), target.recordModuleSnapshotId(),
                    accessedAt, accessedAt, session.accountId(), accessedAt, session.accountId());
        } else {
            recentId = existing.getFirst().id();
            jdbc.update("UPDATE un_module_recent SET module_schema_version_id=?,module_snapshot_id=?,"
                            + "record_schema_version_id=?,record_module_snapshot_id=?,access_count=access_count+1,"
                            + "last_accessed_at=?,updated_at=?,updated_by=?,version=version+1 "
                            + "WHERE id=? AND system_id=? AND tenant_id=? AND member_id=?",
                    target.moduleSchemaVersionId(), target.moduleSnapshotId(),
                    target.recordSchemaVersionId(), target.recordModuleSnapshotId(),
                    accessedAt, accessedAt, session.accountId(), recentId,
                    session.systemId(), tenant(session), session.memberId());
        }
        prune(session);
        return require(session, recentId, target.moduleCode());
    }

    private void lockOwner(RuntimeSession session) {
        jdbc.queryForObject("SELECT id FROM un_plat_member WHERE system_id=? AND id=? FOR UPDATE",
                Long.class, session.systemId(), session.memberId());
    }

    private LocalDateTime nextAccessTime(RuntimeSession session) {
        var previous = jdbc.queryForObject("SELECT MAX(last_accessed_at) FROM un_module_recent "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=?",
                LocalDateTime.class, session.systemId(), tenant(session), session.memberId());
        var now = LocalDateTime.now();
        return previous != null && !now.isAfter(previous) ? previous.plusNanos(1_000_000) : now;
    }

    private List<StoredRecent> find(
            RuntimeSession session,
            long logicalModuleId,
            long recordId,
            String moduleCode
    ) {
        return jdbc.query("SELECT recent.* FROM un_module_recent recent "
                        + "WHERE recent.system_id=? AND recent.tenant_id=? AND recent.member_id=? "
                        + "AND recent.logical_module_id=? AND recent.record_id=?",
                (result, row) -> row(result, moduleCode),
                session.systemId(), tenant(session), session.memberId(), logicalModuleId, recordId);
    }

    private StoredRecent require(RuntimeSession session, long recentId, String moduleCode) {
        var rows = jdbc.query("SELECT recent.* FROM un_module_recent recent "
                        + "WHERE recent.id=? AND recent.system_id=? AND recent.tenant_id=? AND recent.member_id=?",
                (result, row) -> row(result, moduleCode),
                recentId, session.systemId(), tenant(session), session.memberId());
        if (rows.isEmpty()) {
            throw new IllegalStateException("Touched recent record was not persisted");
        }
        return rows.getFirst();
    }

    private void prune(RuntimeSession session) {
        var staleIds = jdbc.queryForList("SELECT id FROM un_module_recent "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? "
                        + "ORDER BY last_accessed_at DESC,id DESC LIMIT 1000000 OFFSET ?",
                Long.class, session.systemId(), tenant(session), session.memberId(), RETENTION_LIMIT);
        if (staleIds.isEmpty()) {
            return;
        }
        jdbc.update("DELETE FROM un_module_recent WHERE system_id=? AND tenant_id=? AND member_id=? "
                        + "AND id IN (" + placeholders(staleIds.size()) + ")",
                arguments(session, staleIds));
    }

    private static Object[] arguments(RuntimeSession session, List<Long> ids) {
        var arguments = new Object[3 + ids.size()];
        arguments[0] = session.systemId();
        arguments[1] = tenant(session);
        arguments[2] = session.memberId();
        for (var index = 0; index < ids.size(); index++) {
            arguments[3 + index] = ids.get(index);
        }
        return arguments;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static StoredRecent row(ResultSet result, String moduleCode) throws SQLException {
        return new StoredRecent(
                result.getLong("id"),
                result.getLong("logical_module_id"),
                moduleCode,
                result.getLong("record_id"),
                result.getLong("access_count"),
                result.getObject("last_accessed_at", LocalDateTime.class));
    }

    private static long tenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED",
                    "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    public record StoredRecent(
            long id,
            long logicalModuleId,
            String moduleCode,
            long recordId,
            long accessCount,
            LocalDateTime lastAccessedAt
    ) {
    }
}
