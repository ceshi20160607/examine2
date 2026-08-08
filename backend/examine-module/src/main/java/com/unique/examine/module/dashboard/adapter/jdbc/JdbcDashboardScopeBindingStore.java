package com.unique.examine.module.dashboard.adapter.jdbc;

import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardScopeBinding;
import com.unique.examine.module.dashboard.port.DashboardScopeBindingStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcDashboardScopeBindingStore
        implements DashboardScopeBindingStore {
    private static final String COLUMNS =
            "dashboard_id,system_id,tenant_id,scope_type,scope_key,owner_member_id";
    private final JdbcTemplate jdbc;

    public JdbcDashboardScopeBindingStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void bind(DashboardScopeBinding binding) {
        try {
            var updated = jdbc.update("""
                    INSERT INTO un_module_dashboard_scope_binding (
                        dashboard_id,system_id,tenant_id,scope_type,scope_key,
                        owner_member_id,created_at)
                    VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP(6))
                    """, binding.dashboardId(), binding.systemId(),
                    binding.tenantId(), binding.placement().name(),
                    binding.scopeKey(), binding.ownerMemberId());
            if (updated != 1) {
                throw new IllegalStateException(
                        "Dashboard scope insert did not affect one row");
            }
        } catch (DuplicateKeyException conflict) {
            throw new DashboardException(
                    "DASHBOARD_SCOPE_CONFLICT",
                    "A dashboard already exists for this scope");
        }
    }

    @Override
    public Optional<DashboardScopeBinding> find(
            long systemId,
            long tenantId,
            DashboardPlacement placement,
            String scopeKey,
            long ownerMemberId
    ) {
        return one(jdbc.query("SELECT " + COLUMNS
                        + " FROM un_module_dashboard_scope_binding"
                        + " WHERE system_id=? AND tenant_id=? AND scope_type=?"
                        + " AND scope_key=? AND owner_member_id=?",
                JdbcDashboardScopeBindingStore::row, systemId, tenantId,
                placement.name(), scopeKey, ownerMemberId));
    }

    @Override
    public Optional<DashboardScopeBinding> findByDashboard(
            long systemId,
            long tenantId,
            long dashboardId
    ) {
        return one(jdbc.query("SELECT " + COLUMNS
                        + " FROM un_module_dashboard_scope_binding"
                        + " WHERE system_id=? AND tenant_id=? AND dashboard_id=?",
                JdbcDashboardScopeBindingStore::row,
                systemId, tenantId, dashboardId));
    }

    @Override
    public List<DashboardScopeBinding> findPublic(
            long systemId,
            long tenantId
    ) {
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM un_module_dashboard_scope_binding"
                        + " WHERE system_id=? AND tenant_id=?"
                        + " AND scope_type IN ('APPLICATION_HOME','MODULE_HOME')"
                        + " AND owner_member_id=0"
                        + " ORDER BY scope_type,scope_key,dashboard_id",
                JdbcDashboardScopeBindingStore::row, systemId, tenantId);
    }

    @Override
    public List<DashboardScopeBinding> findPersonal(
            long systemId,
            long tenantId,
            long ownerMemberId
    ) {
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM un_module_dashboard_scope_binding"
                        + " WHERE system_id=? AND tenant_id=?"
                        + " AND scope_type='PERSONAL_HOME'"
                        + " AND owner_member_id=? ORDER BY scope_key,dashboard_id",
                JdbcDashboardScopeBindingStore::row,
                systemId, tenantId, ownerMemberId);
    }

    private static DashboardScopeBinding row(ResultSet row, int ignored)
            throws SQLException {
        return new DashboardScopeBinding(
                row.getLong("dashboard_id"), row.getLong("system_id"),
                row.getLong("tenant_id"), DashboardPlacement.valueOf(
                row.getString("scope_type")), row.getString("scope_key"),
                row.getLong("owner_member_id"));
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Dashboard scope is not unique");
        }
        return rows.stream().findFirst();
    }
}
