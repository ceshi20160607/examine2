package com.unique.examine.module.dashboard.domain;

import java.util.List;

public record DashboardDraft(List<DashboardWidgetDraft> widgets) {
    public static final int MAX_WIDGETS = 20;

    public DashboardDraft {
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
        if (widgets.size() > MAX_WIDGETS) {
            throw new DashboardException(
                    "DASHBOARD_DRAFT_INVALID",
                    "Dashboard draft cannot exceed 20 widgets");
        }
    }

    public static DashboardDraft empty() {
        return new DashboardDraft(List.of());
    }
}
