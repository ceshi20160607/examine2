package com.unique.examine.module.manage.service;

import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.service.DashboardService;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.service.KpiService;
import com.unique.examine.module.manage.api.ConfigRecoveryViews;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ConfigRecoveryService {
    private final ConfigPublicationService moduleConfig;
    private final DataSourceService dataSources;
    private final DashboardService dashboards;
    private final KpiService kpis;
    private final ReportService reports;
    private final ConfigMutationSupport mutations;

    public ConfigRecoveryService(
            ConfigPublicationService moduleConfig,
            DataSourceService dataSources,
            DashboardService dashboards,
            KpiService kpis,
            ReportService reports,
            ConfigMutationSupport mutations
    ) {
        this.moduleConfig = moduleConfig;
        this.dataSources = dataSources;
        this.dashboards = dashboards;
        this.kpis = kpis;
        this.reports = reports;
        this.mutations = mutations;
    }

    @Transactional
    public ConfigRecoveryViews.RestoreResult restoreModuleConfig(
            ConfigSession session,
            long versionId,
            int versionNumber,
            ConfigRequests.RestoreConfig request,
            String idempotencyKey,
            RequestContext context
    ) {
        var target = moduleConfig.versions(session.systemId()).stream()
                .filter(version -> version.id().equals(
                        Long.toString(versionId)))
                .findFirst()
                .orElseThrow(ConfigErrors::notFound);
        if (Integer.parseInt(target.versionNo()) != versionNumber) {
            throw ConfigErrors.invalid("versionNumber 与版本标识不一致");
        }
        var root = moduleConfig.restoreDraft(
                session, versionId, request, idempotencyKey, context);
        return result("MODULE_CONFIG", Long.toString(versionId),
                versionNumber, root.draftRevision(), root.activeVersionId());
    }

    @Transactional
    public ConfigRecoveryViews.RestoreResult restoreDataSource(
            ConfigSession session,
            long dataSourceId,
            int versionNumber,
            ConfigRequests.RestoreConfig request,
            String idempotencyKey,
            RequestContext context
    ) {
        var expected = expected(request);
        return mutations.idempotent(scope(session, "DATA_SOURCE",
                        dataSourceId, versionNumber), idempotencyKey, request,
                ConfigRecoveryViews.RestoreResult.class, () -> {
                    var actor = new DataSourceActor(
                            session.systemId(), tenant(session),
                            session.memberId());
                    var restored = dataSources.restoreVersion(actor,
                            dataSourceId, versionNumber, expected);
                    changed(session, "DATA_SOURCE", dataSourceId,
                            versionNumber, expected, restored.draftVersion(),
                            request, context);
                    return result("DATA_SOURCE",
                            Long.toString(dataSourceId), versionNumber,
                            Long.toString(restored.draftVersion()),
                            string(restored.activeVersionNumber()));
                });
    }

    @Transactional
    public ConfigRecoveryViews.RestoreResult restoreDashboard(
            ConfigSession session,
            long dashboardId,
            int versionNumber,
            ConfigRequests.RestoreConfig request,
            String idempotencyKey,
            RequestContext context
    ) {
        var expected = expected(request);
        return mutations.idempotent(scope(session, "DASHBOARD",
                        dashboardId, versionNumber), idempotencyKey, request,
                ConfigRecoveryViews.RestoreResult.class, () -> {
                    var actor = new DashboardActor(
                            session.systemId(), tenant(session),
                            session.memberId());
                    var restored = dashboards.restoreVersion(actor,
                            dashboardId, versionNumber, expected);
                    changed(session, "DASHBOARD", dashboardId,
                            versionNumber, expected, restored.draftVersion(),
                            request, context);
                    return result("DASHBOARD", Long.toString(dashboardId),
                            versionNumber,
                            Long.toString(restored.draftVersion()),
                            string(restored.activeVersionNumber()));
                });
    }

    @Transactional
    public ConfigRecoveryViews.RestoreResult restoreKpi(
            ConfigSession session,
            long kpiId,
            int versionNumber,
            ConfigRequests.RestoreConfig request,
            String idempotencyKey,
            RequestContext context
    ) {
        var expected = expected(request);
        return mutations.idempotent(scope(session, "KPI", kpiId,
                        versionNumber), idempotencyKey, request,
                ConfigRecoveryViews.RestoreResult.class, () -> {
                    var actor = new KpiActor(session.systemId(),
                            tenant(session), session.memberId());
                    var restored = kpis.restoreVersion(actor, kpiId,
                            versionNumber, expected);
                    changed(session, "KPI", kpiId, versionNumber, expected,
                            restored.draftVersion(), request, context);
                    return result("KPI", Long.toString(kpiId),
                            versionNumber,
                            Long.toString(restored.draftVersion()),
                            string(restored.activeVersionNumber()));
                });
    }

    @Transactional
    public ConfigRecoveryViews.RestoreResult restoreReport(
            ConfigSession session,
            long reportId,
            int versionNumber,
            ConfigRequests.RestoreConfig request,
            String idempotencyKey,
            RequestContext context
    ) {
        var expected = expected(request);
        return mutations.idempotent(scope(session, "REPORT", reportId,
                        versionNumber), idempotencyKey, request,
                ConfigRecoveryViews.RestoreResult.class, () -> {
                    var actor = new ReportActor(session.systemId(),
                            tenant(session), session.memberId());
                    var restored = reports.restoreVersion(actor, reportId,
                            versionNumber, expected);
                    changed(session, "REPORT", reportId, versionNumber,
                            expected, restored.draftVersion(), request,
                            context);
                    return result("REPORT", Long.toString(reportId),
                            versionNumber,
                            Long.toString(restored.draftVersion()),
                            string(restored.activeVersionNumber()));
                });
    }

    private void changed(
            ConfigSession session,
            String owner,
            long resourceId,
            int sourceVersionNumber,
            long beforeDraftVersion,
            long afterDraftVersion,
            ConfigRequests.RestoreConfig request,
            RequestContext context
    ) {
        mutations.changed(session, owner, Long.toString(resourceId),
                owner + "_VERSION_RESTORED_TO_DRAFT",
                Map.of("draftVersion", Long.toString(beforeDraftVersion)),
                Map.of("draftVersion", Long.toString(afterDraftVersion),
                        "sourceVersionNumber",
                        Integer.toString(sourceVersionNumber),
                        "reason", request.reason().trim()),
                afterDraftVersion,
                "restore-draft:" + sourceVersionNumber, context);
    }

    private static ConfigRecoveryViews.RestoreResult result(
            String owner,
            String resourceId,
            int sourceVersionNumber,
            String draftVersion,
            String activeVersionReference
    ) {
        return new ConfigRecoveryViews.RestoreResult(owner, resourceId,
                sourceVersionNumber, draftVersion, activeVersionReference,
                "DRAFT_RESTORED");
    }

    private static long expected(ConfigRequests.RestoreConfig request) {
        var value = ConfigErrors.version(request.expectedVersion());
        if (value <= 0) {
            throw ConfigErrors.invalid("expectedVersion 格式无效");
        }
        return value;
    }

    private static long tenant(ConfigSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw ConfigErrors.invalid("恢复配置前必须选择租户");
        }
        return session.tenantId();
    }

    private static String scope(
            ConfigSession session,
            String owner,
            long resourceId,
            int versionNumber
    ) {
        return session.systemId() + ":config-recovery:" + owner + ":"
                + resourceId + ":" + versionNumber;
    }

    private static String string(Integer value) {
        return value == null ? null : Integer.toString(value);
    }
}
