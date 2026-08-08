package com.unique.examine.plat.directory;

import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RuntimeActiveMemberBridge implements RuntimeActiveMemberFacade {
    static final String ACTIVE_MEMBER_LOCK_SQL = """
            SELECT m.id
              FROM un_plat_member_tenant mt
              JOIN un_plat_member m
                ON m.system_id=mt.system_id
               AND m.id=mt.member_id
             WHERE mt.system_id=?
               AND mt.tenant_id=?
               AND mt.member_id=?
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3))
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
             FOR UPDATE
            """;
    static final String PRIMARY_DEPARTMENT_LOCK_SQL = """
            SELECT md.department_id
              FROM un_plat_member_department md
             WHERE md.scope_type='SYSTEM'
               AND md.scope_key=?
               AND md.system_id=?
               AND md.member_id=?
               AND md.is_primary=1
               AND md.deleted_at IS NULL
               AND (md.tenant_id IS NULL OR md.tenant_id=?)
             ORDER BY CASE WHEN md.tenant_id=? THEN 0 ELSE 1 END,md.department_id
             LIMIT 1
             FOR UPDATE
            """;

    private final JdbcTemplate jdbc;

    public RuntimeActiveMemberBridge(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ActiveMember> lockActiveMember(long systemId, long tenantId, long memberId) {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            return Optional.empty();
        }
        var members = jdbc.query(
                ACTIVE_MEMBER_LOCK_SQL,
                (result, row) -> result.getLong("id"),
                systemId,
                tenantId,
                memberId);
        if (members.isEmpty()) {
            return Optional.empty();
        }
        var departments = jdbc.query(
                PRIMARY_DEPARTMENT_LOCK_SQL,
                (result, row) -> result.getLong("department_id"),
                systemId,
                systemId,
                memberId,
                tenantId,
                tenantId);
        return Optional.of(new ActiveMember(
                members.getFirst(),
                departments.isEmpty() ? null : departments.getFirst()));
    }
}
