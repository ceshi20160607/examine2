package com.unique.examine.module.dashboard.adapter.jdbc;

import com.unique.examine.module.dashboard.port.DashboardKpiCatalog;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcDashboardKpiCatalog implements DashboardKpiCatalog {
    private final JdbcTemplate jdbc;

    public JdbcDashboardKpiCatalog(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<ActiveKpiVersion> activeVersion(
            long systemId,
            long tenantId,
            long kpiId
    ) {
        List<ActiveKpiVersion> rows = jdbc.query("""
                        SELECT version_row.kpi_id,version_row.system_id,
                               version_row.tenant_id,version_row.id,
                               version_row.version_no,version_row.kpi_code,
                               version_row.kpi_name,version_row.subject_type,
                               version_row.period_type
                          FROM un_module_kpi root
                          JOIN un_module_kpi_version version_row
                            ON version_row.system_id=root.system_id
                           AND version_row.tenant_id=root.tenant_id
                           AND version_row.kpi_id=root.id
                           AND version_row.id=root.active_version_id
                           AND version_row.version_no=root.active_version_no
                         WHERE root.system_id=? AND root.tenant_id=?
                           AND root.id=?
                        """, (result, rowNumber) -> new ActiveKpiVersion(
                        result.getLong("kpi_id"),
                        result.getLong("system_id"),
                        result.getLong("tenant_id"),
                        result.getLong("id"), result.getInt("version_no"),
                        result.getString("kpi_code"),
                        result.getString("kpi_name"),
                        KpiSubjectType.valueOf(result.getString("subject_type")),
                        KpiPeriodType.valueOf(result.getString("period_type"))),
                systemId, tenantId, kpiId);
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped dashboard KPI lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }
}
