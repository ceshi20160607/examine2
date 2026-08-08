package com.unique.examine.module.dashboard.service;

import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardCheckReport;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardStatisticsSnapshot;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.dashboard.domain.DashboardWidgetDraft;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.PublishedDashboard;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.port.DashboardRepository;
import com.unique.examine.module.dashboard.port.DashboardKpiCatalog;
import com.unique.examine.module.dashboard.port.DashboardSourceCatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.unique.examine.module.dashboard.domain.DashboardCheckReport.Severity.BLOCKER;

public final class DashboardService {
    private final DashboardRepository repository;
    private final DashboardSourceCatalog sources;
    private final DashboardKpiCatalog kpis;
    private final Clock clock;

    public DashboardService(
            DashboardRepository repository,
            DashboardSourceCatalog sources,
            DashboardKpiCatalog kpis,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.kpis = Objects.requireNonNull(kpis, "kpis");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Compatibility constructor for callers that only publish legacy widgets. */
    public DashboardService(
            DashboardRepository repository,
            DashboardSourceCatalog sources,
            Clock clock
    ) {
        this(repository, sources, (systemId, tenantId, kpiId) ->
                java.util.Optional.empty(), clock);
    }

    public SystemDashboard create(
            DashboardActor actor,
            String code,
            DashboardPlacement placement,
            String name,
            String description,
            DashboardDraft draft
    ) {
        requireActor(actor);
        if (placement == null) {
            throw error("DASHBOARD_PLACEMENT_INVALID",
                    "Dashboard placement is required");
        }
        if (placement == DashboardPlacement.SYSTEM_HOME
                && repository.findByPlacement(
                actor.systemId(), actor.tenantId(), placement).isPresent()) {
            throw error("DASHBOARD_PLACEMENT_CONFLICT",
                    "This tenant already has a SYSTEM_HOME dashboard");
        }
        var normalizedCode = SystemDashboard.code(code);
        if (repository.findByCode(
                actor.systemId(), actor.tenantId(), normalizedCode).isPresent()) {
            throw error("DASHBOARD_CODE_CONFLICT",
                    "Dashboard code already exists in this tenant");
        }
        var root = SystemDashboard.create(
                repository.nextDashboardId(), actor.systemId(),
                actor.tenantId(), normalizedCode, placement,
                name, description, draft, clock.instant());
        return repository.insert(root);
    }

    public List<SystemDashboard> list(DashboardActor actor) {
        requireActor(actor);
        return repository.findAll(actor.systemId(), actor.tenantId())
                .stream()
                .sorted(Comparator.comparing(
                                (SystemDashboard value) -> value.code())
                        .thenComparingLong(SystemDashboard::id))
                .toList();
    }

    public SystemDashboard detail(
            DashboardActor actor,
            long dashboardId
    ) {
        requireActor(actor);
        return root(actor, dashboardId);
    }

    public SystemDashboard detail(
            DashboardActor actor,
            DashboardPlacement placement
    ) {
        requireActor(actor);
        return repository.findByPlacement(
                        actor.systemId(), actor.tenantId(), placement)
                .orElseThrow(DashboardService::notFound);
    }

    public SystemDashboard revise(
            DashboardActor actor,
            long dashboardId,
            long expectedDraftVersion,
            String name,
            String description,
            DashboardDraft draft
    ) {
        requireActor(actor);
        var current = root(actor, dashboardId);
        requireDraftVersion(current, expectedDraftVersion);
        var revised = current.reviseDraft(
                name, description, draft, clock.instant());
        return repository.saveDraft(current, revised);
    }

    public SystemDashboard saveDraft(
            DashboardActor actor,
            long dashboardId,
            long expectedDraftVersion,
            String name,
            String description,
            DashboardDraft draft
    ) {
        return revise(actor, dashboardId, expectedDraftVersion,
                name, description, draft);
    }

    /** Read-only validation against each source's current active version. */
    public DashboardCheckReport check(
            DashboardActor actor,
            long dashboardId
    ) {
        requireActor(actor);
        return validate(actor, root(actor, dashboardId)).report();
    }

    public DashboardVersion publish(
            DashboardActor actor,
            long dashboardId,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, dashboardId);
        requireDraftVersion(current, expectedDraftVersion);
        var validation = validate(actor, current);
        if (!validation.report().publishable()) {
            throw error("DASHBOARD_CHECK_BLOCKED",
                    "Dashboard draft contains publish blockers");
        }

        var fingerprint = fingerprint(current, validation.widgets());
        var active = repository.findActiveVersion(
                actor.systemId(), actor.tenantId(), current.id());
        if (current.activeVersionId() != null && active.isEmpty()) {
            throw error("DASHBOARD_VERSION_NOT_FOUND",
                    "Active dashboard version does not exist");
        }
        if (active.isPresent()
                && active.get().fingerprint().equals(fingerprint)) {
            return active.get();
        }

        var versionId = repository.nextVersionId();
        var publishedWidgets = new ArrayList<DashboardVersionWidget>();
        for (int index = 0; index < validation.widgets().size(); index++) {
            var resolved = validation.widgets().get(index);
            var draft = resolved.draft();
            if (resolved.kpi() != null) {
                var kpi = resolved.kpi();
                publishedWidgets.add(DashboardVersionWidget.kpiValue(
                        repository.nextVersionWidgetId(), versionId,
                        current.id(), current.systemId(), current.tenantId(),
                        index, draft.code(), draft.title(), kpi.kpiId(),
                        kpi.versionId(), kpi.versionNumber(), kpi.code(),
                         kpi.name(), kpi.subjectType(), kpi.periodType(),
                         draft.behavior(), draft.grid()));
            } else {
                var source = resolved.source();
                publishedWidgets.add(new DashboardVersionWidget(
                        repository.nextVersionWidgetId(), versionId,
                        current.id(), current.systemId(), current.tenantId(),
                        index, draft.code(), draft.type(), draft.title(),
                        source.dataSourceId(), source.dataSourceCode(),
                        source.versionId(), source.versionNumber(),
                        source.moduleCode(), source.schemaVersionId(),
                        draft.rowLimit(), statisticsSnapshot(
                        draft.statistics(), source), null, null, null, null,
                         null, null, null, draft.behavior(), draft.grid()));
            }
        }
        var publishedAt = clock.instant();
        var version = new DashboardVersion(
                versionId, current.id(), current.systemId(),
                current.tenantId(),
                active.map(value -> value.versionNumber() + 1).orElse(1),
                current.draftVersion(),
                current.code(), current.placement(), current.name(),
                current.description(), publishedWidgets, fingerprint,
                actor.memberId(), publishedAt);
        var activated = current.activate(version, publishedAt);
        return repository.publish(current, activated, version);
    }

