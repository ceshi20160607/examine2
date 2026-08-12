package com.unique.examine.module.dashboard.api;

import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.datasource.statistics.api.StatisticsViews;
import com.unique.examine.module.kpi.api.KpiViews;

import java.util.List;

public final class DashboardViews {
    private DashboardViews() {
    }

    public record Grid(int x, int y, int width, int height) {
    }

    public record Behavior(
            int refreshSeconds,
            String clickThrough,
            String styleVariant
    ) {
    }

    public record WidgetDraft(
            String code,
            String type,
            String title,
            String dataSourceId,
            String kpiId,
            Integer rowLimit,
            Statistic statistics,
            Behavior behavior,
            Grid grid
    ) {
        public WidgetDraft(
                String code, String type, String title, String dataSourceId,
                String kpiId, Integer rowLimit, Statistic statistics,
                Grid grid
        ) {
            this(code, type, title, dataSourceId, kpiId, rowLimit,
                    statistics, new Behavior(0, null, "STANDARD"), grid);
        }
    }

    public record Statistic(
            String aggregation,
            String measureFieldCode,
            Grouping grouping,
            Trend trend,
            StatisticField measureField,
            StatisticField groupField,
            StatisticField timeField
    ) {
    }

    public record StatisticField(
            String logicalFieldId,
            String code,
            String name,
            String type,
            String queryType
    ) {
    }

    public record Grouping(String fieldCode, int bucketLimit) {
    }

    public record Trend(
            String fieldCode,
            String grain,
            String startInclusive,
            String endExclusive
    ) {
    }

    public record Draft(List<WidgetDraft> widgets) {
        public Draft {
            widgets = List.copyOf(widgets);
        }
    }

    public record Dashboard(
            String id,
            String systemId,
            String tenantId,
            String code,
            String placement,
            String scopeKey,
            String name,
            String description,
            long draftVersion,
            String activeVersionId,
            Integer activeVersionNumber,
            String createdAt,
            String updatedAt,
            long version,
            Draft draft
    ) {
        public Dashboard(
                String id,
                String systemId,
                String tenantId,
                String code,
                String placement,
                String name,
                String description,
                long draftVersion,
                String activeVersionId,
                Integer activeVersionNumber,
                String createdAt,
                String updatedAt,
                long version,
                Draft draft
        ) {
            this(id, systemId, tenantId, code, placement, null, name,
                    description, draftVersion, activeVersionId,
                    activeVersionNumber, createdAt, updatedAt, version, draft);
        }
    }

    public record CheckIssue(
            String severity,
            String code,
            String path,
            String message
    ) {
    }

    public record CheckResult(
            String dashboardId,
            long checkedDraftVersion,
            boolean valid,
            long blockerCount,
            long warningCount,
            List<CheckIssue> issues
    ) {
        public CheckResult {
            issues = List.copyOf(issues);
        }
    }

    public record VersionWidget(
            String id,
            int ordinal,
            String code,
            String type,
            String title,
            String dataSourceId,
            String dataSourceCode,
            String dataSourceVersionId,
            Integer dataSourceVersionNumber,
            String moduleCode,
            String schemaVersionId,
            String kpiId,
            String kpiVersionId,
            Integer kpiVersionNumber,
            String kpiCode,
            String kpiName,
            String kpiSubjectType,
            String kpiPeriodType,
            Integer rowLimit,
            Statistic statistics,
            Behavior behavior,
            Grid grid
    ) {
        public VersionWidget(
                String id, int ordinal, String code, String type, String title,
                String dataSourceId, String dataSourceCode,
                String dataSourceVersionId, Integer dataSourceVersionNumber,
                String moduleCode, String schemaVersionId, String kpiId,
                String kpiVersionId, Integer kpiVersionNumber, String kpiCode,
                String kpiName, String kpiSubjectType, String kpiPeriodType,
                Integer rowLimit, Statistic statistics, Grid grid
        ) {
            this(id, ordinal, code, type, title, dataSourceId, dataSourceCode,
                    dataSourceVersionId, dataSourceVersionNumber, moduleCode,
                    schemaVersionId, kpiId, kpiVersionId, kpiVersionNumber,
                    kpiCode, kpiName, kpiSubjectType, kpiPeriodType, rowLimit,
                    statistics, new Behavior(0, null, "STANDARD"), grid);
        }
    }

