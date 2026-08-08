package com.unique.examine.module.dashboard.domain;

import java.util.regex.Pattern;

/** Mutable context lookup around the existing immutable dashboard engine. */
public record DashboardScopeBinding(
        long dashboardId,
        long systemId,
        long tenantId,
        DashboardPlacement placement,
        String scopeKey,
        long ownerMemberId
) {
    private static final Pattern KEY = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$");

    public DashboardScopeBinding {
        if (dashboardId <= 0 || systemId <= 0 || tenantId <= 0
                || placement == null || placement == DashboardPlacement.SYSTEM_HOME
                || scopeKey == null || !KEY.matcher(scopeKey).matches()
                || placement == DashboardPlacement.PERSONAL_HOME
                != (ownerMemberId > 0)) {
            throw new DashboardException(
                    "DASHBOARD_SCOPE_INVALID",
                    "Dashboard scope binding is invalid");
        }
    }
}
