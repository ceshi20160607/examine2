package com.unique.examine.collab.recordteam;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class JdbcRecordTeamRepository implements RecordTeamRepository {
    static final String INSERT_TEAM_SQL = """
            INSERT INTO un_collab_record_team (
                system_id, tenant_id, record_id, version,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), ?)
            """;
    static final String INSERT_MEMBER_SQL = """
            INSERT INTO un_collab_record_team_member (
                system_id, tenant_id, record_id, member_id, team_role, row_version,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), ?)
            """;
    static final String FIND_TEAM_SQL = """
            SELECT version
              FROM un_collab_record_team
             WHERE system_id = ? AND tenant_id = ? AND record_id = ?
            """;
    static final String LOCK_TEAM_SQL = FIND_TEAM_SQL + " FOR UPDATE";
    static final String FIND_MEMBERS_SQL = """
            SELECT member_id, team_role
              FROM un_collab_record_team_member
             WHERE system_id = ? AND tenant_id = ? AND record_id = ?
             ORDER BY member_id
            """;
    static final String DELETE_MEMBER_SQL = """
            DELETE FROM un_collab_record_team_member
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND member_id = ?
            """;
    static final String UPDATE_MEMBER_ROLE_SQL = """
            UPDATE un_collab_record_team_member
               SET team_role = ?, row_version = row_version + 1,
                   updated_at = CURRENT_TIMESTAMP(3), updated_by = ?
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND member_id = ?
            """;
    static final String UPDATE_TEAM_SQL = """
            UPDATE un_collab_record_team
               SET version = ?, updated_at = CURRENT_TIMESTAMP(3), updated_by = ?
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND version = ?
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcRecordTeamRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public void create(RecordTeam team) {
        Objects.requireNonNull(team, "team");
        try {
            transaction.executeWithoutResult(status -> {
                var actor = team.owner().memberId();
                var key = team.key();
                jdbc.update(
                        INSERT_TEAM_SQL,
                        key.systemId(),
                        key.tenantId(),
                        key.recordId(),
                        team.version(),
                        actor,
                        actor);
                for (var member : team.members()) {
                    insertMember(key, member, actor);
                }
            });
        } catch (DuplicateKeyException exception) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_DUPLICATE",
                    "A record team already exists for record " + team.key().recordId());
        }
    }

    @Override
    public RecordTeamInitialization initialize(RecordTeam team) {
        Objects.requireNonNull(team, "team");
        try {
            create(team);
            return RecordTeamInitialization.created(team);
        } catch (RecordTeamException exception) {
            if (!"RECORD_TEAM_DUPLICATE".equals(exception.code())) {
                throw exception;
            }
            var existing = find(team.key()).orElseThrow(() -> RecordTeamException.conflict(
                    "RECORD_TEAM_INITIALIZATION_CONFLICT",
                    "The record team was concurrently initialized but is not visible"));
            return RecordTeamInitialization.existing(existing);
        }
    }

    @Override
    public Optional<RecordTeam> find(RecordTeamKey key) {
        Objects.requireNonNull(key, "key");
        var versions = jdbc.query(
                FIND_TEAM_SQL,
                (resultSet, rowNumber) -> resultSet.getLong("version"),
                key.systemId(),
                key.tenantId(),
                key.recordId());
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(restore(key, versions.getFirst()));
    }

    @Override
    public List<RecordTeam> lockAll(List<RecordTeamKey> keys) {
        Objects.requireNonNull(keys, "keys");
        var locked = new ArrayList<RecordTeam>(keys.size());
        for (var key : keys) {
            Objects.requireNonNull(key, "key");
            var versions = jdbc.query(
                    LOCK_TEAM_SQL,
                    (resultSet, rowNumber) -> resultSet.getLong("version"),
                    key.systemId(),
                    key.tenantId(),
                    key.recordId());
            if (!versions.isEmpty()) {
                locked.add(restore(key, versions.getFirst()));
            }
        }
        return List.copyOf(locked);
    }

    @Override
    public RecordTeam update(
            RecordTeamKey key,
            String actorMemberId,
            UnaryOperator<RecordTeam> mutation
    ) {
        Objects.requireNonNull(key, "key");
        var actor = requireActor(actorMemberId);
        Objects.requireNonNull(mutation, "mutation");

        return transaction.execute(status -> {
            var versions = jdbc.query(
                    LOCK_TEAM_SQL,
                    (resultSet, rowNumber) -> resultSet.getLong("version"),
                    key.systemId(),
                    key.tenantId(),
                    key.recordId());
            if (versions.isEmpty()) {
                throw RecordTeamException.notFound(
                        "RECORD_TEAM_NOT_FOUND",
                        "No record team exists for record " + key.recordId());
            }

            var current = restore(key, versions.getFirst());
            var changed = Objects.requireNonNull(mutation.apply(current), "mutation result");
            if (!changed.key().equals(key)) {
                throw RecordTeamException.conflict(
                        "RECORD_TEAM_PERSISTENCE_INVALID",
                        "A repository mutation cannot change the record-team key");
            }
            if (changed.version() == current.version()) {
                return current;
            }
            if (changed.version() != current.version() + 1) {
                throw RecordTeamException.conflict(
                        "RECORD_TEAM_PERSISTENCE_INVALID",
                        "A repository mutation must advance the version exactly once");
            }

            persistMemberDiff(current, changed, actor);
            var updated = jdbc.update(
                    UPDATE_TEAM_SQL,
                    changed.version(),
                    actor,
                    key.systemId(),
                    key.tenantId(),
                    key.recordId(),
                    current.version());
            if (updated != 1) {
                throw RecordTeamException.conflict(
                        "RECORD_TEAM_VERSION_CONFLICT",
                        "The record team changed concurrently");
            }
            return changed;
        });
    }

    private RecordTeam restore(RecordTeamKey key, long version) {
        var members = jdbc.query(
                FIND_MEMBERS_SQL,
                JdbcRecordTeamRepository::mapMember,
                key.systemId(),
                key.tenantId(),
                key.recordId());
        return RecordTeam.restore(key, members, version);
    }

    private void persistMemberDiff(RecordTeam before, RecordTeam after, String actor) {
        var beforeById = byId(before.members());
        var afterById = byId(after.members());
        var key = before.key();

        beforeById.keySet().stream()
                .filter(memberId -> !afterById.containsKey(memberId))
                .forEach(memberId -> jdbc.update(
                        DELETE_MEMBER_SQL,
                        key.systemId(),
                        key.tenantId(),
                        key.recordId(),
                        memberId));

        beforeById.values().stream()
                .filter(member -> {
                    var changed = afterById.get(member.memberId());
                    return changed != null
                            && changed.role() != member.role()
                            && changed.role() != RecordTeamRole.OWNER;
                })
                .forEach(member -> updateRole(key, afterById.get(member.memberId()), actor));

        beforeById.values().stream()
                .filter(member -> {
                    var changed = afterById.get(member.memberId());
                    return changed != null
                            && changed.role() != member.role()
                            && changed.role() == RecordTeamRole.OWNER;
                })
                .forEach(member -> updateRole(key, afterById.get(member.memberId()), actor));

        afterById.values().stream()
                .filter(member -> !beforeById.containsKey(member.memberId()))
                .forEach(member -> insertMember(key, member, actor));
    }

    private void updateRole(RecordTeamKey key, RecordTeamMember member, String actor) {
        var updated = jdbc.update(
                UPDATE_MEMBER_ROLE_SQL,
                member.role().name(),
                actor,
                key.systemId(),
                key.tenantId(),
                key.recordId(),
                member.memberId());
        if (updated != 1) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_VERSION_CONFLICT",
                    "A record-team member changed concurrently: " + member.memberId());
        }
    }

    private void insertMember(RecordTeamKey key, RecordTeamMember member, String actor) {
        jdbc.update(
                INSERT_MEMBER_SQL,
                key.systemId(),
                key.tenantId(),
                key.recordId(),
                member.memberId(),
                member.role().name(),
                actor,
                actor);
    }

    private static RecordTeamMember mapMember(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RecordTeamMember(
                resultSet.getString("member_id"),
                RecordTeamRole.valueOf(resultSet.getString("team_role")));
    }

    private static Map<String, RecordTeamMember> byId(List<RecordTeamMember> members) {
        var result = new LinkedHashMap<String, RecordTeamMember>();
        for (var member : members) {
            result.put(member.memberId(), member);
        }
        return result;
    }

    private static String requireActor(String actorMemberId) {
        if (actorMemberId == null || actorMemberId.isBlank()) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_ACTOR_REQUIRED",
                    "actor memberId is required");
        }
        return actorMemberId.trim();
    }
}
