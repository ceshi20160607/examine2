package com.unique.examine.module.dashboard.api;

import com.unique.examine.module.dashboard.domain.DashboardCheckReport;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardGrid;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.dashboard.domain.DashboardStatisticsSnapshot;
import com.unique.examine.module.dashboard.domain.DashboardWidgetDraft;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.DashboardWidgetBehavior;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;

public final class DashboardMapping {
    private DashboardMapping() {
    }

    public static DashboardDraft draft(DashboardRequests.SaveDraft request) {
        if (request == null) {
            throw invalid("Dashboard draft is required");
        }
        return new DashboardDraft(request.widgets().stream()
                .map(DashboardMapping::widget).toList());
    }

    public static DashboardPlacement placement(String value) {
        try {
            return DashboardPlacement.valueOf(value);
        } catch (RuntimeException exception) {
            throw invalid("Dashboard placement is unsupported");
        }
    }

    public static DashboardViews.Dashboard dashboard(SystemDashboard value) {
        return dashboard(value, null);
    }

    public static DashboardViews.Dashboard dashboard(
            SystemDashboard value,
            String scopeKey
    ) {
        return new DashboardViews.Dashboard(
                Long.toString(value.id()), Long.toString(value.systemId()),
                Long.toString(value.tenantId()), value.code(),
                value.placement().name(), scopeKey, value.name(),
                value.description(), value.draftVersion(),
                string(value.activeVersionId()), value.activeVersionNumber(),
                value.createdAt().toString(), value.updatedAt().toString(),
                value.version(), draft(value.draft()));
    }

    public static DashboardViews.CheckResult check(
            DashboardCheckReport report
    ) {
        return new DashboardViews.CheckResult(
                Long.toString(report.dashboardId()), report.draftVersion(),
                report.publishable(), report.blockerCount(),
                report.warningCount(), report.issues().stream()
                .map(issue -> new DashboardViews.CheckIssue(
                        issue.severity().name(), issue.code(), issue.path(),
                        issue.message())).toList());
    }

    public static DashboardViews.Version version(
            DashboardVersion version,
            Long activeVersionId
    ) {
        var widgets = version.widgets().stream()
                .map(DashboardMapping::versionWidget).toList();
        return new DashboardViews.Version(
                Long.toString(version.id()),
                Long.toString(version.dashboardId()),
                version.versionNumber(), version.sourceDraftVersion(),
                version.code(), version.placement().name(), version.name(),
                version.description(), new DashboardViews.Draft(
                widgets.stream().map(widget ->
                        new DashboardViews.WidgetDraft(
                                widget.code(), widget.type(), widget.title(),
                                widget.dataSourceId(), widget.kpiId(),
                                widget.rowLimit(),
                                widget.statistics(),
                                widget.behavior(),
                                widget.grid())).toList()),
                version.fingerprint(), widgets.size(),
                Long.toString(version.publishedByMemberId()),
                version.publishedAt().toString(),
                activeVersionId != null && activeVersionId == version.id(),
                widgets);
    }

    public static DashboardViews.Grid grid(DashboardGrid value) {
        return new DashboardViews.Grid(
                value.x(), value.y(), value.width(), value.height());
    }

    private static DashboardWidgetDraft widget(
            DashboardRequests.Widget value
    ) {
        final DashboardWidgetType type;
        try {
            type = DashboardWidgetType.valueOf(value.type());
        } catch (RuntimeException exception) {
            throw invalid("Dashboard widget type is unsupported");
        }
        var grid = value.grid();
        return new DashboardWidgetDraft(
                value.code(), type, value.title(),
                optionalPositiveId(value.dataSourceId(), "dataSourceId"),
                optionalPositiveId(value.kpiId(), "kpiId"),
                value.rowLimit(), statistics(value.statistics()),
                behavior(value.behavior()),
                new DashboardGrid(
                grid.x(), grid.y(), grid.width(), grid.height()));
    }

    private static DashboardViews.Draft draft(DashboardDraft value) {
        return new DashboardViews.Draft(value.widgets().stream()
                .map(widget -> new DashboardViews.WidgetDraft(
                        widget.code(), widget.type().name(), widget.title(),
                        string(widget.dataSourceId()), string(widget.kpiId()),
                        widget.rowLimit(), statistics(widget.statistics()),
                        behavior(widget.behavior()),
                        grid(widget.grid())))
                .toList());
    }

