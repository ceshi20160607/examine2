package com.unique.examine.module.dashboard.port;

import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;

import java.util.Optional;

/** Tenant-scoped active KPI version lookup used only during dashboard check. */
public interface DashboardKpiCatalog {
    Optional<ActiveKpiVersion> activeVersion(
            long systemId,
            long tenantId,
            long kpiId);

    record ActiveKpiVersion(
            long kpiId,
            long systemId,
            long tenantId,
            long versionId,
            int versionNumber,
            String code,
            String name,
            KpiSubjectType subjectType,
            KpiPeriodType periodType
    ) {
        public ActiveKpiVersion {
            if (kpiId <= 0 || systemId <= 0 || tenantId <= 0
                    || versionId <= 0 || versionNumber <= 0
                    || code == null || code.isBlank()
                    || name == null || name.isBlank()
                    || subjectType == null || periodType == null) {
                throw new IllegalArgumentException(
                        "Dashboard KPI version is invalid");
            }
            code = code.strip();
            name = name.strip();
        }
    }
}
