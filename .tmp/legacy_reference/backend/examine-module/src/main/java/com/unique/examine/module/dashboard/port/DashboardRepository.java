package com.unique.examine.module.dashboard.port;

import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.SystemDashboard;

import java.util.List;
import java.util.Optional;

public interface DashboardRepository {
    long nextDashboardId();

    long nextVersionId();

    long nextVersionWidgetId();

    Optional<SystemDashboard> findById(
            long systemId,
            long tenantId,
            long dashboardId);

    Optional<SystemDashboard> findByCode(
            long systemId,
            long tenantId,
            String code);

    Optional<SystemDashboard> findByPlacement(
            long systemId,
            long tenantId,
            DashboardPlacement placement);

    List<SystemDashboard> findAll(long systemId, long tenantId);

    SystemDashboard insert(SystemDashboard root);

    SystemDashboard saveDraft(
            SystemDashboard expected,
            SystemDashboard revised);

    DashboardVersion publish(
            SystemDashboard expected,
            SystemDashboard activated,
            DashboardVersion version);

    Optional<DashboardVersion> findActiveVersion(
            long systemId,
            long tenantId,
            long dashboardId);

    Optional<DashboardVersion> findVersion(
            long systemId,
            long tenantId,
            long dashboardId,
            int versionNumber);

    List<DashboardVersion> findVersions(
            long systemId,
            long tenantId,
            long dashboardId);
}
