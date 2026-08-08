package com.unique.examine.module.runtime.favorite;

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
public class FavoriteRepository {
    private static final int ACTIVE_LIMIT = 500;

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public FavoriteRepository(JdbcTemplate jdbc, IdService ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public List<StoredFavorite> listActive(RuntimeSession session) {
        return jdbc.query("SELECT f.*,m.module_code FROM un_module_favorite f "
                        + "JOIN un_module_config_root root ON root.system_id=f.system_id "
                        + "JOIN un_module_runtime_schema_module m ON m.system_id=root.system_id "
                        + "AND m.schema_version_id=root.active_version_id "
                        + "AND m.logical_module_id=f.logical_module_id "
                        + "WHERE f.system_id=? AND f.tenant_id=? AND f.member_id=? "
                        + "AND f.deleted_at IS NULL ORDER BY f.updated_at DESC,f.id DESC LIMIT 500",
                (result, row) -> row(result, result.getString("module_code")),
                session.systemId(), tenant(session), session.memberId());
    }

    public CreateResult createOrFind(
            RuntimeSession session,
            FavoriteTargetResolver.ResolvedTarget target
    ) {
        lockOwner(session);
        var existing = findActive(session, target);
        if (!existing.isEmpty()) {
            return new CreateResult(existing.getFirst(), false);
        }
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_favorite "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? AND deleted_at IS NULL",
                Long.class, session.systemId(), tenant(session), session.memberId());
        if (count != null && count >= ACTIVE_LIMIT) {
            throw new BusinessException("FAVORITE_LIMIT_REACHED",
                    "A member may own at most 500 active favorites in a tenant", HttpStatus.CONFLICT);
        }
        var id = ids.nextId();
        var now = LocalDateTime.now();
        jdbc.update("INSERT INTO un_module_favorite "
                        + "(id,system_id,tenant_id,member_id,target_type,logical_module_id,"
                        + "module_schema_version_id,module_snapshot_id,record_id,record_schema_version_id,"
                        + "record_module_snapshot_id,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                id, session.systemId(), tenant(session), session.memberId(), target.type(),
                target.logicalModuleId(), target.moduleSchemaVersionId(), target.moduleSnapshotId(),
                target.recordId(), target.recordSchemaVersionId(), target.recordModuleSnapshotId(),
                now, session.accountId(), now, session.accountId());
        return new CreateResult(requireOwned(session, id, target.moduleCode()), true);
    }

    public StoredFavorite requireOwned(RuntimeSession session, long favoriteId) {
        return requireOwned(session, favoriteId, null);
    }

    public long delete(RuntimeSession session, long favoriteId, long expectedVersion) {
        var now = LocalDateTime.now();
        var updated = jdbc.update("UPDATE un_module_favorite SET deleted_at=?,deleted_by=?,updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE id=? AND system_id=? AND tenant_id=? "
                        + "AND member_id=? AND deleted_at IS NULL AND version=?",
                now, session.accountId(), now, session.accountId(), favoriteId,
                session.systemId(), tenant(session), session.memberId(), expectedVersion);
        if (updated != 1) {
            requireOwned(session, favoriteId);
            throw new BusinessException("FAVORITE_VERSION_CONFLICT",
                    "Favorite version has changed", HttpStatus.CONFLICT);
        }
        return expectedVersion + 1;
    }

    private void lockOwner(RuntimeSession session) {
        jdbc.queryForObject("SELECT id FROM un_plat_member WHERE system_id=? AND id=? FOR UPDATE",
                Long.class, session.systemId(), session.memberId());
    }

    private List<StoredFavorite> findActive(
            RuntimeSession session,
            FavoriteTargetResolver.ResolvedTarget target
    ) {
        return jdbc.query("SELECT f.* FROM un_module_favorite f WHERE f.system_id=? "
                        + "AND f.tenant_id=? AND f.member_id=? AND f.target_type=? "
                        + "AND f.logical_module_id=? AND f.target_record_key=? AND f.deleted_at IS NULL",
                (result, row) -> row(result, target.moduleCode()),
                session.systemId(), tenant(session), session.memberId(), target.type(),
                target.logicalModuleId(), target.recordId() == null ? 0L : target.recordId());
    }

    private StoredFavorite requireOwned(RuntimeSession session, long favoriteId, String moduleCode) {
        var rows = jdbc.query("SELECT f.* FROM un_module_favorite f WHERE f.id=? AND f.system_id=? "
                        + "AND f.tenant_id=? AND f.member_id=? AND f.deleted_at IS NULL",
                (result, row) -> row(result, moduleCode), favoriteId,
                session.systemId(), tenant(session), session.memberId());
        if (rows.isEmpty()) {
            throw new BusinessException("FAVORITE_NOT_FOUND",
                    "Favorite does not exist", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private static StoredFavorite row(ResultSet result, String moduleCode) throws SQLException {
        return new StoredFavorite(
                result.getLong("id"),
                result.getString("target_type"),
                result.getLong("logical_module_id"),
                moduleCode,
                nullableLong(result, "record_id"),
                result.getLong("version"),
                result.getObject("updated_at", LocalDateTime.class));
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        var value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static long tenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED",
                    "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    public record StoredFavorite(
            long id,
            String type,
            long logicalModuleId,
            String moduleCode,
            Long recordId,
            long version,
            LocalDateTime updatedAt
    ) {
    }

    public record CreateResult(StoredFavorite favorite, boolean created) {
    }
}
