package com.unique.examine.module.dashboard.domain;

import java.util.Set;

/** Immutable client behavior; drill-through is restricted to an in-app path. */
public record DashboardWidgetBehavior(
        int refreshSeconds,
        String clickThrough,
        String styleVariant
) {
    private static final Set<String> STYLES = Set.of(
            "STANDARD", "COMPACT", "EMPHASIS");

    public DashboardWidgetBehavior {
        if (refreshSeconds != 0
                && (refreshSeconds < 15 || refreshSeconds > 3_600)) {
            throw invalid("Widget refresh must be manual or 15..3600 seconds");
        }
        if (clickThrough != null) {
            clickThrough = clickThrough.strip();
            if (clickThrough.isEmpty()) {
                clickThrough = null;
            } else if (clickThrough.length() > 300
                    || !clickThrough.startsWith("/")
                    || clickThrough.startsWith("//")
                    || clickThrough.contains("\\")
                    || clickThrough.contains("://")
                    || clickThrough.chars().anyMatch(Character::isISOControl)) {
                throw invalid("Widget click-through must be a safe in-app path");
            }
        }
        styleVariant = styleVariant == null ? "STANDARD"
                : styleVariant.strip().toUpperCase(java.util.Locale.ROOT);
        if (!STYLES.contains(styleVariant)) {
            throw invalid("Widget style variant is unsupported");
        }
    }

    public static DashboardWidgetBehavior defaults() {
        return new DashboardWidgetBehavior(0, null, "STANDARD");
    }

    private static DashboardException invalid(String message) {
        return new DashboardException("DASHBOARD_WIDGET_INVALID", message);
    }
}
