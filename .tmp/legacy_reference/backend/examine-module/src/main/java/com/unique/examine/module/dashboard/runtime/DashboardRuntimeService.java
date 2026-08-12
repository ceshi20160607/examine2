package com.unique.examine.module.dashboard.runtime;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.dashboard.api.DashboardMapping;
import com.unique.examine.module.dashboard.api.DashboardViews;
import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.PublishedDashboard;
import com.unique.examine.module.dashboard.service.DashboardService;
import com.unique.examine.module.datasource.runtime.DataSourceRuntimeService;
import com.unique.examine.module.datasource.statistics.api.StatisticsMapping;
import com.unique.examine.module.datasource.statistics.api.StatisticsViews;
import com.unique.examine.module.datasource.statistics.domain.StatisticsFieldPins;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.service.DataSourceStatisticsService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public final class DashboardRuntimeService {
    private final DashboardService dashboards;
    private final DataSourceRuntimeService dataSources;
    private final DataSourceStatisticsService statistics;
    private final DashboardKpiRuntime kpis;

    @Autowired
    public DashboardRuntimeService(
            DashboardService dashboards,
            DataSourceRuntimeService dataSources,
            DataSourceStatisticsService statistics,
            DashboardKpiRuntime kpis
    ) {
        this.dashboards = Objects.requireNonNull(dashboards, "dashboards");
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.kpis = Objects.requireNonNull(kpis, "kpis");
    }

    DashboardRuntimeService(
            DashboardService dashboards,
            DataSourceRuntimeService dataSources
    ) {
        this.dashboards = Objects.requireNonNull(dashboards, "dashboards");
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.statistics = null;
        this.kpis = null;
    }

    DashboardRuntimeService(
            DashboardService dashboards,
            DataSourceRuntimeService dataSources,
            DataSourceStatisticsService statistics
    ) {
        this.dashboards = Objects.requireNonNull(dashboards, "dashboards");
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.kpis = null;
    }

    public DashboardViews.RuntimeDashboard systemHome(RuntimeSession session) {
        return runtime(session, dashboards.activeHome(actor(session)));
    }

    public DashboardViews.RuntimeDashboard byCode(
            RuntimeSession session,
            String code
    ) {
        return runtime(session, dashboards.active(actor(session), code));
    }

    public DashboardViews.RuntimeDashboard published(
            RuntimeSession session,
            PublishedDashboard dashboard
    ) {
        var actor = actor(session);
        if (dashboard == null
                || dashboard.root().systemId() != actor.systemId()
                || dashboard.root().tenantId() != actor.tenantId()) {
            throw new DashboardException(
                    "DASHBOARD_NOT_FOUND", "Dashboard does not exist");
        }
        return runtime(session, dashboard);
    }

    private DashboardViews.RuntimeDashboard runtime(
            RuntimeSession session,
            PublishedDashboard dashboard
    ) {
        var version = dashboard.version();
        var widgets = version.widgets().stream()
                .map(widget -> widget(session, widget))
                .toList();
        return new DashboardViews.RuntimeDashboard(
                Long.toString(dashboard.root().id()), dashboard.root().code(),
                version.name(), version.description(),
                version.placement().name(), Long.toString(version.id()),
                version.versionNumber(), widgets);
    }

    private DashboardViews.RuntimeWidget widget(
            RuntimeSession session,
            DashboardVersionWidget widget
    ) {
        try {
            if (widget.type() == DashboardWidgetType.KPI_VALUE) {
                if (kpis == null) {
                    throw new IllegalStateException(
                            "KPI runtime is unavailable");
                }
                var targets = kpis.targets(session,
                        new DashboardKpiRuntime.PublishedKpiPin(
                                widget.kpiId(), widget.kpiVersionId(),
                                widget.kpiPeriodType()));
                return view(widget, "OK", null, null, null,
                        List.of(), List.of(), targets);
            }
            if (widget.type() == DashboardWidgetType.STAT_COUNT) {
                if (statistics != null) {
                    var result = statistics.statistics(
                            session, widget.dataSourceId(),
                            widget.dataSourceVersionId(),
                            new StatisticsRequest(
                                    StatisticsAggregation.COUNT,
                                    null, null, null));
                    return success(widget, result.matchedRecordCount(), null,
                            List.of(), List.of());
                }
                var result = dataSources.rows(
                        session, widget.dataSourceId(),
                        widget.dataSourceVersionId(), 1, 1);
                return success(widget, result.total(), null,
                        List.of(), List.of());
            }
            if (widget.type() != DashboardWidgetType.DATA_LIST
                    && widget.type() != DashboardWidgetType.TODO_LIST
                    && widget.type() != DashboardWidgetType.QUICK_ENTRY) {
                if (statistics == null) {
                    throw new IllegalStateException(
                            "Statistics runtime is unavailable");
                }
                var result = statistics.statistics(
                        session, widget.dataSourceId(),
                        widget.dataSourceVersionId(),
                        widget.statistics().request(), pins(widget));
                var view = StatisticsMapping.result(result);
                if (widget.type() == DashboardWidgetType.RANKING) {
                    view = ranking(view);
                }
                return success(widget, result.matchedRecordCount(), view,
                        List.of(), List.of());
            }
            var metadata = dataSources.metadata(
                    session, widget.dataSourceId(),
                    widget.dataSourceVersionId());
            var result = dataSources.rows(
                    session, widget.dataSourceId(),
                    widget.dataSourceVersionId(), 1, widget.rowLimit());
            if (result.partial()) {
                var detail = result.failedSourceAliases().isEmpty()
                        ? "The source returned a bounded partial result"
                        : "Joined sources unavailable: "
                        + String.join(",", result.failedSourceAliases());
                return view(widget, "PARTIAL",
                        new DashboardViews.RuntimeWidgetError(
                                "DASHBOARD_WIDGET_PARTIAL_RESULT", detail),
                        result.total(), null, metadata.outputFields(),
                        result.rows(), List.of());
            }
            return success(widget, result.total(), null,
                    metadata.outputFields(), result.rows());
        } catch (BusinessException exception) {
            return failure(widget, exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            return failure(
                    widget, "DASHBOARD_WIDGET_EXECUTION_FAILED",
                    "Dashboard widget could not be executed");
        }
    }

    private static DashboardViews.RuntimeWidget success(
            DashboardVersionWidget widget,
            long total,
            StatisticsViews.Result statisticsResult,
            List<com.unique.examine.module.datasource.api.DataSourceViews.RuntimeField>
                    fields,
            List<com.unique.examine.module.datasource.api.DataSourceViews.RuntimeRow>
                    rows
    ) {
        return view(widget, "OK", null, total, statisticsResult,
                fields, rows, List.of());
    }

    private static DashboardViews.RuntimeWidget failure(
            DashboardVersionWidget widget,
            String code,
            String message
    ) {
        return view(widget, "ERROR",
                new DashboardViews.RuntimeWidgetError(code, message),
                null, null, List.of(), List.of(), List.of());
    }

    private static DashboardViews.RuntimeWidget view(
            DashboardVersionWidget widget,
            String status,
            DashboardViews.RuntimeWidgetError error,
            Long total,
            StatisticsViews.Result statisticsResult,
            List<com.unique.examine.module.datasource.api.DataSourceViews.RuntimeField>
                    fields,
            List<com.unique.examine.module.datasource.api.DataSourceViews.RuntimeRow>
                    rows,
            List<com.unique.examine.module.kpi.api.KpiViews.Target> kpiTargets
    ) {
        return new DashboardViews.RuntimeWidget(
                widget.code(), widget.type().name(), widget.title(),
                widget.ordinal(), DashboardMapping.grid(widget.grid()),
                string(widget.dataSourceId()),
                string(widget.dataSourceVersionId()),
                widget.dataSourceVersionNumber(), widget.dataSourceCode(),
                string(widget.kpiId()), string(widget.kpiVersionId()),
                widget.kpiVersionNumber(), widget.kpiCode(), widget.kpiName(),
                widget.kpiSubjectType() == null
                        ? null : widget.kpiSubjectType().name(),
                widget.kpiPeriodType() == null
                        ? null : widget.kpiPeriodType().name(),
                widget.rowLimit(), status, error, total, statisticsResult,
                fields, rows, kpiTargets,
                new DashboardViews.Behavior(
                        widget.behavior().refreshSeconds(),
                        widget.behavior().clickThrough(),
                        widget.behavior().styleVariant()));
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static StatisticsViews.Result ranking(StatisticsViews.Result value) {
        var groups = value.groupBuckets().stream()
                .sorted(java.util.Comparator
                        .comparing((StatisticsViews.GroupBucket bucket) ->
                                bucket.value() == null
                                        ? java.math.BigDecimal.ZERO
                                        : new java.math.BigDecimal(bucket.value()))
                        .reversed()
                        .thenComparing(bucket -> bucket.key() == null
                                ? "" : bucket.key()))
                .toList();
        return new StatisticsViews.Result(
                value.queryId(), value.dataSourceId(), value.dataSourceCode(),
                value.dataSourceVersionId(), value.dataSourceVersionNumber(),
                value.moduleCode(), value.schemaVersionId(),
                value.aggregation(), value.measureFieldCode(), value.value(),
                value.matchedRecordCount(), value.aggregateCount(),
                value.bucketCount(), groups, value.trendBuckets(),
                value.totalBucketCount(), value.truncated());
    }

    private static DashboardActor actor(RuntimeSession session) {
        Objects.requireNonNull(session, "session");
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DashboardException(
                    "DASHBOARD_TENANT_REQUIRED",
                    "Select an active tenant before reading a dashboard");
        }
        return new DashboardActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static StatisticsFieldPins pins(DashboardVersionWidget widget) {
        var statistics = widget.statistics();
        return new StatisticsFieldPins(
                pin(statistics.measure()), pin(statistics.group()),
                pin(statistics.time()));
    }

    private static StatisticsFieldPins.Field pin(
            com.unique.examine.module.dashboard.domain.DashboardStatisticsSnapshot.Field
                    field
    ) {
        return field == null ? null : new StatisticsFieldPins.Field(
                field.logicalFieldId(), field.code(), field.name(),
                field.type(), field.queryType());
    }
}
