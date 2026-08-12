package com.unique.examine.module.kpi.domain;

public record PublishedKpi(KpiDefinition root, KpiVersion version) {
    public PublishedKpi {
        if (root == null || version == null || root.id() != version.kpiId()
                || root.systemId() != version.systemId()
                || root.tenantId() != version.tenantId()
                || !root.code().equals(version.code())
                || root.activeVersionId() == null
                || root.activeVersionId() != version.id()
                || root.activeVersionNumber() == null
                || root.activeVersionNumber() != version.versionNumber()) {
            throw new IllegalArgumentException(
                    "Published KPI active pointer is inconsistent");
        }
    }
}