    private static DashboardViews.VersionWidget versionWidget(
            DashboardVersionWidget widget
    ) {
        return new DashboardViews.VersionWidget(
                Long.toString(widget.id()), widget.ordinal(), widget.code(),
                widget.type().name(), widget.title(),
                string(widget.dataSourceId()), widget.dataSourceCode(),
                string(widget.dataSourceVersionId()),
                widget.dataSourceVersionNumber(), widget.moduleCode(),
                widget.schemaVersionId(), string(widget.kpiId()),
                string(widget.kpiVersionId()), widget.kpiVersionNumber(),
                widget.kpiCode(), widget.kpiName(),
                widget.kpiSubjectType() == null ? null
                        : widget.kpiSubjectType().name(),
                widget.kpiPeriodType() == null ? null
                        : widget.kpiPeriodType().name(), widget.rowLimit(),
                statistics(widget.statistics()),
                behavior(widget.behavior()),
                grid(widget.grid()));
    }

    private static DashboardWidgetBehavior behavior(
            DashboardRequests.Behavior value
    ) {
        return value == null ? DashboardWidgetBehavior.defaults()
                : new DashboardWidgetBehavior(
                value.refreshSeconds() == null ? 0 : value.refreshSeconds(),
                value.clickThrough(), value.styleVariant());
    }

    private static DashboardViews.Behavior behavior(
            DashboardWidgetBehavior value
    ) {
        return new DashboardViews.Behavior(
                value.refreshSeconds(), value.clickThrough(),
                value.styleVariant());
    }

    private static StatisticsRequest statistics(
            DashboardRequests.Statistic value
    ) {
        if (value == null) {
            return null;
        }
        final StatisticsAggregation aggregation;
        try {
            aggregation = StatisticsAggregation.valueOf(value.aggregation());
        } catch (RuntimeException exception) {
            throw invalid("Dashboard statistics aggregation is unsupported");
        }
        StatisticsRequest.Grouping grouping = value.grouping() == null
                ? null : new StatisticsRequest.Grouping(
                value.grouping().fieldCode(),
                value.grouping().bucketLimit());
        StatisticsRequest.Trend trend = null;
        if (value.trend() != null) {
            final StatisticsGrain grain;
            try {
                grain = StatisticsGrain.valueOf(value.trend().grain());
            } catch (RuntimeException exception) {
                throw invalid("Dashboard statistics grain is unsupported");
            }
            trend = new StatisticsRequest.Trend(
                    value.trend().fieldCode(), grain,
                    value.trend().startInclusive(),
                    value.trend().endExclusive());
        }
        return new StatisticsRequest(
                aggregation, value.measureFieldCode(), grouping, trend);
    }

    private static DashboardViews.Statistic statistics(
            StatisticsRequest value
    ) {
        if (value == null) {
            return null;
        }
        var grouping = value.grouping() == null ? null
                : new DashboardViews.Grouping(
                value.grouping().fieldCode(), value.grouping().bucketLimit());
        var trend = value.trend() == null ? null
                : new DashboardViews.Trend(
                value.trend().fieldCode(), value.trend().grain().name(),
                value.trend().startInclusive().toString(),
                value.trend().endExclusive().toString());
        return new DashboardViews.Statistic(
                value.aggregation().name(), value.measureFieldCode(),
                grouping, trend, null, null, null);
    }

    private static DashboardViews.Statistic statistics(
            DashboardStatisticsSnapshot value
    ) {
        if (value == null) {
            return null;
        }
        var request = statistics(value.request());
        return new DashboardViews.Statistic(
                request.aggregation(), request.measureFieldCode(),
                request.grouping(), request.trend(),
                statisticField(value.measure()),
                statisticField(value.group()),
                statisticField(value.time()));
    }

    private static DashboardViews.StatisticField statisticField(
            DashboardStatisticsSnapshot.Field value
    ) {
        return value == null ? null : new DashboardViews.StatisticField(
                Long.toString(value.logicalFieldId()), value.code(),
                value.name(), value.type(), value.queryType());
    }

    private static long positiveId(String value, String field) {
        if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
            throw invalid(field + " must be a positive decimal id string");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(field + " is outside the supported id range");
        }
    }

    private static Long optionalPositiveId(String value, String field) {
        return value == null ? null : positiveId(value, field);
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static DashboardException invalid(String message) {
        return new DashboardException("DASHBOARD_REQUEST_INVALID", message);
    }
}
