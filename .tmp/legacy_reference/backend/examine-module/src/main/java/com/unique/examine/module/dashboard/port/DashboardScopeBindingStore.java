package com.unique.examine.module.dashboard.port;

import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardScopeBinding;

import java.util.List;
import java.util.Optional;

public interface DashboardScopeBindingStore {
    void bind(DashboardScopeBinding binding);

    Optional<DashboardScopeBinding> find(
            long systemId,
            long tenantId,
            DashboardPlacement placement,
            String scopeKey,
            long ownerMemberId);

    Optional<DashboardScopeBinding> findByDashboard(
            long systemId,
            long tenantId,
            long dashboardId);

    List<DashboardScopeBinding> findPublic(
            long systemId,
            long tenantId);

    List<DashboardScopeBinding> findPersonal(
            long systemId,
            long tenantId,
            long ownerMemberId);
}
