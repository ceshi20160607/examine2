package com.unique.examine.module.dashboard.domain;

import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;

import java.util.regex.Pattern;

public record DashboardWidgetDraft(
        String code,
        DashboardWidgetType type,
        String title,
        Long dataSourceId,
        Long kpiId,
        Integer rowLimit,
        StatisticsRequest statistics,
        DashboardWidgetBehavior behavior,
        DashboardGrid grid
) {
    private static final Pattern CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public DashboardWidgetDraft {
        code = code(code);
        title = title(title);
        if (type == null || grid == null) {
            throw invalid("Dashboard widget state is incomplete");
        }
        behavior = behavior == null
                ? DashboardWidgetBehavior.defaults() : behavior;
        var kpi = type == DashboardWidgetType.KPI_VALUE;
        if (kpi && (kpiId == null || kpiId <= 0 || dataSourceId != null)
                || !kpi && (dataSourceId == null || dataSourceId <= 0
                || kpiId != null)) {
            throw invalid("Dashboard widget source selection is invalid");
        }
        if (kpi && (rowLimit != null || statistics != null)) {
            throw invalid("KPI_VALUE does not accept row or statistics settings");
        }
    }

    public DashboardWidgetDraft(
            String code,
            DashboardWidgetType type,
            String title,
            Long dataSourceId,
            Long kpiId,
            Integer rowLimit,
            StatisticsRequest statistics,
            DashboardGrid grid
    ) {
        this(code, type, title, dataSourceId, kpiId, rowLimit, statistics,
                DashboardWidgetBehavior.defaults(), grid);
    }

    public DashboardWidgetDraft(
            String code,
            DashboardWidgetType type,
            String title,
            long dataSourceId,
            Integer rowLimit,
            DashboardGrid grid
    ) {
        this(code, type, title, dataSourceId, null, rowLimit, null,
                DashboardWidgetBehavior.defaults(), grid);
    }

    public DashboardWidgetDraft(
            String code,
            DashboardWidgetType type,
            String title,
            long dataSourceId,
            Integer rowLimit,
            StatisticsRequest statistics,
            DashboardGrid grid
    ) {
        this(code, type, title, dataSourceId, null, rowLimit, statistics,
                DashboardWidgetBehavior.defaults(), grid);
    }

    public static DashboardWidgetDraft kpiValue(
            String code,
            String title,
            long kpiId,
            DashboardGrid grid
    ) {
        return new DashboardWidgetDraft(code, DashboardWidgetType.KPI_VALUE,
                title, null, kpiId, null, null,
                DashboardWidgetBehavior.defaults(), grid);
    }

    static void validateConfiguration(
            DashboardWidgetType type,
            Integer rowLimit,
            StatisticsRequest statistics
    ) {
        if (type == DashboardWidgetType.KPI_VALUE) {
            if (rowLimit != null || statistics != null) {
                throw invalid("KPI_VALUE does not accept row or statistics settings");
            }
            return;
        }
        if (type == DashboardWidgetType.STAT_COUNT) {
            if (rowLimit != null || statistics != null) {
                throw invalid(
                        "STAT_COUNT does not accept row or statistics settings");
            }
            return;
        }
        if (type == DashboardWidgetType.DATA_LIST
                || type == DashboardWidgetType.TODO_LIST
                || type == DashboardWidgetType.QUICK_ENTRY) {
            if (rowLimit == null || rowLimit < 1 || rowLimit > 20
                    || statistics != null) {
                throw invalid(
                    "List and quick-entry row limit must be between 1 and 20");
            }
            return;
        }
        if (rowLimit != null || statistics == null) {
            throw invalid(
                    "Statistics widgets require statistics and no row limit");
        }
        if ((type == DashboardWidgetType.STAT_VALUE
                || type == DashboardWidgetType.PROGRESS)
                && (statistics.grouping() != null
                || statistics.trend() != null)) {
            throw invalid("STAT_VALUE accepts only an aggregate value");
        }
        if ((type == DashboardWidgetType.BAR_CHART
                || type == DashboardWidgetType.PIE_CHART
                || type == DashboardWidgetType.RANKING)
                && statistics.grouping() == null) {
            throw invalid("Grouped charts require grouping settings");
        }
        if (type == DashboardWidgetType.LINE_TREND
                && statistics.trend() == null) {
            throw invalid("LINE_TREND requires trend settings");
        }
    }

    public static String code(String value) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw invalid("Dashboard widget code is invalid");
        }
        return value;
    }

    public static String title(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > 200) {
            throw invalid(
                    "Dashboard widget title must contain 1 to 200 characters");
        }
        return value.strip();
    }

    private static DashboardException invalid(String message) {
        return new DashboardException("DASHBOARD_WIDGET_INVALID", message);
    }
}
