package com.unique.examine.module.manage.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.module.base.entity.ConfigCheck;
import com.unique.examine.module.base.mapper.ModuleConfigCheckMapper;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Component
public class DraftRevisionCoordinator {
    private final JdbcTemplate jdbc;
    private final IdService idService;
    private final ModuleConfigCheckMapper checkMapper;

    public DraftRevisionCoordinator(JdbcTemplate jdbc, IdService idService, ModuleConfigCheckMapper checkMapper) {
        this.jdbc = jdbc;
        this.idService = idService;
        this.checkMapper = checkMapper;
    }

    public ConfigViews.RootSummary summary(long systemId) {
        var roots = jdbc.query(
                "SELECT id, system_id, status, draft_revision, active_version_id, base_version_id, "
                        + "last_check_id, version FROM un_module_config_root WHERE system_id = ?",
                DraftRevisionCoordinator::mapRoot,
                systemId
        );
        if (roots.isEmpty()) {
            return new ConfigViews.RootSummary(
                    Long.toString(systemId), "CLEAN", "0", null, null, "0", null
            );
        }
        var root = roots.getFirst();
        ConfigViews.CheckSummary check = null;
        if (root.lastCheckId() != null) {
            var entity = checkMapper.selectById(root.lastCheckId());
            if (entity != null && entity.getSystemId() == systemId) {
                check = checkView(entity);
            }
        }
        return rootView(root, check);
    }

    public Revision begin(ConfigSession session, String expectedRevision) {
        var expected = ConfigErrors.version(expectedRevision);
        var now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO un_module_config_root "
                        + "(id, system_id, status, draft_revision, created_at, created_by, updated_at, updated_by, version) "
                        + "VALUES (?, ?, 'CLEAN', 0, ?, ?, ?, ?, 0) "
                        + "ON DUPLICATE KEY UPDATE system_id = VALUES(system_id)",
                idService.nextId(), session.systemId(), now, session.accountId(), now, session.accountId()
        );
        var root = jdbc.queryForObject(
                "SELECT id, system_id, status, draft_revision, active_version_id, base_version_id, "
                        + "last_check_id, version FROM un_module_config_root WHERE system_id = ? FOR UPDATE",
                DraftRevisionCoordinator::mapRoot,
                session.systemId()
        );
        if (root == null || root.draftRevision() != expected) {
            throw ConfigErrors.versionConflict();
        }
        return new Revision(root, expected + 1);
    }

    public void finish(ConfigSession session, Revision revision) {
        var changed = jdbc.update(
                "UPDATE un_module_config_root SET status = 'DIRTY', draft_revision = ?, draft_checksum = NULL, "
                        + "last_check_id = NULL, updated_at = ?, updated_by = ?, version = version + 1 "
                        + "WHERE id = ? AND draft_revision = ?",
                revision.next(), LocalDateTime.now(), session.accountId(), revision.root().id(),
                revision.root().draftRevision()
        );
        if (changed != 1) {
            throw ConfigErrors.versionConflict();
        }
        jdbc.update(
                "UPDATE un_module_config_check SET status = 'STALE', version = version + 1 "
                        + "WHERE system_id = ? AND status <> 'STALE' AND draft_revision < ?",
                session.systemId(), revision.next()
        );
    }

    private static DraftRoot mapRoot(ResultSet rs, int rowNum) throws SQLException {
        return new DraftRoot(
                rs.getLong("id"), rs.getLong("system_id"), rs.getString("status"),
                rs.getLong("draft_revision"), nullableLong(rs, "active_version_id"),
                nullableLong(rs, "base_version_id"), nullableLong(rs, "last_check_id"),
                rs.getLong("version")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static ConfigViews.RootSummary rootView(DraftRoot root, ConfigViews.CheckSummary check) {
        return new ConfigViews.RootSummary(
                Long.toString(root.systemId()), root.status(), Long.toString(root.draftRevision()),
                string(root.activeVersionId()), string(root.baseVersionId()), Long.toString(root.version()), check
        );
    }

    private static ConfigViews.CheckSummary checkView(ConfigCheck check) {
        return new ConfigViews.CheckSummary(
                Long.toString(check.getId()), check.getStatus(), check.getBlockerCount(), check.getWarningCount(),
                Long.toString(check.getDraftRevision()), Long.toString(check.getVersion())
        );
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    public record DraftRoot(long id, long systemId, String status, long draftRevision, Long activeVersionId,
                            Long baseVersionId, Long lastCheckId, long version) { }

    public record Revision(DraftRoot root, long next) { }
}