    public record Version(
            String id,
            String dashboardId,
            int versionNumber,
            long sourceDraftVersion,
            String code,
            String placement,
            String name,
            String description,
            Draft snapshot,
            String fingerprint,
            int widgetCount,
            String publishedBy,
            String publishedAt,
            boolean active,
            List<VersionWidget> widgets
    ) {
        public Version {
            widgets = List.copyOf(widgets);
        }
    }

    public record PublishResult(Dashboard dashboard, Version version) {
    }

    public record RuntimeWidgetError(String code, String message) {
    }

    public record RuntimeWidget(
            String code,
            String type,
            String title,
            int ordinal,
            Grid grid,
            String dataSourceId,
            String dataSourceVersionId,
            Integer dataSourceVersionNumber,
            String dataSourceCode,
            String kpiId,
            String kpiVersionId,
            Integer kpiVersionNumber,
            String kpiCode,
            String kpiName,
            String kpiSubjectType,
            String kpiPeriodType,
            Integer rowLimit,
            String status,
            RuntimeWidgetError error,
            Long total,
            StatisticsViews.Result statisticsResult,
            List<DataSourceViews.RuntimeField> fields,
            List<DataSourceViews.RuntimeRow> rows,
            List<KpiViews.Target> kpiTargets,
            Behavior behavior
    ) {
        public RuntimeWidget {
            fields = List.copyOf(fields);
            rows = List.copyOf(rows);
            kpiTargets = List.copyOf(kpiTargets);
            behavior = behavior == null
                    ? new Behavior(0, null, "STANDARD") : behavior;
        }

        public RuntimeWidget(
                String code, String type, String title, int ordinal, Grid grid,
                String dataSourceId, String dataSourceVersionId,
                Integer dataSourceVersionNumber, String dataSourceCode,
                String kpiId, String kpiVersionId, Integer kpiVersionNumber,
                String kpiCode, String kpiName, String kpiSubjectType,
                String kpiPeriodType, Integer rowLimit, String status,
                RuntimeWidgetError error, Long total,
                StatisticsViews.Result statisticsResult,
                List<DataSourceViews.RuntimeField> fields,
                List<DataSourceViews.RuntimeRow> rows,
                List<KpiViews.Target> kpiTargets
        ) {
            this(code, type, title, ordinal, grid, dataSourceId,
                    dataSourceVersionId, dataSourceVersionNumber,
                    dataSourceCode, kpiId, kpiVersionId, kpiVersionNumber,
                    kpiCode, kpiName, kpiSubjectType, kpiPeriodType, rowLimit,
                    status, error, total, statisticsResult, fields, rows,
                    kpiTargets, new Behavior(0, null, "STANDARD"));
        }

        public RuntimeWidget(
                String code,
                String type,
                String title,
                int ordinal,
                Grid grid,
                String dataSourceId,
                String dataSourceVersionId,
                int dataSourceVersionNumber,
                String dataSourceCode,
                Integer rowLimit,
                String status,
                RuntimeWidgetError error,
                Long total,
                StatisticsViews.Result statisticsResult,
                List<DataSourceViews.RuntimeField> fields,
                List<DataSourceViews.RuntimeRow> rows
        ) {
            this(code, type, title, ordinal, grid, dataSourceId,
                    dataSourceVersionId, dataSourceVersionNumber,
                    dataSourceCode, null, null, null, null, null, null, null,
                    rowLimit, status, error, total,
                    statisticsResult, fields, rows, List.of(),
                    new Behavior(0, null, "STANDARD"));
        }
    }

    public record RuntimeDashboard(
            String id,
            String code,
            String name,
            String description,
            String placement,
            String versionId,
            int versionNumber,
            List<RuntimeWidget> widgets
    ) {
        public RuntimeDashboard {
            widgets = List.copyOf(widgets);
        }
    }
}
