package com.unique.examine.plat.directory;

import com.unique.examine.core.api.KpiReminderRecipientFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Tenant-hiding JDBC projection for current KPI department recipients. */
@Service
public class KpiReminderRecipientBridge
        implements KpiReminderRecipientFacade {
    static final String ACTIVE_DEPARTMENT_MEMBERS_SQL = """
            SELECT DISTINCT m.id
              FROM un_plat_department d
              JOIN un_plat_member_department md
                ON md.scope_type=d.scope_type
               AND md.scope_key=d.scope_key
               AND md.tenant_key=d.tenant_key
               AND md.department_id=d.id
              JOIN un_plat_member m
                ON m.system_id=md.system_id
               AND m.id=md.member_id
              JOIN un_plat_member_tenant mt
                ON mt.system_id=m.system_id
               AND mt.member_id=m.id
               AND mt.tenant_id=?
             WHERE d.scope_type='SYSTEM'
               AND d.scope_key=?
               AND d.system_id=?
               AND d.id=?
               AND (d.tenant_id IS NULL OR d.tenant_id=?)
               AND d.status='ACTIVE'
               AND d.deleted_at IS NULL
               AND md.system_id=?
               AND (md.tenant_id IS NULL OR md.tenant_id=?)
               AND md.deleted_at IS NULL
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL
                    OR mt.expires_at>CURRENT_TIMESTAMP(3))
             ORDER BY m.id
             LIMIT 1000
             FOR SHARE
            """;

    private final JdbcTemplate jdbc;

    public KpiReminderRecipientBridge(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Long> activeDepartmentMemberIds(
            long systemId,
            long tenantId,
            long departmentId
    ) {
        if (systemId <= 0 || tenantId <= 0 || departmentId <= 0) {
            return List.of();
        }
        return bounded(jdbc.query(
                ACTIVE_DEPARTMENT_MEMBERS_SQL,
                (result, row) -> result.getLong(1),
                tenantId,
                systemId,
                systemId,
                departmentId,
                tenantId,
                systemId,
                tenantId));
    }

    private static List<Long> bounded(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberIds.stream()
                .filter(memberId -> memberId != null && memberId > 0)
                .distinct()
                .sorted()
                .limit(MAX_RECIPIENTS)
                .toList();
    }
}