    public List<DashboardVersion> versions(
            DashboardActor actor,
            long dashboardId
    ) {
        requireActor(actor);
        root(actor, dashboardId);
        return repository.findVersions(
                        actor.systemId(), actor.tenantId(), dashboardId)
                .stream()
                .sorted(Comparator.comparingInt(
                        DashboardVersion::versionNumber).reversed())
                .toList();
    }

    public DashboardVersion version(
            DashboardActor actor,
            long dashboardId,
            int versionNumber
    ) {
        requireActor(actor);
        root(actor, dashboardId);
        return repository.findVersion(
                        actor.systemId(), actor.tenantId(), dashboardId,
                        versionNumber)
                .orElseThrow(() -> error(
                        "DASHBOARD_VERSION_NOT_FOUND",
                        "Dashboard version does not exist"));
    }

    /** Restores a publication as a new draft without changing runtime state. */
    public SystemDashboard restoreVersion(
            DashboardActor actor,
            long dashboardId,
            int versionNumber,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, dashboardId);
        requireDraftVersion(current, expectedDraftVersion);
        var source = version(actor, dashboardId, versionNumber);
        var restoredDraft = new DashboardDraft(source.widgets().stream()
                .map(DashboardService::draftWidget)
                .toList());
        return repository.saveDraft(current, current.reviseDraft(
                source.name(), source.description(), restoredDraft,
                clock.instant()));
    }

    private static DashboardWidgetDraft draftWidget(
            DashboardVersionWidget source
    ) {
        return new DashboardWidgetDraft(
                source.code(), source.type(), source.title(),
                source.dataSourceId(), source.kpiId(), source.rowLimit(),
                source.statistics() == null
                        ? null : source.statistics().request(),
                source.behavior(), source.grid());
    }

    public PublishedDashboard active(
            DashboardActor actor,
            String code
    ) {
        requireActor(actor);
        var root = repository.findByCode(
                        actor.systemId(), actor.tenantId(),
                        SystemDashboard.code(code))
                .orElseThrow(DashboardService::notFound);
        return active(actor, root);
    }

    public PublishedDashboard active(
            DashboardActor actor,
            long dashboardId
    ) {
        requireActor(actor);
        return active(actor, root(actor, dashboardId));
    }

    public PublishedDashboard activeHome(DashboardActor actor) {
        requireActor(actor);
        var root = repository.findByPlacement(
                        actor.systemId(), actor.tenantId(),
                        DashboardPlacement.SYSTEM_HOME)
                .orElseThrow(DashboardService::notFound);
        return active(actor, root);
    }

    private PublishedDashboard active(
            DashboardActor actor,
            SystemDashboard root
    ) {
        var version = repository.findActiveVersion(
                        actor.systemId(), actor.tenantId(), root.id())
                .orElseThrow(() -> error(
                        "DASHBOARD_UNPUBLISHED",
                        "Dashboard has no active published version"));
        return new PublishedDashboard(root, version);
    }

    private Validation validate(
            DashboardActor actor,
            SystemDashboard root
    ) {
        var issues = new ArrayList<DashboardCheckReport.Issue>();
        var resolvedWidgets = new ArrayList<ResolvedWidget>();
        var draftWidgets = root.draft().widgets();
        if (draftWidgets.isEmpty()) {
            blocker(issues, "WIDGETS_REQUIRED", "widgets",
                    "At least one dashboard widget is required");
        }

        Set<String> codes = new HashSet<>();
        for (int index = 0; index < draftWidgets.size(); index++) {
            var widget = draftWidgets.get(index);
            var path = "widgets[" + index + "]";
            if (!codes.add(widget.code())) {
                blocker(issues, "WIDGET_CODE_DUPLICATE", path + ".code",
                        "Widget code is used more than once");
            }
            validateRowLimit(widget, path, issues);
            if (!widget.grid().withinBounds()) {
                blocker(issues, "WIDGET_GRID_OUT_OF_BOUNDS", path + ".grid",
                        "Widget grid must fit within 12 columns and 100 rows");
            } else {
                for (int previous = 0; previous < index; previous++) {
                    var earlier = draftWidgets.get(previous);
                    if (earlier.grid().withinBounds()
                            && widget.grid().overlaps(earlier.grid())) {
                        blocker(issues, "WIDGET_GRID_OVERLAP", path + ".grid",
                                "Widget overlaps " + earlier.code());
                    }
                }
            }

            if (widget.type() == DashboardWidgetType.KPI_VALUE) {
                var kpi = resolveKpi(actor, widget, path, issues);
                if (kpi != null) {
                    resolvedWidgets.add(new ResolvedWidget(
                            widget, null, kpi));
                }
            } else {
                var source = resolveSource(actor, widget, path, issues);
                if (source != null) {
                    validateStatistics(widget, source, path, issues);
                    resolvedWidgets.add(new ResolvedWidget(
                            widget, source, null));
                }
            }
        }
        return new Validation(
                new DashboardCheckReport(
                        root.id(), root.draftVersion(), issues),
                List.copyOf(resolvedWidgets));
    }

    private DashboardKpiCatalog.ActiveKpiVersion resolveKpi(
            DashboardActor actor,
            DashboardWidgetDraft widget,
            String path,
            List<DashboardCheckReport.Issue> issues
    ) {
        var version = kpis.activeVersion(
                        actor.systemId(), actor.tenantId(), widget.kpiId())
                .filter(value -> value.systemId() == actor.systemId()
                        && value.tenantId() == actor.tenantId()
                        && value.kpiId() == widget.kpiId())
                .orElse(null);
        if (version == null) {
            blocker(issues, "WIDGET_KPI_UNAVAILABLE", path + ".kpiId",
                    "Widget KPI has no active version in this tenant");
        }
        return version;
    }

    private DashboardSourceCatalog.SourceVersion resolveSource(
            DashboardActor actor,
            DashboardWidgetDraft widget,
            String path,
            List<DashboardCheckReport.Issue> issues
    ) {
        var root = sources.source(
                        actor.systemId(), actor.tenantId(),
                        widget.dataSourceId())
                .filter(value -> value.systemId() == actor.systemId()
                        && value.tenantId() == actor.tenantId()
                        && value.dataSourceId() == widget.dataSourceId())
                .orElse(null);
        if (root == null) {
            blocker(issues, "WIDGET_SOURCE_NOT_FOUND",
                    path + ".dataSourceId",
                    "Widget data source does not exist in this tenant");
            return null;
        }
        if (root.activeVersionId() == null) {
            blocker(issues, "WIDGET_SOURCE_UNPUBLISHED",
                    path + ".dataSourceId",
                    "Widget data source has no active published version");
            return null;
        }
        var version = sources.version(
                        actor.systemId(), actor.tenantId(),
                        root.dataSourceId(), root.activeVersionId())
                .filter(value -> value.systemId() == actor.systemId()
                        && value.tenantId() == actor.tenantId()
                        && value.dataSourceId() == root.dataSourceId()
                        && value.versionId() == root.activeVersionId()
                        && value.versionNumber()
                        == root.activeVersionNumber())
                .orElse(null);
        if (version == null) {
            blocker(issues, "WIDGET_SOURCE_VERSION_NOT_FOUND",
                    path + ".dataSourceId",
                    "Active widget data-source version does not exist");
            return null;
        }
        if (!version.moduleAvailable()) {
            blocker(issues, "WIDGET_SOURCE_MODULE_UNAVAILABLE",
                    path + ".dataSourceId",
                    "Widget source module is unavailable");
        }
        if (version.readableOutputCount() == 0) {
            blocker(issues, "WIDGET_SOURCE_OUTPUT_UNREADABLE",
                    path + ".dataSourceId",
                    "Widget source has no readable published output");
        }
        return version;
    }

    private static void validateRowLimit(
            DashboardWidgetDraft widget,
            String path,
            List<DashboardCheckReport.Issue> issues
    ) {
        if (widget.type() == DashboardWidgetType.KPI_VALUE) {
            if (widget.rowLimit() != null || widget.statistics() != null) {
                blocker(issues, "KPI_VALUE_CONFIGURATION_FORBIDDEN",
                        path + ".kpiId",
                        "KPI_VALUE accepts only a KPI and layout settings");
            }
            return;
        }
        if (widget.type() == DashboardWidgetType.STAT_COUNT
                && (widget.rowLimit() != null
                || widget.statistics() != null)) {
            blocker(issues, "STAT_COUNT_ROW_LIMIT_FORBIDDEN",
                    path + ".statistics",
                    "STAT_COUNT widgets do not accept row or statistics settings");
        }
        if ((widget.type() == DashboardWidgetType.DATA_LIST
                || widget.type() == DashboardWidgetType.TODO_LIST
                || widget.type() == DashboardWidgetType.QUICK_ENTRY)
                && (widget.rowLimit() == null
                || widget.rowLimit() < 1 || widget.rowLimit() > 20
                || widget.statistics() != null)) {
            blocker(issues, "DATA_LIST_ROW_LIMIT_INVALID",
                    path + ".rowLimit",
                    "DATA_LIST requires a 1..20 row limit and no statistics");
        }
        if (widget.type() != DashboardWidgetType.STAT_COUNT
                && widget.type() != DashboardWidgetType.DATA_LIST
                && widget.type() != DashboardWidgetType.TODO_LIST
                && widget.type() != DashboardWidgetType.QUICK_ENTRY
                && (widget.rowLimit() != null
                || widget.statistics() == null)) {
            blocker(issues, "STATISTICS_CONFIGURATION_REQUIRED",
                    path + ".statistics",
                    "Statistics widgets require statistics and no row limit");
        }
        var statistics = widget.statistics();
        if (statistics == null) {
            return;
        }
        if ((widget.type() == DashboardWidgetType.STAT_VALUE
                || widget.type() == DashboardWidgetType.PROGRESS)
                && (statistics.grouping() != null
                || statistics.trend() != null)) {
            blocker(issues, "STAT_VALUE_SHAPE_INVALID",
                    path + ".statistics",
                    "STAT_VALUE accepts only a scalar aggregate");
        }
        if ((widget.type() == DashboardWidgetType.BAR_CHART
                || widget.type() == DashboardWidgetType.PIE_CHART
                || widget.type() == DashboardWidgetType.RANKING)
                && statistics.grouping() == null) {
            blocker(issues, "GROUPED_CHART_GROUP_REQUIRED",
                    path + ".statistics.grouping",
                    "BAR_CHART, PIE_CHART and RANKING require grouping");
        }
        if (widget.type() == DashboardWidgetType.LINE_TREND
                && statistics.trend() == null) {
            blocker(issues, "LINE_TREND_RANGE_REQUIRED",
                    path + ".statistics.trend",
                    "LINE_TREND requires a bounded trend range");
        }
    }

    private static void validateStatistics(
            DashboardWidgetDraft widget,
            DashboardSourceCatalog.SourceVersion source,
            String path,
            List<DashboardCheckReport.Issue> issues
    ) {
        var statistics = widget.statistics();
        if (statistics == null) {
            return;
        }
        if (statistics.measureFieldCode() != null) {
            var measure = source.fields().stream()
                    .filter(field -> field.code().equals(
                            statistics.measureFieldCode()))
                    .findFirst().orElse(null);
            if (measure == null || !measure.readable()) {
                blocker(issues, "STATISTICS_MEASURE_UNREADABLE",
                        path + ".statistics.measureFieldCode",
                        "Statistics measure must be a readable published output");
            } else if (!measure.numeric()) {
                blocker(issues, "STATISTICS_MEASURE_NOT_NUMERIC",
                        path + ".statistics.measureFieldCode",
                        "Statistics measure must be numeric");
            }
        }
        if (statistics.grouping() != null) {
            var group = source.fields().stream()
                    .filter(field -> field.code().equals(
                            statistics.grouping().fieldCode()))
                    .findFirst().orElse(null);
            if (group == null || !group.readable() || !group.groupable()) {
                blocker(issues, "STATISTICS_GROUP_UNAVAILABLE",
                        path + ".statistics.grouping.fieldCode",
                        "Statistics group must be a readable groupable output");
            }
        }
        if (statistics.trend() != null) {
            var time = source.fields().stream()
                    .filter(field -> field.code().equals(
                            statistics.trend().fieldCode()))
                    .findFirst().orElse(null);
            if (time == null || !time.readable() || !time.temporal()) {
                blocker(issues, "STATISTICS_TIME_UNAVAILABLE",
                        path + ".statistics.trend.fieldCode",
                        "Statistics trend must use a readable temporal output");
            }
        }
    }

    private static String fingerprint(
            SystemDashboard root,
            List<ResolvedWidget> widgets
    ) {
        var value = new StringBuilder("dashboard-publish-v1|");
        append(value, Long.toString(root.systemId()));
        append(value, Long.toString(root.tenantId()));
        append(value, root.code());
        append(value, root.placement().name());
        append(value, root.name());
        append(value, root.description());
        append(value, Integer.toString(widgets.size()));
        for (var resolved : widgets) {
            var widget = resolved.draft();
            append(value, widget.code());
            append(value, widget.type().name());
             append(value, widget.title());
            if (!widget.behavior().equals(
                    com.unique.examine.module.dashboard.domain
                            .DashboardWidgetBehavior.defaults())) {
                append(value, "widget-behavior-v1");
                append(value, Integer.toString(
                        widget.behavior().refreshSeconds()));
                append(value, widget.behavior().clickThrough());
                append(value, widget.behavior().styleVariant());
            }
            if (resolved.kpi() != null) {
                var kpi = resolved.kpi();
                append(value, "kpi-value-v1");
                append(value, Long.toString(kpi.kpiId()));
                append(value, Long.toString(kpi.versionId()));
                append(value, Integer.toString(kpi.versionNumber()));
                append(value, kpi.code());
                append(value, kpi.name());
                append(value, kpi.subjectType().name());
                append(value, kpi.periodType().name());
                append(value, Integer.toString(widget.grid().x()));
                append(value, Integer.toString(widget.grid().y()));
                append(value, Integer.toString(widget.grid().width()));
                append(value, Integer.toString(widget.grid().height()));
                continue;
            }
            var source = resolved.source();
            append(value, Long.toString(source.dataSourceId()));
            append(value, source.dataSourceCode());
            append(value, Long.toString(source.versionId()));
            append(value, Integer.toString(source.versionNumber()));
            append(value, source.moduleCode());
            append(value, source.schemaVersionId());
            append(value, widget.rowLimit() == null
                    ? null : widget.rowLimit().toString());
            var statistics = widget.statistics();
            // Preserve the exact V8.51 identity for legacy widgets so an
            // unchanged dashboard still replays after the V8.52 upgrade.
            if (statistics != null) {
                append(value, "statistics-v1");
                append(value, statistics.aggregation().name());
                append(value, statistics.measureFieldCode());
                append(value, statistics.grouping() == null
                        ? null : statistics.grouping().fieldCode());
                append(value, statistics.grouping() == null
                        ? null : Integer.toString(
                        statistics.grouping().bucketLimit()));
                append(value, statistics.trend() == null
                        ? null : statistics.trend().fieldCode());
                append(value, statistics.trend() == null
                        ? null : statistics.trend().grain().name());
                append(value, statistics.trend() == null
                        ? null : statistics.trend().startInclusive().toString());
                append(value, statistics.trend() == null
                        ? null : statistics.trend().endExclusive().toString());
                appendStatisticField(value, source, statistics.measureFieldCode());
                appendStatisticField(value, source,
                        statistics.grouping() == null
                                ? null : statistics.grouping().fieldCode());
                appendStatisticField(value, source,
                        statistics.trend() == null
                                ? null : statistics.trend().fieldCode());
            }
            append(value, Integer.toString(widget.grid().x()));
            append(value, Integer.toString(widget.grid().y()));
            append(value, Integer.toString(widget.grid().width()));
            append(value, Integer.toString(widget.grid().height()));
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                    value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static DashboardStatisticsSnapshot statisticsSnapshot(
            com.unique.examine.module.datasource.statistics.domain.StatisticsRequest
                    request,
            DashboardSourceCatalog.SourceVersion source
    ) {
        if (request == null) {
            return null;
        }
        return new DashboardStatisticsSnapshot(
                request,
                statisticField(source, request.measureFieldCode()),
                statisticField(source, request.grouping() == null
                        ? null : request.grouping().fieldCode()),
                statisticField(source, request.trend() == null
                        ? null : request.trend().fieldCode()));
    }

    private static DashboardStatisticsSnapshot.Field statisticField(
            DashboardSourceCatalog.SourceVersion source,
            String code
    ) {
        if (code == null) {
            return null;
        }
        var field = source.fields().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> error(
                        "DASHBOARD_CHECK_BLOCKED",
                        "Statistics field metadata is unavailable"));
        return new DashboardStatisticsSnapshot.Field(
                field.logicalFieldId(), field.code(), field.name(),
                field.type(), field.queryType());
    }

    private static void appendStatisticField(
            StringBuilder target,
            DashboardSourceCatalog.SourceVersion source,
            String code
    ) {
        var field = statisticField(source, code);
        if (field == null) {
            append(target, null);
            return;
        }
        append(target, Long.toString(field.logicalFieldId()));
        append(target, field.code());
        append(target, field.name());
        append(target, field.type());
        append(target, field.queryType());
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append('|');
    }

    private SystemDashboard root(
            DashboardActor actor,
            long dashboardId
    ) {
        if (dashboardId <= 0) {
            throw notFound();
        }
        return repository.findById(
                        actor.systemId(), actor.tenantId(), dashboardId)
                .orElseThrow(DashboardService::notFound);
    }

    private static void requireDraftVersion(
            SystemDashboard root,
            long expectedDraftVersion
    ) {
        if (expectedDraftVersion <= 0
                || root.draftVersion() != expectedDraftVersion) {
            throw error("DASHBOARD_VERSION_CONFLICT",
                    "Dashboard draft changed; refresh before retrying");
        }
    }

    private static void requireActor(DashboardActor actor) {
        Objects.requireNonNull(actor, "actor");
    }

    private static DashboardException notFound() {
        return error("DASHBOARD_NOT_FOUND", "Dashboard does not exist");
    }

    private static DashboardException error(String code, String message) {
        return new DashboardException(code, message);
    }

    private static void blocker(
            List<DashboardCheckReport.Issue> issues,
            String code,
            String path,
            String message
    ) {
        issues.add(new DashboardCheckReport.Issue(
                BLOCKER, code, path, message));
    }

    private record ResolvedWidget(
            DashboardWidgetDraft draft,
            DashboardSourceCatalog.SourceVersion source,
            DashboardKpiCatalog.ActiveKpiVersion kpi
    ) {
    }

    private record Validation(
            DashboardCheckReport report,
            List<ResolvedWidget> widgets
    ) {
    }
}
