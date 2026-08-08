package com.unique.examine.plat.directory;

import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.api.ReportExportPrincipalFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class ReportExportPrincipalBridge
        implements ReportExportPrincipalFacade {
    static final String ACTIVE_PRINCIPAL_SQL = """
            SELECT m.account_id
              FROM un_plat_member m
              JOIN un_plat_member_tenant mt
                ON mt.system_id=m.system_id
               AND mt.member_id=m.id
               AND mt.tenant_id=?
             WHERE m.system_id=?
               AND m.id=?
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL
                    OR mt.expires_at>CURRENT_TIMESTAMP(3))
             FOR SHARE
            """;

    private final JdbcTemplate jdbc;
    private final EffectivePermissionFacade permissions;

    public ReportExportPrincipalBridge(
            JdbcTemplate jdbc,
            EffectivePermissionFacade permissions
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Principal> current(
            long systemId,
            long tenantId,
            long memberId
    ) {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            return Optional.empty();
        }
        var accounts = jdbc.query(
                ACTIVE_PRINCIPAL_SQL,
                (result, row) -> result.getLong(1),
                tenantId, systemId, memberId);
        if (accounts.size() != 1 || accounts.getFirst() <= 0) {
            return Optional.empty();
        }
        var current = permissions.evaluateSystem(
                systemId, tenantId, memberId);
        return Optional.of(new Principal(
                accounts.getFirst(), memberId, current.epoch(),
                current.permissions()));
    }
}
