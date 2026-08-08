package com.unique.examine.module.dashboard.domain;

public record PublishedDashboard(
        SystemDashboard root,
        DashboardVersion version
) {
    public PublishedDashboard {
        if (root == null || version == null
                || root.id() != version.dashboardId()
                || root.systemId() != version.systemId()
                || root.tenantId() != version.tenantId()
                || !root.code().equals(version.code())
                || root.placement() != version.placement()
                || root.activeVersionId() == null
                || root.activeVersionId() != version.id()
                || root.activeVersionNumber() == null
                || root.activeVersionNumber() != version.versionNumber()) {
            throw new IllegalArgumentException(
                    "Published dashboard active pointer is inconsistent");
        }
    }
}
