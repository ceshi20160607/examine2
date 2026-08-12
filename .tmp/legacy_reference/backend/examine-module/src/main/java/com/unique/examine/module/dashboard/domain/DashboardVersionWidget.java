package com.unique.examine.module.dashboard.domain;

import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;

public record DashboardVersionWidget(
        long id,
        long dashboardVersionId,
        long dashboardId,
        long systemId,
        long tenantId,
        int ordinal,
        String code,
        DashboardWidgetType type,
        String title,
        Long dataSourceId,
        String dataSourceCode,
        Long dataSourceVersionId,
        Integer dataSourceVersionNumber,
        String moduleCode,
        String schemaVersionId,
        Integer rowLimit,
        DashboardStatisticsSnapshot statistics,
        Long kpiId,
        Long kpiVersionId,
        Integer kpiVersionNumber,
        String kpiCode,
        String kpiName,
        KpiSubjectType kpiSubjectType,
        KpiPeriodType kpiPeriodType,
        DashboardWidgetBehavior behavior,
        DashboardGrid grid
) {
    public DashboardVersionWidget {
        if (id <= 0 || dashboardVersionId <= 0 || dashboardId <= 0
                || systemId <= 0 || tenantId <= 0 || ordinal < 0
                || ordinal >= DashboardDraft.MAX_WIDGETS
                || type == null || grid == null || !grid.withinBounds()) {
            throw invalid("Published dashboard widget state is incomplete");
        }
        code = DashboardWidgetDraft.code(code);
        title = DashboardWidgetDraft.title(title);
        behavior = behavior == null
                ? DashboardWidgetBehavior.defaults() : behavior;
        var kpi = type == DashboardWidgetType.KPI_VALUE;
        if (kpi) {
            if (dataSourceId != null || dataSourceCode != null
                    || dataSourceVersionId != null
                    || dataSourceVersionNumber != null || moduleCode != null
                    || schemaVersionId != null || kpiId == null || kpiId <= 0
                    || kpiVersionId == null || kpiVersionId <= 0
                    || kpiVersionNumber == null || kpiVersionNumber <= 0
                    || kpiSubjectType == null || kpiPeriodType == null) {
                throw invalid("Published KPI widget pins are invalid");
            }
            kpiCode = DashboardWidgetDraft.code(kpiCode);
            kpiName = text(kpiName, "KPI name", 200);
        } else {
            if (dataSourceId == null || dataSourceId <= 0
                    || dataSourceVersionId == null || dataSourceVersionId <= 0
                    || dataSourceVersionNumber == null
                    || dataSourceVersionNumber <= 0 || kpiId != null
                    || kpiVersionId != null || kpiVersionNumber != null
                    || kpiCode != null || kpiName != null
                    || kpiSubjectType != null || kpiPeriodType != null) {
                throw invalid("Published data-source widget pins are invalid");
            }
            dataSourceCode = text(dataSourceCode, "data source code", 64);
            moduleCode = text(moduleCode, "module code", 100);
            schemaVersionId = text(schemaVersionId, "schema version", 200);
        }
        try {
            DashboardWidgetDraft.validateConfiguration(
                    type, rowLimit,
                    statistics == null ? null : statistics.request());
        } catch (DashboardException exception) {
            throw invalid("Published dashboard widget configuration is invalid");
        }
    }

    public DashboardVersionWidget(
            long id, long dashboardVersionId, long dashboardId,
            long systemId, long tenantId, int ordinal, String code,
            DashboardWidgetType type, String title, Long dataSourceId,
            String dataSourceCode, Long dataSourceVersionId,
            Integer dataSourceVersionNumber, String moduleCode,
            String schemaVersionId, Integer rowLimit,
            DashboardStatisticsSnapshot statistics, Long kpiId,
            Long kpiVersionId, Integer kpiVersionNumber, String kpiCode,
            String kpiName, KpiSubjectType kpiSubjectType,
            KpiPeriodType kpiPeriodType, DashboardGrid grid
    ) {
        this(id, dashboardVersionId, dashboardId, systemId, tenantId,
                ordinal, code, type, title, dataSourceId, dataSourceCode,
                dataSourceVersionId, dataSourceVersionNumber, moduleCode,
                schemaVersionId, rowLimit, statistics, kpiId, kpiVersionId,
                kpiVersionNumber, kpiCode, kpiName, kpiSubjectType,
                kpiPeriodType, DashboardWidgetBehavior.defaults(), grid);
    }

    public DashboardVersionWidget(
            long id,
            long dashboardVersionId,
            long dashboardId,
            long systemId,
            long tenantId,
            int ordinal,
            String code,
            DashboardWidgetType type,
            String title,
            long dataSourceId,
            String dataSourceCode,
            long dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleCode,
            String schemaVersionId,
            Integer rowLimit,
            DashboardGrid grid
    ) {
        this(id, dashboardVersionId, dashboardId, systemId, tenantId,
                ordinal, code, type, title, dataSourceId, dataSourceCode,
                dataSourceVersionId, dataSourceVersionNumber, moduleCode,
                schemaVersionId, rowLimit, null, null, null, null, null,
                null, null, null, DashboardWidgetBehavior.defaults(), grid);
    }

    public DashboardVersionWidget(
            long id,
            long dashboardVersionId,
            long dashboardId,
            long systemId,
            long tenantId,
            int ordinal,
            String code,
            DashboardWidgetType type,
            String title,
            long dataSourceId,
            String dataSourceCode,
            long dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleCode,
            String schemaVersionId,
            Integer rowLimit,
            DashboardStatisticsSnapshot statistics,
            DashboardGrid grid
    ) {
        this(id, dashboardVersionId, dashboardId, systemId, tenantId,
                ordinal, code, type, title, dataSourceId, dataSourceCode,
                dataSourceVersionId, dataSourceVersionNumber, moduleCode,
                schemaVersionId, rowLimit, statistics, null, null, null,
                null, null, null, null, DashboardWidgetBehavior.defaults(),
                grid);
    }

    public static DashboardVersionWidget kpiValue(
            long id,
            long dashboardVersionId,
            long dashboardId,
            long systemId,
            long tenantId,
            int ordinal,
            String code,
            String title,
            long kpiId,
            long kpiVersionId,
            int kpiVersionNumber,
            String kpiCode,
            String kpiName,
            KpiSubjectType subjectType,
            KpiPeriodType periodType,
            DashboardGrid grid
    ) {
        return kpiValue(id, dashboardVersionId, dashboardId, systemId,
                tenantId, ordinal, code, title, kpiId, kpiVersionId,
                kpiVersionNumber, kpiCode, kpiName, subjectType, periodType,
                DashboardWidgetBehavior.defaults(), grid);
    }

    public static DashboardVersionWidget kpiValue(
            long id,
            long dashboardVersionId,
            long dashboardId,
            long systemId,
            long tenantId,
            int ordinal,
            String code,
            String title,
            long kpiId,
            long kpiVersionId,
            int kpiVersionNumber,
            String kpiCode,
            String kpiName,
            KpiSubjectType subjectType,
            KpiPeriodType periodType,
            DashboardWidgetBehavior behavior,
            DashboardGrid grid
    ) {
        return new DashboardVersionWidget(id, dashboardVersionId, dashboardId,
                systemId, tenantId, ordinal, code,
                DashboardWidgetType.KPI_VALUE, title, null, null, null, null,
                null, null, null, null, kpiId, kpiVersionId,
                kpiVersionNumber, kpiCode, kpiName, subjectType, periodType,
                behavior, grid);
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid("Published dashboard " + name + " is invalid");
        }
        return value.strip();
    }

    private static DashboardException invalid(String message) {
        return new DashboardException(
                "DASHBOARD_VERSION_WIDGET_INVALID", message);
    }
}
