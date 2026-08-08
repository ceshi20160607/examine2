package com.unique.examine.module.dashboard.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardGrid;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.dashboard.domain.DashboardStatisticsSnapshot;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.DashboardWidgetBehavior;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.port.DashboardRepository;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository("jdbcDashboardRepository")
public class JdbcDashboardRepository implements DashboardRepository {
    static final String ROOT_COLUMNS = """
            id,system_id,tenant_id,dashboard_code,placement,dashboard_name,
            description,draft_json,draft_version,active_version_id,
            active_version_no,created_at,updated_at,version
            """;
    static final String VERSION_COLUMNS = """
            id,dashboard_id,system_id,tenant_id,version_no,
            source_draft_version,dashboard_code,placement,dashboard_name,
            description,snapshot_fingerprint,widget_count,
            published_by_member_id,published_at
            """;
    static final String WIDGET_COLUMNS = """
            id,dashboard_version_id,dashboard_id,system_id,tenant_id,
            widget_ordinal,widget_code,widget_type,widget_title,data_source_id,
            data_source_code,data_source_version_id,data_source_version_no,
            module_code,schema_version_id,row_limit,stat_aggregation,
            stat_measure_field_code,stat_measure_field_id,
            stat_measure_field_name,stat_measure_field_type,
            stat_measure_query_type,stat_group_field_code,
            stat_group_field_id,stat_group_field_name,stat_group_field_type,
            stat_group_query_type,stat_group_limit,stat_time_field_code,
            stat_time_field_id,stat_time_field_name,stat_time_field_type,
            stat_time_query_type,stat_time_grain,stat_time_start,stat_time_end,
            kpi_id,kpi_version_id,kpi_version_no,kpi_code,kpi_name,
            kpi_subject_type,kpi_period_type,refresh_seconds,click_through,
            style_variant,
            grid_x,grid_y,grid_width,grid_height
            """;
    static final String INSERT_ROOT = """
            INSERT INTO un_module_dashboard (
                id,system_id,tenant_id,dashboard_code,placement,dashboard_name,
                description,draft_json,draft_version,active_version_id,
                active_version_no,created_at,updated_at,version
            ) VALUES (?,?,?,?,?,?,?,?,?,NULL,NULL,?,?,?)
            """;
    static final String SAVE_DRAFT_CAS = """
            UPDATE un_module_dashboard
               SET dashboard_name=?,description=?,draft_json=?,draft_version=?,
                   updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=?
               AND dashboard_code=? AND placement=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;
    static final String INSERT_VERSION = """
            INSERT INTO un_module_dashboard_version (
                id,system_id,tenant_id,dashboard_id,version_no,
                source_draft_version,dashboard_code,placement,dashboard_name,
                description,snapshot_json,snapshot_fingerprint,widget_count,
                published_by_member_id,published_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String INSERT_WIDGET = """
            INSERT INTO un_module_dashboard_version_widget (
                id,system_id,tenant_id,dashboard_id,dashboard_version_id,
                dashboard_version_no,widget_ordinal,widget_code,widget_type,
                widget_title,data_source_id,data_source_version_id,
                data_source_version_no,data_source_code,module_code,
                schema_version_id,row_limit,stat_aggregation,
                stat_measure_field_code,stat_measure_field_id,
                stat_measure_field_name,stat_measure_field_type,
                stat_measure_query_type,stat_group_field_code,
                stat_group_field_id,stat_group_field_name,
                stat_group_field_type,stat_group_query_type,stat_group_limit,
                stat_time_field_code,stat_time_field_id,stat_time_field_name,
                stat_time_field_type,stat_time_query_type,stat_time_grain,
                stat_time_start,stat_time_end,kpi_id,kpi_version_id,
                kpi_version_no,kpi_code,kpi_name,kpi_subject_type,
                kpi_period_type,refresh_seconds,click_through,style_variant,
                grid_x,grid_y,grid_width,grid_height
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String ACTIVATE_CAS = """
            UPDATE un_module_dashboard
               SET active_version_id=?,active_version_no=?,updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=?
               AND dashboard_code=? AND placement=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final TransactionTemplate transactions;

    public JdbcDashboardRepository(
            JdbcTemplate jdbc,
            ObjectMapper json,
            IdService ids,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
    }

    @Override
    public long nextDashboardId() {
        return ids.nextId();
    }

    @Override
    public long nextVersionId() {
        return ids.nextId();
    }

    @Override
    public long nextVersionWidgetId() {
        return ids.nextId();
    }

    @Override
    public Optional<SystemDashboard> findById(
            long systemId,
            long tenantId,
            long dashboardId
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_dashboard "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                this::root,
                systemId, tenantId, dashboardId));
    }

    @Override
    public Optional<SystemDashboard> findByCode(
            long systemId,
            long tenantId,
            String code
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_dashboard "
                        + "WHERE system_id=? AND tenant_id=? AND dashboard_code=?",
                this::root,
                systemId, tenantId, code));
    }

    @Override
    public Optional<SystemDashboard> findByPlacement(
            long systemId,
            long tenantId,
            DashboardPlacement placement
    ) {
        Objects.requireNonNull(placement, "placement");
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_dashboard "
                        + "WHERE system_id=? AND tenant_id=? AND placement=?",
                this::root,
                systemId, tenantId, placement.name()));
    }

    @Override
    public List<SystemDashboard> findAll(long systemId, long tenantId) {
        return jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_dashboard "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "ORDER BY updated_at DESC,id DESC",
                this::root,
                systemId, tenantId);
    }

    @Override
    public SystemDashboard insert(SystemDashboard root) {
        Objects.requireNonNull(root, "root");
        if (root.activeVersionId() != null
                || root.activeVersionNumber() != null) {
            throw invalid("A new dashboard cannot already be published");
        }
        try {
            requireOne(jdbc.update(
                            INSERT_ROOT,
                            root.id(), root.systemId(), root.tenantId(),
                            root.code(), root.placement().name(), root.name(),
                            root.description(), write(root.draft()),
                            root.draftVersion(), Timestamp.from(root.createdAt()),
                            Timestamp.from(root.updatedAt()), root.version()),
                    "Dashboard root insert did not affect one row");
            return root;
        } catch (DuplicateKeyException exception) {
            throw new DashboardException(
                    "DASHBOARD_CODE_CONFLICT",
                    "Dashboard code or placement already exists");
        }
    }

    @Override
    public SystemDashboard saveDraft(
            SystemDashboard expected,
            SystemDashboard revised
    ) {
        requireDraftRevision(expected, revised);
        var updated = jdbc.update(
                SAVE_DRAFT_CAS,
                revised.name(), revised.description(), write(revised.draft()),
                revised.draftVersion(), Timestamp.from(revised.updatedAt()),
                revised.version(), expected.systemId(), expected.tenantId(),
                expected.id(), expected.code(), expected.placement().name(),
                expected.draftVersion(), expected.version(),
                expected.activeVersionId(), expected.activeVersionNumber());
        if (updated != 1) {
            throw conflict("Dashboard draft changed before this save");
        }
        return revised;
    }

    @Override
    public DashboardVersion publish(
            SystemDashboard expected,
            SystemDashboard activated,
            DashboardVersion version
    ) {
        requirePublication(expected, activated, version);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                requireOne(jdbc.update(
                                INSERT_VERSION,
                                version.id(), version.systemId(),
                                version.tenantId(), version.dashboardId(),
                                version.versionNumber(),
                                version.sourceDraftVersion(),
                                version.code(), version.placement().name(),
                                version.name(), version.description(),
                                writeSnapshot(version), version.fingerprint(),
                                version.widgets().size(),
                                version.publishedByMemberId(),
                                Timestamp.from(version.publishedAt())),
                        "Dashboard version insert did not affect one row");
                insertWidgets(version);
                var updated = jdbc.update(
                        ACTIVATE_CAS,
                        activated.activeVersionId(),
                        activated.activeVersionNumber(),
                        Timestamp.from(activated.updatedAt()),
                        activated.version(), expected.systemId(),
                        expected.tenantId(), expected.id(), expected.code(),
                        expected.placement().name(), expected.draftVersion(),
                        expected.version(), expected.activeVersionId(),
                        expected.activeVersionNumber());
                if (updated != 1) {
                    throw conflict(
                            "Dashboard changed before publication completed");
                }
                return version;
            }));
        } catch (DuplicateKeyException exception) {
            throw conflict("Dashboard draft or version was already published");
        } catch (DataIntegrityViolationException exception) {
            throw invalid(
                    "Dashboard publication violated a scoped immutable reference");
        }
    }

    @Override
    public Optional<DashboardVersion> findActiveVersion(
            long systemId,
            long tenantId,
            long dashboardId
    ) {
        var row = one(jdbc.query(
                "SELECT " + prefixed(VERSION_COLUMNS, "version_row")
                        + " FROM un_module_dashboard root "
                        + "JOIN un_module_dashboard_version version_row "
                        + "ON version_row.system_id=root.system_id "
                        + "AND version_row.tenant_id=root.tenant_id "
                        + "AND version_row.dashboard_id=root.id "
                        + "AND version_row.id=root.active_version_id "
                        + "AND version_row.version_no=root.active_version_no "
                        + "WHERE root.system_id=? AND root.tenant_id=? "
                        + "AND root.id=?",
                JdbcDashboardRepository::versionRow,
                systemId, tenantId, dashboardId));
        return hydrate(row);
    }

    @Override
    public Optional<DashboardVersion> findVersion(
            long systemId,
            long tenantId,
            long dashboardId,
            int versionNumber
    ) {
        var row = one(jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_dashboard_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND dashboard_id=? AND version_no=?",
                JdbcDashboardRepository::versionRow,
                systemId, tenantId, dashboardId, versionNumber));
        return hydrate(row);
    }

    @Override
    public List<DashboardVersion> findVersions(
            long systemId,
            long tenantId,
            long dashboardId
    ) {
        var rows = jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_dashboard_version "
                        + "WHERE system_id=? AND tenant_id=? AND dashboard_id=? "
                        + "ORDER BY version_no DESC,id DESC",
                JdbcDashboardRepository::versionRow,
                systemId, tenantId, dashboardId);
        if (rows.isEmpty()) {
            return List.of();
        }
        var widgets = widgets(systemId, tenantId, dashboardId, null);
        Map<Long, List<DashboardVersionWidget>> byVersion =
                new LinkedHashMap<>();
        widgets.forEach(widget -> byVersion.computeIfAbsent(
                widget.dashboardVersionId(), ignored -> new ArrayList<>())
                .add(widget));
        return rows.stream()
                .map(row -> materialize(
                        row, byVersion.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private Optional<DashboardVersion> hydrate(Optional<VersionRow> row) {
        if (row.isEmpty()) {
            return Optional.empty();
        }
        var value = row.orElseThrow();
        return Optional.of(materialize(
                value, widgets(value.systemId(), value.tenantId(),
                        value.dashboardId(), value.id())));
    }

    private List<DashboardVersionWidget> widgets(
            long systemId,
            long tenantId,
            long dashboardId,
            Long versionId
    ) {
        var filter = versionId == null
                ? "" : " AND dashboard_version_id=?";
        var arguments = versionId == null
                ? new Object[]{systemId, tenantId, dashboardId}
                : new Object[]{systemId, tenantId, dashboardId, versionId};
        return jdbc.query(
                "SELECT " + WIDGET_COLUMNS
                        + " FROM un_module_dashboard_version_widget "
                        + "WHERE system_id=? AND tenant_id=? AND dashboard_id=?"
                        + filter
                        + " ORDER BY dashboard_version_no DESC,widget_ordinal ASC,id ASC",
                JdbcDashboardRepository::widget,
                arguments);
    }

    private void insertWidgets(DashboardVersion version) {
        var arguments = version.widgets().stream()
                .map(widget -> new Object[]{
                        widget.id(), widget.systemId(), widget.tenantId(),
                        widget.dashboardId(), widget.dashboardVersionId(),
                        version.versionNumber(), widget.ordinal(), widget.code(),
                        widget.type().name(), widget.title(),
                        widget.dataSourceId(), widget.dataSourceVersionId(),
                        widget.dataSourceVersionNumber(), widget.dataSourceCode(),
                        widget.moduleCode(), widget.schemaVersionId(),
                        widget.rowLimit(), aggregation(widget),
                        fieldCode(measure(widget)), fieldId(measure(widget)),
                        fieldName(measure(widget)), fieldType(measure(widget)),
                        fieldQueryType(measure(widget)),
                        fieldCode(group(widget)), fieldId(group(widget)),
                        fieldName(group(widget)), fieldType(group(widget)),
                        fieldQueryType(group(widget)), groupLimit(widget),
                        fieldCode(time(widget)), fieldId(time(widget)),
                        fieldName(time(widget)), fieldType(time(widget)),
                        fieldQueryType(time(widget)), grain(widget),
                        timeStart(widget), timeEnd(widget),
                        widget.kpiId(), widget.kpiVersionId(),
                        widget.kpiVersionNumber(), widget.kpiCode(),
                        widget.kpiName(), widget.kpiSubjectType() == null
                                ? null : widget.kpiSubjectType().name(),
                        widget.kpiPeriodType() == null
                                ? null : widget.kpiPeriodType().name(),
                        widget.behavior().refreshSeconds(),
                        widget.behavior().clickThrough(),
                        widget.behavior().styleVariant(),
                        widget.grid().x(), widget.grid().y(),
                        widget.grid().width(), widget.grid().height()})
                .toList();
        var affected = jdbc.batchUpdate(INSERT_WIDGET, arguments);
        if (affected.length != arguments.size()) {
            throw new IllegalStateException(
                    "Dashboard widget insert count is incomplete");
        }
        for (var count : affected) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException(
                        "Dashboard widget insert did not affect one row");
            }
        }
    }

    private SystemDashboard root(ResultSet result, int rowNumber)
            throws SQLException {
        return new SystemDashboard(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getString("dashboard_code"),
                DashboardPlacement.valueOf(result.getString("placement")),
                result.getString("dashboard_name"),
                result.getString("description"),
                readDraft(result.getString("draft_json")),
                result.getLong("draft_version"),
                result.getObject("active_version_id", Long.class),
                result.getObject("active_version_no", Integer.class),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version"));
    }

    private static VersionRow versionRow(ResultSet result, int rowNumber)
            throws SQLException {
        return new VersionRow(
                result.getLong("id"), result.getLong("dashboard_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getInt("version_no"),
                result.getLong("source_draft_version"),
                result.getString("dashboard_code"),
                DashboardPlacement.valueOf(result.getString("placement")),
                result.getString("dashboard_name"),
                result.getString("description"),
                result.getString("snapshot_fingerprint"),
                result.getInt("widget_count"),
                result.getLong("published_by_member_id"),
                result.getTimestamp("published_at").toInstant());
    }

    private static DashboardVersionWidget widget(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new DashboardVersionWidget(
                result.getLong("id"),
                result.getLong("dashboard_version_id"),
                result.getLong("dashboard_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getInt("widget_ordinal"),
                result.getString("widget_code"),
                DashboardWidgetType.valueOf(result.getString("widget_type")),
                result.getString("widget_title"),
                result.getObject("data_source_id", Long.class),
                result.getString("data_source_code"),
                result.getObject("data_source_version_id", Long.class),
                result.getObject("data_source_version_no", Integer.class),
                result.getString("module_code"),
                result.getString("schema_version_id"),
                result.getObject("row_limit", Integer.class),
                statistics(result),
                result.getObject("kpi_id", Long.class),
                result.getObject("kpi_version_id", Long.class),
                result.getObject("kpi_version_no", Integer.class),
                result.getString("kpi_code"), result.getString("kpi_name"),
                result.getString("kpi_subject_type") == null ? null
                        : com.unique.examine.module.kpi.domain.KpiSubjectType
                        .valueOf(result.getString("kpi_subject_type")),
                result.getString("kpi_period_type") == null ? null
                        : com.unique.examine.module.kpi.domain.KpiPeriodType
                        .valueOf(result.getString("kpi_period_type")),
                new DashboardWidgetBehavior(
                        result.getInt("refresh_seconds"),
                        result.getString("click_through"),
                        result.getString("style_variant")),
                new DashboardGrid(
                        result.getInt("grid_x"), result.getInt("grid_y"),
                        result.getInt("grid_width"),
                        result.getInt("grid_height")));
    }

    private static DashboardStatisticsSnapshot statistics(ResultSet result)
            throws SQLException {
        var aggregation = result.getString("stat_aggregation");
        if (aggregation == null) {
            return null;
        }
        StatisticsRequest.Grouping grouping = null;
        var groupField = result.getString("stat_group_field_code");
        if (groupField != null) {
            grouping = new StatisticsRequest.Grouping(
                    groupField, result.getInt("stat_group_limit"));
        }
        StatisticsRequest.Trend trend = null;
        var timeField = result.getString("stat_time_field_code");
        if (timeField != null) {
            trend = new StatisticsRequest.Trend(
                    timeField,
                    StatisticsGrain.valueOf(
                            result.getString("stat_time_grain")),
                    result.getObject("stat_time_start", LocalDate.class),
                    result.getObject("stat_time_end", LocalDate.class));
        }
        var request = new StatisticsRequest(
                StatisticsAggregation.valueOf(aggregation),
                result.getString("stat_measure_field_code"),
                grouping, trend);
        return new DashboardStatisticsSnapshot(
                request, field(result, "measure"), field(result, "group"),
                field(result, "time"));
    }

    private static DashboardStatisticsSnapshot.Field field(
            ResultSet result,
            String role
    ) throws SQLException {
        var code = result.getString("stat_" + role + "_field_code");
        if (code == null) {
            return null;
        }
        return new DashboardStatisticsSnapshot.Field(
                result.getLong("stat_" + role + "_field_id"), code,
                result.getString("stat_" + role + "_field_name"),
                result.getString("stat_" + role + "_field_type"),
                result.getString("stat_" + role + "_query_type"));
    }

    private static String aggregation(DashboardVersionWidget widget) {
        return widget.statistics() == null
                ? null : widget.statistics().request().aggregation().name();
    }

    private static DashboardStatisticsSnapshot.Field measure(
            DashboardVersionWidget widget
    ) {
        return widget.statistics() == null ? null : widget.statistics().measure();
    }

    private static DashboardStatisticsSnapshot.Field group(
            DashboardVersionWidget widget
    ) {
        return widget.statistics() == null ? null : widget.statistics().group();
    }

    private static DashboardStatisticsSnapshot.Field time(
            DashboardVersionWidget widget
    ) {
        return widget.statistics() == null ? null : widget.statistics().time();
    }

    private static String fieldCode(DashboardStatisticsSnapshot.Field field) {
        return field == null ? null : field.code();
    }

    private static Long fieldId(DashboardStatisticsSnapshot.Field field) {
        return field == null ? null : field.logicalFieldId();
    }

    private static String fieldName(DashboardStatisticsSnapshot.Field field) {
        return field == null ? null : field.name();
    }

    private static String fieldType(DashboardStatisticsSnapshot.Field field) {
        return field == null ? null : field.type();
    }

    private static String fieldQueryType(
            DashboardStatisticsSnapshot.Field field
    ) {
        return field == null ? null : field.queryType();
    }

    private static Integer groupLimit(DashboardVersionWidget widget) {
        return widget.statistics() == null
                || widget.statistics().request().grouping() == null
                ? null : widget.statistics().request().grouping().bucketLimit();
    }

    private static String grain(DashboardVersionWidget widget) {
        return widget.statistics() == null
                || widget.statistics().request().trend() == null
                ? null : widget.statistics().request().trend().grain().name();
    }

    private static LocalDate timeStart(DashboardVersionWidget widget) {
        return widget.statistics() == null
                || widget.statistics().request().trend() == null
                ? null : widget.statistics().request().trend().startInclusive();
    }

    private static LocalDate timeEnd(DashboardVersionWidget widget) {
        return widget.statistics() == null
                || widget.statistics().request().trend() == null
                ? null : widget.statistics().request().trend().endExclusive();
    }

    private DashboardVersion materialize(
            VersionRow row,
            List<DashboardVersionWidget> widgets
    ) {
        if (widgets.size() != row.widgetCount()) {
            throw new IllegalStateException(
                    "Stored dashboard widget count is inconsistent");
        }
        return new DashboardVersion(
                row.id(), row.dashboardId(), row.systemId(), row.tenantId(),
                row.versionNumber(), row.sourceDraftVersion(), row.code(),
                row.placement(), row.name(), row.description(), widgets,
                row.fingerprint(),
                row.publishedByMemberId(), row.publishedAt());
    }

    private String write(DashboardDraft value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("Dashboard draft is not JSON serializable");
        }
    }

    private String writeSnapshot(DashboardVersion value) {
        try {
            return json.writeValueAsString(
                    new DashboardSnapshot(value.widgets()));
        } catch (JsonProcessingException exception) {
            throw invalid("Dashboard version is not JSON serializable");
        }
    }

    private DashboardDraft readDraft(String value) {
        try {
            return json.readValue(value, DashboardDraft.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored dashboard draft JSON is invalid", exception);
        }
    }

    private static void requireDraftRevision(
            SystemDashboard expected,
            SystemDashboard revised
    ) {
        requireSameRoot(expected, revised);
        if (revised.draftVersion() != expected.draftVersion() + 1
                || revised.version() != expected.version() + 1
                || !Objects.equals(
                revised.activeVersionId(), expected.activeVersionId())
                || !Objects.equals(revised.activeVersionNumber(),
                expected.activeVersionNumber())
                || !revised.createdAt().equals(expected.createdAt())) {
            throw invalid("Dashboard draft CAS state is invalid");
        }
    }

    private static void requirePublication(
            SystemDashboard expected,
            SystemDashboard activated,
            DashboardVersion version
    ) {
        requireSameRoot(expected, activated);
        if (version == null || version.dashboardId() != expected.id()
                || version.systemId() != expected.systemId()
                || version.tenantId() != expected.tenantId()
                || version.sourceDraftVersion() != expected.draftVersion()
                || !version.code().equals(expected.code())
                || version.placement() != expected.placement()
                || !version.name().equals(expected.name())
                || !Objects.equals(version.description(), expected.description())
                || !Objects.equals(activated.activeVersionId(), version.id())
                || !Objects.equals(activated.activeVersionNumber(),
                version.versionNumber())
                || activated.draftVersion() != expected.draftVersion()
                || activated.version() != expected.version() + 1
                || !activated.draft().equals(expected.draft())
                || !activated.name().equals(expected.name())
                || !Objects.equals(
                activated.description(), expected.description())
                || !activated.createdAt().equals(expected.createdAt())
                || !activated.updatedAt().equals(version.publishedAt())) {
            throw invalid("Dashboard publication CAS state is invalid");
        }
    }

    private static void requireSameRoot(
            SystemDashboard expected,
            SystemDashboard next
    ) {
        if (expected == null || next == null
                || expected.id() != next.id()
                || expected.systemId() != next.systemId()
                || expected.tenantId() != next.tenantId()
                || expected.placement() != next.placement()
                || !expected.code().equals(next.code())) {
            throw invalid("Dashboard immutable identity changed");
        }
    }

    private static String prefixed(String columns, String alias) {
        return columns.replaceAll(
                "(?m)(^|,)\\s*([a-z_]+)", "$1" + alias + ".$2");
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped dashboard lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }

    private static void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static DashboardException invalid(String message) {
        return new DashboardException("DASHBOARD_INVALID", message);
    }

    private static DashboardException conflict(String message) {
        return new DashboardException("DASHBOARD_VERSION_CONFLICT", message);
    }

    private record DashboardSnapshot(
            List<DashboardVersionWidget> widgets
    ) {
        private DashboardSnapshot {
            widgets = List.copyOf(widgets);
        }
    }

    private record VersionRow(
            long id,
            long dashboardId,
            long systemId,
            long tenantId,
            int versionNumber,
            long sourceDraftVersion,
            String code,
            DashboardPlacement placement,
            String name,
            String description,
            String fingerprint,
            int widgetCount,
            long publishedByMemberId,
            java.time.Instant publishedAt
    ) {
    }
}
