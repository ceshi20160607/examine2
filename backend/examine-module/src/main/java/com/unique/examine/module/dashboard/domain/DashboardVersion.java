package com.unique.examine.module.dashboard.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

public record DashboardVersion(
        long id,
        long dashboardId,
        long systemId,
        long tenantId,
        int versionNumber,
        long sourceDraftVersion,
        String code,
        DashboardPlacement placement,
        String name,
        String description,
        List<DashboardVersionWidget> widgets,
        String fingerprint,
        long publishedByMemberId,
        Instant publishedAt
) {
    public DashboardVersion {
        if (id <= 0 || dashboardId <= 0 || systemId <= 0 || tenantId <= 0
                || versionNumber <= 0 || sourceDraftVersion <= 0
                || placement == null
                || widgets == null || widgets.isEmpty()
                || widgets.size() > DashboardDraft.MAX_WIDGETS
                || publishedByMemberId <= 0 || publishedAt == null) {
            throw invalid("Published dashboard state is incomplete");
        }
        code = SystemDashboard.code(code);
        name = SystemDashboard.name(name);
        description = SystemDashboard.description(description);
        widgets = List.copyOf(widgets);
        validateWidgets(id, dashboardId, systemId, tenantId, widgets);
        if (fingerprint == null
                || !fingerprint.matches("^[0-9a-f]{64}$")) {
            throw invalid("Published dashboard fingerprint is invalid");
        }
    }

    private static void validateWidgets(
            long versionId,
            long dashboardId,
            long systemId,
            long tenantId,
            List<DashboardVersionWidget> widgets
    ) {
        var codes = new HashSet<String>();
        for (int index = 0; index < widgets.size(); index++) {
            var widget = widgets.get(index);
            if (widget.dashboardVersionId() != versionId
                    || widget.dashboardId() != dashboardId
                    || widget.systemId() != systemId
                    || widget.tenantId() != tenantId
                    || widget.ordinal() != index || !codes.add(widget.code())) {
                throw invalid(
                        "Published dashboard widget identity is inconsistent");
            }
            for (int previous = 0; previous < index; previous++) {
                if (widget.grid().overlaps(widgets.get(previous).grid())) {
                    throw invalid(
                            "Published dashboard widget layout overlaps");
                }
            }
        }
    }

    private static DashboardException invalid(String message) {
        return new DashboardException("DASHBOARD_VERSION_INVALID", message);
    }
}
