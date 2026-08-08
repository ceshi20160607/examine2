package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.work.port.WorkMemberDirectory;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcWorkMemberDirectory implements WorkMemberDirectory {
    static final String ACTIVE_MEMBER_SQL = """
            SELECT COUNT(*)
              FROM un_plat_member member
              JOIN un_plat_member_tenant membership
                ON membership.system_id = member.system_id
               AND membership.member_id = member.id
             WHERE member.system_id = ?
               AND member.id = ?
               AND member.status = 'ACTIVE'
               AND member.deleted_at IS NULL
               AND membership.tenant_id = ?
               AND membership.status = 'ACTIVE'
               AND membership.deleted_at IS NULL
               AND (membership.expires_at IS NULL OR membership.expires_at > CURRENT_TIMESTAMP(3))
            """;

    private final JdbcTemplate jdbc;

    public JdbcWorkMemberDirectory(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("JdbcTemplate is required");
        }
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActiveMember(long systemId, long tenantId, long memberId) {
        var count = jdbc.queryForObject(ACTIVE_MEMBER_SQL, Integer.class, systemId, memberId, tenantId);
        return count != null && count > 0;
    }
}
