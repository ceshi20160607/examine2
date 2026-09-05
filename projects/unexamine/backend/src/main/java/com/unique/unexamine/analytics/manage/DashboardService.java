package com.unique.unexamine.analytics.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.analytics.base.entity.AnaDashboard;
import com.unique.unexamine.analytics.base.entity.AnaDashboardComponent;
import com.unique.unexamine.analytics.base.entity.AnaDashboardPublication;
import com.unique.unexamine.analytics.base.entity.AnaDashboardVersion;
import com.unique.unexamine.analytics.base.entity.AnaDataSource;
import com.unique.unexamine.analytics.base.entity.AnaDataSourceVersion;
import com.unique.unexamine.analytics.base.service.AnaDashboardBaseService;
import com.unique.unexamine.analytics.base.service.AnaDashboardComponentBaseService;
import com.unique.unexamine.analytics.base.service.AnaDashboardPublicationBaseService;
import com.unique.unexamine.analytics.base.service.AnaDashboardVersionBaseService;
import com.unique.unexamine.analytics.base.service.AnaDataSourceBaseService;
import com.unique.unexamine.analytics.base.service.AnaDataSourceVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.notification.manage.MessageModels;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.notification.manage.TodoModels;
import com.unique.unexamine.notification.manage.TodoService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.manage.AccessibleSystem;
import com.unique.unexamine.system.manage.SystemEntryService;
import com.unique.unexamine.work.manage.WorkManagementModels;
import com.unique.unexamine.work.manage.WorkManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DashboardService {
    private static final Pattern MODULE_CODE = Pattern.compile("[a-z][a-z0-9_-]{1,99}");
    private static final Set<String> OPEN_SOURCE_TYPES = Set.of(
            "PLATFORM_SYSTEMS", "MODULE_RECORDS", "MODULE_REPORT", "EXTERNAL_API", "DATABASE_CONNECTION",
            "TODO_ITEMS", "MESSAGE_ITEMS", "WORK_PROJECTS");
    private static final Pattern CONNECTION_REFERENCE = Pattern.compile("[a-z][a-z0-9_-]{1,99}");
    private static final Pattern APPROVED_OPERATION = Pattern.compile("[A-Z][A-Z0-9_]{1,99}");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() { };

    private final AnaDataSourceBaseService sourceService;
    private final AnaDataSourceVersionBaseService sourceVersionService;
    private final AnaDashboardBaseService dashboardService;
    private final AnaDashboardComponentBaseService componentService;
    private final AnaDashboardVersionBaseService dashboardVersionService;
    private final AnaDashboardPublicationBaseService publicationService;
    private final RuntimeDataService runtimeDataService;
    private final SystemEntryService systemEntryService;
    private final TodoService todoService;
    private final MessageService messageService;
    private final WorkManagementService workManagementService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final ReportService reportService;
    private final KpiService kpiService;

    public DashboardService(
            AnaDataSourceBaseService sourceService,
            AnaDataSourceVersionBaseService sourceVersionService,
            AnaDashboardBaseService dashboardService,
            AnaDashboardComponentBaseService componentService,
            AnaDashboardVersionBaseService dashboardVersionService,
            AnaDashboardPublicationBaseService publicationService,
            RuntimeDataService runtimeDataService,
            SystemEntryService systemEntryService,
            TodoService todoService,
            MessageService messageService,
            WorkManagementService workManagementService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            ReportService reportService,
            KpiService kpiService) {
        this.sourceService = sourceService;
        this.sourceVersionService = sourceVersionService;
        this.dashboardService = dashboardService;
        this.componentService = componentService;
        this.dashboardVersionService = dashboardVersionService;
        this.publicationService = publicationService;
        this.runtimeDataService = runtimeDataService;
        this.systemEntryService = systemEntryService;
        this.todoService = todoService;
        this.messageService = messageService;
        this.workManagementService = workManagementService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.reportService = reportService;
        this.kpiService = kpiService;
    }

    public ReportModels.Metadata reportMetadata(AuthenticatedContext context, String traceId) {
        return reportService.metadata(context, traceId);
    }

    public ReportModels.Preview previewReportSource(
            AuthenticatedContext context, Long sourceId, String traceId) {
        return reportService.previewDraft(context, sourceId, traceId);
    }

    public ReportModels.Result executeReportSource(
            AuthenticatedContext context, Long sourceId, String traceId) {
        return reportService.executePublished(context, sourceId, traceId);
    }

    @Transactional(readOnly = true)
    public DashboardModels.AdminOverview overview(AuthenticatedContext context) {
        requireAdmin(context);
        List<DashboardModels.DataSourceView> sources = ownedSources(context).stream()
                .sorted(Comparator.comparing(AnaDataSource::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::sourceView).toList();
        List<DashboardModels.DashboardView> dashboards = ownedDashboards(context).stream()
                .sorted(Comparator.comparing(AnaDashboard::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::dashboardView).toList();
        return new DashboardModels.AdminOverview(contextType(context), sources, dashboards);
    }

    @Transactional
    public DashboardModels.DataSourceView createSource(
            AuthenticatedContext context, DashboardModels.DataSourceRequest input, String traceId) {
        requireAdmin(context);
        Map<String, Object> definition = validateSource(context, input.sourceType(), input.definition(), traceId);
        if (findSource(context, input.code()) != null) {
            throw conflict("DASHBOARD_DATA_SOURCE_CODE_EXISTS", "当前上下文已存在同编码数据源");
        }
        LocalDateTime now = LocalDateTime.now();
        AnaDataSource source = new AnaDataSource();
        bindContext(source, context);
        source.setCode(input.code());
        source.setName(input.name().strip());
        source.setSourceType(input.sourceType());
        source.setDraftRevision(1);
        source.setDefinitionJson(writeJson(definition));
        source.setPermissionPolicyJson(writeJson(input.permissionPolicy()));
        source.setStatus("DRAFT");
        source.setCreatedByAccountId(context.accountId());
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        source.setVersion(0);
        sourceService.insert(source);
        audit(context, traceId, "DASHBOARD_DATA_SOURCE_CREATED", "ANA_DATA_SOURCE", source.getId(),
                Map.of("code", source.getCode(), "sourceType", source.getSourceType(), "draftRevision", 1));
        return sourceView(source);
    }

    @Transactional
    public DashboardModels.DataSourceView saveSource(
            AuthenticatedContext context, Long sourceId, DashboardModels.DataSourceRequest input, String traceId) {
        requireAdmin(context);
        Map<String, Object> definition = validateSource(context, input.sourceType(), input.definition(), traceId);
        AnaDataSource source = requireSource(context, sourceId);
        requireVersion(source.getVersion(), input.expectedVersion(), "DASHBOARD_DATA_SOURCE_VERSION_CONFLICT");
        AnaDataSource duplicate = findSource(context, input.code());
        if (duplicate != null && !duplicate.getId().equals(sourceId)) {
            throw conflict("DASHBOARD_DATA_SOURCE_CODE_EXISTS", "当前上下文已存在同编码数据源");
        }
        source.setCode(input.code());
        source.setName(input.name().strip());
        source.setSourceType(input.sourceType());
        source.setDefinitionJson(writeJson(definition));
        source.setPermissionPolicyJson(writeJson(input.permissionPolicy()));
        source.setDraftRevision(source.getDraftRevision() + 1);
        source.setStatus("DRAFT");
        source.setUpdatedAt(LocalDateTime.now());
        if (sourceService.updateById(source) != 1) {
            throw conflict("DASHBOARD_DATA_SOURCE_VERSION_CONFLICT", "数据源已被其他用户修改，请刷新后重试");
        }
        audit(context, traceId, "DASHBOARD_DATA_SOURCE_DRAFT_SAVED", "ANA_DATA_SOURCE", source.getId(),
                Map.of("draftRevision", source.getDraftRevision(), "sourceType", source.getSourceType()));
        return sourceView(sourceService.selectById(sourceId));
    }

    @Transactional
    public DashboardModels.DataSourceView publishSource(
            AuthenticatedContext context, Long sourceId, DashboardModels.PublishRequest input, String traceId) {
        requireAdmin(context);
        AnaDataSource source = requireSource(context, sourceId);
        if (!Objects.equals(source.getDraftRevision(), input.expectedDraftRevision())) {
            throw conflict("DASHBOARD_DATA_SOURCE_REVISION_CONFLICT", "数据源草稿修订号已变化，请重新预览");
        }
        Map<String, Object> definition = validateSource(context, source.getSourceType(),
                readMap(source.getDefinitionJson()), traceId);
        if ("MODULE_REPORT".equals(source.getSourceType())) {
            reportService.assertPublishable(context, definition, traceId);
        }
        AnaDataSourceVersion latest = latestSourceVersion(sourceId);
        int versionNumber = latest == null ? 1 : latest.getVersionNumber() + 1;
        LinkedHashMap<String, Object> snapshot = sourceSnapshot(source, null);
        snapshot.put("versionNumber", versionNumber);
        snapshot.put("publishedAt", LocalDateTime.now());
        String json = writeJson(snapshot);
        AnaDataSourceVersion version = new AnaDataSourceVersion();
        version.setDataSourceId(sourceId);
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(source.getDraftRevision());
        version.setDefinitionHash(sha256(json));
        version.setSnapshotJson(json);
        version.setPublishedByAccountId(context.accountId());
        version.setPublishedAt(LocalDateTime.now());
        sourceVersionService.insert(version);
        source.setStatus("PUBLISHED");
        source.setUpdatedAt(LocalDateTime.now());
        sourceService.updateById(source);
        audit(context, traceId, "DASHBOARD_DATA_SOURCE_PUBLISHED", "ANA_DATA_SOURCE_VERSION", version.getId(),
                Map.of("dataSourceId", sourceId, "versionNumber", versionNumber,
                        "draftRevision", source.getDraftRevision(), "definitionHash", version.getDefinitionHash()));
        return sourceView(sourceService.selectById(sourceId));
    }

    @Transactional
    public DashboardModels.DashboardView createDashboard(
            AuthenticatedContext context, DashboardModels.DashboardRequest input, String traceId) {
        requireAdmin(context);
        validateComponents(input.components());
        if (findDashboard(context, input.code()) != null) {
            throw conflict("DASHBOARD_CODE_EXISTS", "当前上下文已存在同编码仪表盘");
        }
        LocalDateTime now = LocalDateTime.now();
        AnaDashboard dashboard = new AnaDashboard();
        bindContext(dashboard, context);
        dashboard.setCode(input.code());
        dashboard.setName(input.name().strip());
        dashboard.setDescription(blankToNull(input.description()));
        dashboard.setDraftRevision(1);
        dashboard.setStatus("DRAFT");
        dashboard.setCreatedByAccountId(context.accountId());
        dashboard.setCreatedAt(now);
        dashboard.setUpdatedAt(now);
        dashboard.setVersion(0);
        dashboardService.insert(dashboard);
        replaceComponents(context, dashboard, input.components());
        audit(context, traceId, "DASHBOARD_DRAFT_CREATED", "ANA_DASHBOARD", dashboard.getId(),
                Map.of("code", dashboard.getCode(), "componentCount", input.components().size(),
                        "draftRevision", 1));
        return dashboardView(dashboardService.selectById(dashboard.getId()));
    }

    @Transactional
    public DashboardModels.DashboardView saveDashboard(
            AuthenticatedContext context, Long dashboardId, DashboardModels.DashboardRequest input,
            String traceId) {
        requireAdmin(context);
        validateComponents(input.components());
        AnaDashboard dashboard = requireDashboard(context, dashboardId);
        requireVersion(dashboard.getVersion(), input.expectedVersion(), "DASHBOARD_VERSION_CONFLICT");
        AnaDashboard duplicate = findDashboard(context, input.code());
        if (duplicate != null && !duplicate.getId().equals(dashboardId)) {
            throw conflict("DASHBOARD_CODE_EXISTS", "当前上下文已存在同编码仪表盘");
        }
        dashboard.setCode(input.code());
        dashboard.setName(input.name().strip());
        dashboard.setDescription(blankToNull(input.description()));
        dashboard.setDraftRevision(dashboard.getDraftRevision() + 1);
        dashboard.setStatus("DRAFT");
        dashboard.setUpdatedAt(LocalDateTime.now());
        if (dashboardService.updateById(dashboard) != 1) {
            throw conflict("DASHBOARD_VERSION_CONFLICT", "仪表盘已被其他用户修改，请刷新后重试");
        }
        replaceComponents(context, dashboard, input.components());
        audit(context, traceId, "DASHBOARD_DRAFT_SAVED", "ANA_DASHBOARD", dashboardId,
                Map.of("componentCount", input.components().size(),
                        "draftRevision", dashboard.getDraftRevision()));
        return dashboardView(dashboardService.selectById(dashboardId));
    }

    public DashboardModels.Preview preview(AuthenticatedContext context, Long dashboardId, String traceId) {
        requireAdmin(context);
        AnaDashboard dashboard = requireDashboard(context, dashboardId);
        List<Map<String, Object>> components = components(dashboardId).stream()
                .map(component -> componentSnapshot(context, component)).toList();
        return previewSnapshot(context, dashboardId, dashboard.getDraftRevision(), components, traceId);
    }

    @Transactional
    public DashboardModels.PublishResult publishDashboard(
            AuthenticatedContext context, Long dashboardId, DashboardModels.PublishRequest input,
            String traceId) {
        requireAdmin(context);
        AnaDashboard dashboard = requireDashboard(context, dashboardId);
        if (!Objects.equals(dashboard.getDraftRevision(), input.expectedDraftRevision())) {
            throw conflict("DASHBOARD_REVISION_CONFLICT", "仪表盘草稿修订号已变化，请重新预览");
        }
        List<Map<String, Object>> componentSnapshots = components(dashboardId).stream()
                .map(component -> componentSnapshot(context, component)).toList();
        DashboardModels.Preview preview = previewSnapshot(
                context, dashboardId, dashboard.getDraftRevision(), componentSnapshots, traceId);
        if (!preview.valid()) {
            throw new DomainException("DASHBOARD_PUBLICATION_INVALID", "仪表盘预览存在无效组件，不能发布",
                    HttpStatus.UNPROCESSABLE_ENTITY, Map.of("issues", preview.issues()));
        }
        AnaDashboardVersion latest = latestDashboardVersion(dashboardId);
        int versionNumber = latest == null ? 1 : latest.getVersionNumber() + 1;
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("dashboardId", dashboardId);
        snapshot.put("contextType", dashboard.getContextType());
        snapshot.put("platformId", dashboard.getPlatformId());
        snapshot.put("systemId", dashboard.getSystemId());
        snapshot.put("ownerTenantId", dashboard.getOwnerTenantId());
        snapshot.put("code", dashboard.getCode());
        snapshot.put("name", dashboard.getName());
        snapshot.put("description", dashboard.getDescription());
        snapshot.put("draftRevision", dashboard.getDraftRevision());
        snapshot.put("versionNumber", versionNumber);
        snapshot.put("components", componentSnapshots);
        String snapshotJson = writeJson(snapshot);
        AnaDashboardVersion version = new AnaDashboardVersion();
        version.setDashboardId(dashboardId);
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(dashboard.getDraftRevision());
        version.setSnapshotHash(sha256(snapshotJson));
        version.setSnapshotJson(snapshotJson);
        version.setPublishedByAccountId(context.accountId());
        version.setPublishedAt(LocalDateTime.now());
        dashboardVersionService.insert(version);

        AnaDashboardPublication publication = publication(dashboardId);
        if (publication == null) {
            publication = new AnaDashboardPublication();
            publication.setDashboardId(dashboardId);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setUpdatedAt(LocalDateTime.now());
            publication.setVersion(0);
            publicationService.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setUpdatedAt(LocalDateTime.now());
            publicationService.updateById(publication);
        }
        dashboard.setStatus("PUBLISHED");
        dashboard.setUpdatedAt(LocalDateTime.now());
        dashboardService.updateById(dashboard);
        audit(context, traceId, "DASHBOARD_PUBLISHED", "ANA_DASHBOARD_VERSION", version.getId(),
                Map.of("dashboardId", dashboardId, "versionNumber", versionNumber,
                        "draftRevision", dashboard.getDraftRevision(), "snapshotHash", version.getSnapshotHash(),
                        "componentCount", componentSnapshots.size()));
        return new DashboardModels.PublishResult(dashboardId, version.getId(), versionNumber,
                dashboard.getDraftRevision(), version.getSnapshotHash(), preview);
    }

    public DashboardModels.RuntimeDashboard runtime(AuthenticatedContext context, String traceId) {
        AnaDashboard dashboard = ownedDashboards(context).stream()
                .sorted(Comparator.comparing(AnaDashboard::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(item -> publication(item.getId()) != null)
                .findFirst().orElse(null);
        if (dashboard == null) {
            return new DashboardModels.RuntimeDashboard(false, contextType(context), null, null, null,
                    null, null, null, List.of(), "当前上下文尚未发布仪表盘，不展示写死统计或伪造零值。");
        }
        AnaDashboardPublication publication = publication(dashboard.getId());
        AnaDashboardVersion version = publication == null ? null
                : dashboardVersionService.selectById(publication.getCurrentVersionId());
        if (version == null || !dashboard.getId().equals(version.getDashboardId())) {
            return new DashboardModels.RuntimeDashboard(false, contextType(context), dashboard.getId(), null, null,
                    dashboard.getName(), dashboard.getDescription(), null, List.of(),
                    "仪表盘发布指针无效，请联系管理员重新发布；页面未伪造任何统计值。");
        }
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        List<Map<String, Object>> componentSnapshots = objectMapper.convertValue(
                snapshot.getOrDefault("components", List.of()), MAP_LIST_TYPE);
        List<DashboardModels.RuntimeComponent> visible = componentSnapshots.stream()
                .filter(component -> visible(context, readMapValue(component.get("displayConfig"))))
                .map(component -> runtimeComponent(context, component, traceId)).toList();
        return new DashboardModels.RuntimeDashboard(true, contextType(context), dashboard.getId(), version.getId(),
                version.getVersionNumber(), string(snapshot.get("name")), stringOrNull(snapshot.get("description")),
                version.getPublishedAt(), visible, "仪表盘内容来自不可变发布版本，并按当前上下文重新执行权限。" );
    }

    private DashboardModels.Preview previewSnapshot(
            AuthenticatedContext context, Long dashboardId, Integer draftRevision,
            List<Map<String, Object>> componentSnapshots, String traceId) {
        List<DashboardModels.RuntimeComponent> rendered = componentSnapshots.stream()
                .filter(component -> visible(context, readMapValue(component.get("displayConfig"))))
                .map(component -> runtimeComponent(context, component, traceId)).toList();
        List<DashboardModels.Issue> issues = rendered.stream()
                .filter(component -> "ERROR".equals(component.outcome()))
                .map(component -> new DashboardModels.Issue(component.errorCode(), component.message(),
                        component.componentKey())).toList();
        return new DashboardModels.Preview(dashboardId, draftRevision, issues.isEmpty(), issues, rendered);
    }

    private DashboardModels.RuntimeComponent runtimeComponent(
            AuthenticatedContext context, Map<String, Object> component, String traceId) {
        String key = string(component.get("componentKey"));
        String type = string(component.get("componentType"));
        String title = string(component.get("title"));
        Map<String, Object> layout = readMapValue(component.get("layout"));
        Map<String, Object> display = readMapValue(component.get("displayConfig"));
        Map<String, Object> drill = readMapValue(component.get("drillTarget"));
        Map<String, Object> sourceSnapshot = readMapValue(component.get("dataSource"));
        LocalDateTime now = LocalDateTime.now();
        if ("QUICK_ENTRY".equals(type) && sourceSnapshot.isEmpty()) {
            DrillResult target = drill(context, drill);
            return new DashboardModels.RuntimeComponent(key, type, title, "READY", null, List.of(),
                    "快捷入口不执行数据查询。", layout, display, target.target(), target.available(),
                    null, "快捷入口已按当前权限校验。", now);
        }
        if (sourceSnapshot.isEmpty()) {
            return failed(key, type, title, layout, display, drill,
                    "DASHBOARD_DATA_SOURCE_MISSING", "组件没有绑定已发布数据源。", now);
        }
        Long sourceId = longValue(sourceSnapshot.get("dataSourceId"));
        AnaDataSource current = sourceId == null ? null : sourceService.selectById(sourceId);
        if (current == null || !owned(context, current)) {
            LocalDateTime changedAt = current == null ? now : current.getUpdatedAt();
            return failed(key, type, title, layout, display, drill,
                    "DASHBOARD_DATA_SOURCE_INACTIVE",
                    "数据源已失效或不属于当前上下文；组件不使用零值代替失败。", changedAt);
        }
        Map<String, Object> policy = readMapValue(sourceSnapshot.get("permissionPolicy"));
        if (!allowsPolicy(context, policy)) {
            return failed(key, type, title, layout, display, drill,
                    "DASHBOARD_DATA_SOURCE_FORBIDDEN", "当前上下文无权执行该组件数据源。", now);
        }
        try {
            if ("KPI".equals(type)) {
                return runtimeKpi(context, component, sourceSnapshot, key, title, layout, display, drill,
                        traceId, now);
            }
            SourceResult source = executeSource(context, sourceSnapshot,
                    readMapValue(component.get("queryParameters")), traceId);
            DrillResult target = drill(context, drill);
            return new DashboardModels.RuntimeComponent(key, type, title, "READY", source.value(),
                    source.items(), source.metricDefinition(), layout, display, target.target(), target.available(),
                    null, "数据源执行成功。", now);
        } catch (DomainException exception) {
            return failed(key, type, title, layout, display, drill, exception.code(),
                    exception.getMessage() + "；组件未伪造零值。", now);
        } catch (RuntimeException exception) {
            return failed(key, type, title, layout, display, drill,
                    "DASHBOARD_DATA_SOURCE_FAILED", "数据源执行失败；组件未伪造零值。", now);
        }
    }

    private DashboardModels.RuntimeComponent runtimeKpi(
            AuthenticatedContext context, Map<String, Object> component,
            Map<String, Object> sourceSnapshot, String key, String title,
            Map<String, Object> layout, Map<String, Object> display,
            Map<String, Object> drill, String traceId, LocalDateTime now) {
        Map<String, Object> query = readMapValue(component.get("queryParameters"));
        Long kpiId = longValue(query.get("kpiId"));
        int expectedKpiVersion = intValue(query.get("kpiVersion"), -1);
        if (kpiId == null || expectedKpiVersion < 0) {
            throw invalid("DASHBOARD_KPI_BINDING_INVALID", "KPI 组件缺少 KPI 配置版本绑定");
        }
        KpiModels.KpiView kpi = kpiService.runtimeOne(context, kpiId, traceId);
        if (!Objects.equals(kpi.version(), expectedKpiVersion)) {
            throw invalid("DASHBOARD_KPI_VERSION_CHANGED", "KPI 配置版本已变化，请重新预览并发布仪表盘");
        }
        Long sourceVersionId = longValue(sourceSnapshot.get("dataSourceVersionId"));
        if (!Objects.equals(sourceVersionId, kpi.dataSourceVersionId())) {
            throw invalid("DASHBOARD_KPI_SOURCE_VERSION_MISMATCH", "仪表盘数据源版本与 KPI 计算版本不一致");
        }
        KpiModels.ResultView result = kpi.latestResult();
        if (result == null) {
            throw invalid("DASHBOARD_KPI_RESULT_MISSING", "KPI 尚无计算快照，不能使用零值代替");
        }
        DrillResult target = drill(context, drill);
        String definition = "实际 " + result.actualValue().stripTrailingZeros().toPlainString()
                + " / 目标 " + kpi.targetOperator() + " "
                + result.targetValue().stripTrailingZeros().toPlainString()
                + " / 达成率 " + result.achievementRate().stripTrailingZeros().toPlainString()
                + "% / 数据源版本 #" + kpi.dataSourceVersionId()
                + (result.stale() ? " / 当前为上次成功快照" : "");
        return new DashboardModels.RuntimeComponent(key, "KPI", title, "READY", result.actualValue(),
                result.drillItems(), definition, layout, display, target.target(),
                target.available() && result.drillAvailable(), null,
                result.stale() ? "数据源本次不可用，已保留上次成功快照。" : "KPI 计算快照读取成功。",
                result.calculatedAt() == null ? now : result.calculatedAt());
    }

    private SourceResult executeSource(
            AuthenticatedContext context, Map<String, Object> sourceSnapshot,
            Map<String, Object> queryParameters, String traceId) {
        String sourceType = string(sourceSnapshot.get("sourceType"));
        LinkedHashMap<String, Object> definition = new LinkedHashMap<>(
                readMapValue(sourceSnapshot.get("definition")));
        definition.putAll(queryParameters);
        int limit = Math.max(1, Math.min(20, intValue(definition.get("limit"), 5)));
        if ("PLATFORM_SYSTEMS".equals(sourceType)) {
            if (context.systemId() != null) {
                throw invalid("DASHBOARD_SOURCE_CONTEXT_MISMATCH", "平台系统汇总不能在系统上下文执行");
            }
            List<AccessibleSystem> systems = systemEntryService.listAccessible(context.accountId());
            List<Map<String, Object>> items = systems.stream().limit(limit).map(system -> {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("id", system.systemId());
                item.put("title", system.systemName());
                item.put("subtitle", system.systemCode());
                item.put("status", system.status());
                item.put("route", "/systems/" + system.systemId());
                return (Map<String, Object>) item;
            }).toList();
            return new SourceResult((long) systems.size(), items,
                    "当前平台账号有效系统成员关系对应的可进入系统数。" );
        }
        if ("MODULE_RECORDS".equals(sourceType)) {
            if (context.systemId() == null || context.tenantId() == null) {
                throw invalid("DASHBOARD_SOURCE_CONTEXT_MISMATCH", "模块数据源必须在系统租户上下文执行");
            }
            String moduleCode = string(definition.get("moduleCode"));
            RuntimeRecordList list = runtimeDataService.list(context, moduleCode, "ACTIVE", "ALL", "", "[]",
                    stringOrDefault(definition.get("sortField"), "updatedAt"),
                    stringOrDefault(definition.get("sortDirection"), "DESC"), 1, limit, traceId);
            List<Map<String, Object>> items = list.records().stream()
                    .map(record -> recordItem(context, moduleCode, record)).toList();
            return new SourceResult(list.total(), items,
                    "当前系统、当前租户与显式共享范围内，按普通 " + moduleCode
                            + "/LIST 权限和数据范围统计的有效记录。" );
        }
        if ("MODULE_REPORT".equals(sourceType)) {
            ReportModels.Result report = reportService.executeSnapshot(context, sourceSnapshot, traceId);
            Map<String, Object> dimension = readMapValue(definition.get("dimension"));
            String dimensionAlias = stringOrNull(dimension.get("alias"));
            String dimensionFieldCode = stringOrNull(dimension.get("fieldCode"));
            Object modulesValue = definition.get("modules");
            String primaryAlias = modulesValue instanceof List<?> modules && !modules.isEmpty()
                    ? stringOrNull(readMapValue(modules.getFirst()).get("alias")) : null;
            List<Map<String, Object>> items = report.groups().isEmpty() ? report.items()
                    : report.groups().stream().map(group -> {
                        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                        item.put("id", group.key());
                        item.put("title", group.label());
                        item.put("value", group.value());
                        item.put("status", group.rowCount() + " 行授权记录");
                        // A primary-module grouping can be carried into the ordinary record list as a
                        // permission-checked filter. Related-module groupings remain chart-only because
                        // flattening them into another module's list would change the report semantics.
                        if (dimensionFieldCode != null
                                && (dimensionAlias == null || Objects.equals(primaryAlias, dimensionAlias))) {
                            item.put("filter", Map.of(
                                    "fieldCode", dimensionFieldCode,
                                    "operator", "EQ",
                                    "value", group.key()));
                        }
                        return (Map<String, Object>) item;
                    }).toList();
            return new SourceResult(report.value(), items, report.metricDefinition());
        }
        if ("EXTERNAL_API".equals(sourceType)) {
            throw invalid("DASHBOARD_EXTERNAL_CONNECTION_UNAVAILABLE",
                    "外部 API 数据源只保存连接引用和已批准请求编码；当前部署尚未绑定该连接，未执行任意网络请求");
        }
        if ("DATABASE_CONNECTION".equals(sourceType)) {
            throw invalid("DASHBOARD_DATABASE_CONNECTION_UNAVAILABLE",
                    "数据库数据源只保存连接引用和已批准查询编码；当前部署尚未绑定只读连接，未执行任意 SQL");
        }
        if ("TODO_ITEMS".equals(sourceType)) {
            List<TodoModels.TodoView> todos = todoService.list(context, "PENDING", "ALL");
            List<Map<String, Object>> items = todos.stream().limit(limit).map(todo -> {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("id", todo.id());
                item.put("title", todo.title());
                item.put("subtitle", todo.summary());
                item.put("status", todo.status());
                item.put("route", todo.targetRoute());
                return (Map<String, Object>) item;
            }).toList();
            return new SourceResult((long) todos.size(), items, "当前账号在当前上下文中有权查看的待处理待办。" );
        }
        if ("MESSAGE_ITEMS".equals(sourceType)) {
            MessageModels.InboxView inbox = messageService.list(context, "UNREAD", "ALL");
            List<Map<String, Object>> items = inbox.messages().stream().limit(limit).map(message -> {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("id", message.id());
                item.put("title", message.subject());
                item.put("subtitle", message.content());
                item.put("status", message.recipientStatus());
                item.put("route", message.targetCurrentlyAccessible() ? message.targetRoute() : null);
                return (Map<String, Object>) item;
            }).toList();
            return new SourceResult(inbox.unreadCount(), items, "当前账号在当前上下文中有权查看的未读消息。" );
        }
        if ("WORK_PROJECTS".equals(sourceType)) {
            List<WorkManagementModels.ProjectView> projects = workManagementService.projects(context);
            List<Map<String, Object>> items = projects.stream().limit(limit).map(project -> {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("id", project.id());
                item.put("title", project.name());
                item.put("subtitle", project.code());
                item.put("status", project.status());
                item.put("progressPercent", project.progressPercent());
                item.put("route", context.systemId() == null ? "/platform/tasks"
                        : "/systems/" + context.systemId() + "?workspace=tasks");
                return (Map<String, Object>) item;
            }).toList();
            return new SourceResult((long) projects.size(), items,
                    "当前账号按工作管理权限和项目成员关系可查看的项目数。" );
        }
        throw invalid("DASHBOARD_SOURCE_TYPE_UNSUPPORTED", "数据源类型不受支持");
    }

    private Map<String, Object> recordItem(
            AuthenticatedContext context, String moduleCode, RuntimeRecordView record) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.id());
        item.put("title", record.title());
        item.put("subtitle", record.recordNumber());
        item.put("status", record.status());
        item.put("fields", record.fields());
        item.put("route", "/systems/" + context.systemId()
                + "?workspace=runtime&module=" + moduleCode + "&recordId=" + record.id());
        return item;
    }

    private DashboardModels.RuntimeComponent failed(
            String key, String type, String title, Map<String, Object> layout,
            Map<String, Object> display, Map<String, Object> drill,
            String errorCode, String message, LocalDateTime updatedAt) {
        return new DashboardModels.RuntimeComponent(key, type, title, "ERROR", null, List.of(),
                "数据源执行失败，当前没有可展示的统计口径结果。", layout, display, drill,
                false, errorCode, message, updatedAt == null ? LocalDateTime.now() : updatedAt);
    }

    private boolean visible(AuthenticatedContext context, Map<String, Object> display) {
        return allowsPolicy(context, readMapValue(display.get("visibilityPermission")));
    }

    private boolean allowsPolicy(AuthenticatedContext context, Map<String, Object> policy) {
        if (policy.isEmpty()) return true;
        String resourceType = stringOrNull(policy.get("resourceType"));
        String resourceCode = stringOrNull(policy.get("resourceCode"));
        String actionCode = stringOrNull(policy.get("actionCode"));
        return resourceType == null || resourceCode == null || actionCode == null
                || permissionChecker.allows(context, resourceType, resourceCode, actionCode);
    }

    private DrillResult drill(AuthenticatedContext context, Map<String, Object> target) {
        if (target.isEmpty()) return new DrillResult(Map.of(), false);
        Map<String, Object> policy = readMapValue(target.get("permission"));
        if (!allowsPolicy(context, policy)) return new DrillResult(Map.of(), false);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(target);
        result.remove("permission");
        String moduleCode = stringOrNull(result.get("moduleCode"));
        if (moduleCode != null && context.systemId() != null) {
            result.put("route", "/systems/" + context.systemId()
                    + "?workspace=runtime&module=" + moduleCode);
        }
        String route = stringOrNull(result.get("route"));
        return new DrillResult(route == null ? Map.of() : result, route != null);
    }

    private Map<String, Object> componentSnapshot(
            AuthenticatedContext context, AnaDashboardComponent component) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("componentKey", component.getComponentKey());
        snapshot.put("componentType", component.getComponentType());
        snapshot.put("title", component.getTitle());
        snapshot.put("layout", readMap(component.getLayoutJson()));
        snapshot.put("queryParameters", readMap(component.getQueryParameterJson()));
        snapshot.put("displayConfig", readMap(component.getDisplayConfigJson()));
        snapshot.put("drillTarget", readMap(component.getDrillTargetJson()));
        snapshot.put("sortOrder", component.getSortOrder());
        if (component.getDataSourceId() != null) {
            AnaDataSource source = requireSource(context, component.getDataSourceId());
            AnaDataSourceVersion version = latestSourceVersion(source.getId());
            if (version == null) {
                snapshot.put("dataSource", Map.of("dataSourceId", source.getId(), "status", source.getStatus()));
            } else {
                LinkedHashMap<String, Object> published = new LinkedHashMap<>(readMap(version.getSnapshotJson()));
                published.put("dataSourceId", source.getId());
                published.put("dataSourceVersionId", version.getId());
                published.put("dataSourceVersionNumber", version.getVersionNumber());
                published.put("definitionHash", version.getDefinitionHash());
                snapshot.put("dataSource", published);
            }
        } else {
            snapshot.put("dataSource", Map.of());
        }
        return snapshot;
    }

    private LinkedHashMap<String, Object> sourceSnapshot(AnaDataSource source, AnaDataSourceVersion version) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("dataSourceId", source.getId());
        snapshot.put("dataSourceVersionId", version == null ? null : version.getId());
        snapshot.put("dataSourceVersionNumber", version == null ? null : version.getVersionNumber());
        snapshot.put("contextType", source.getContextType());
        snapshot.put("platformId", source.getPlatformId());
        snapshot.put("systemId", source.getSystemId());
        snapshot.put("ownerTenantId", source.getOwnerTenantId());
        snapshot.put("code", source.getCode());
        snapshot.put("name", source.getName());
        snapshot.put("sourceType", source.getSourceType());
        snapshot.put("definition", readMap(source.getDefinitionJson()));
        snapshot.put("permissionPolicy", readMap(source.getPermissionPolicyJson()));
        snapshot.put("draftRevision", source.getDraftRevision());
        snapshot.put("status", source.getStatus());
        return snapshot;
    }

    private void replaceComponents(
            AuthenticatedContext context, AnaDashboard dashboard,
            List<DashboardModels.ComponentInput> inputs) {
        for (AnaDashboardComponent existing : components(dashboard.getId())) {
            componentService.deleteById(existing.getId());
        }
        for (DashboardModels.ComponentInput input : inputs.stream()
                .sorted(Comparator.comparing(DashboardModels.ComponentInput::sortOrder)).toList()) {
            if (input.dataSourceId() != null) requireSource(context, input.dataSourceId());
            if (!"QUICK_ENTRY".equals(input.componentType()) && input.dataSourceId() == null) {
                throw invalid("DASHBOARD_COMPONENT_SOURCE_REQUIRED", "除快捷入口外的组件必须绑定数据源");
            }
            AnaDashboardComponent component = new AnaDashboardComponent();
            component.setDashboardId(dashboard.getId());
            component.setComponentKey(input.componentKey());
            component.setComponentType(input.componentType());
            component.setTitle(input.title().strip());
            component.setDataSourceId(input.dataSourceId());
            component.setLayoutJson(writeJson(input.layout()));
            component.setQueryParameterJson(writeJson(input.queryParameters()));
            component.setDisplayConfigJson(writeJson(input.displayConfig()));
            component.setDrillTargetJson(input.drillTarget() == null ? null : writeJson(input.drillTarget()));
            component.setSortOrder(input.sortOrder());
            component.setCreatedAt(LocalDateTime.now());
            component.setUpdatedAt(LocalDateTime.now());
            component.setVersion(0);
            componentService.insert(component);
        }
    }

    private void validateComponents(List<DashboardModels.ComponentInput> components) {
        if (components.stream().map(DashboardModels.ComponentInput::componentKey).distinct().count()
                != components.size()) {
            throw invalid("DASHBOARD_COMPONENT_KEY_DUPLICATE", "组件编码不能重复");
        }
        if (components.stream().map(DashboardModels.ComponentInput::sortOrder).distinct().count()
                != components.size()) {
            throw invalid("DASHBOARD_COMPONENT_ORDER_DUPLICATE", "组件排序号不能重复");
        }
    }

    private Map<String, Object> validateSource(
            AuthenticatedContext context, String sourceType, Map<String, Object> definition, String traceId) {
        if (!OPEN_SOURCE_TYPES.contains(sourceType)) {
            throw invalid("DASHBOARD_SOURCE_TYPE_UNSUPPORTED", "数据源类型不受支持");
        }
        if ("PLATFORM_SYSTEMS".equals(sourceType) && context.systemId() != null) {
            throw invalid("DASHBOARD_SOURCE_CONTEXT_MISMATCH", "平台系统汇总只能配置在平台上下文");
        }
        if ("MODULE_RECORDS".equals(sourceType)) {
            if (context.systemId() == null) {
                throw invalid("DASHBOARD_SOURCE_CONTEXT_MISMATCH", "模块数据源只能配置在系统上下文");
            }
            String moduleCode = string(definition.get("moduleCode"));
            if (!MODULE_CODE.matcher(moduleCode).matches()) {
                throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID", "模块数据源必须配置有效 moduleCode");
            }
        }
        if ("MODULE_REPORT".equals(sourceType)) {
            if (context.systemId() == null) {
                throw invalid("DASHBOARD_SOURCE_CONTEXT_MISMATCH", "结构化模块报表只能配置在系统上下文");
            }
            return reportService.prepareDefinition(context, definition, traceId);
        }
        if ("EXTERNAL_API".equals(sourceType)) {
            requireSystemSourceContext(context, "外部 API");
            rejectSensitiveConnectionMaterial(definition);
            requireOnlyKeys(definition, Set.of("connectionReference", "requestCode", "method",
                    "queryParameters", "responseSelector", "limit", "timeoutMillis"));
            String connectionReference = string(definition.get("connectionReference"));
            String requestCode = string(definition.get("requestCode"));
            String method = stringOrDefault(definition.get("method"), "GET").toUpperCase(java.util.Locale.ROOT);
            if (!CONNECTION_REFERENCE.matcher(connectionReference).matches()
                    || !APPROVED_OPERATION.matcher(requestCode).matches()
                    || !Set.of("GET", "POST").contains(method)) {
                throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID",
                        "外部 API 数据源必须选择连接引用、已批准请求编码和 GET/POST 方法");
            }
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(definition);
            normalized.put("method", method);
            normalized.put("queryParameters", readMapValue(definition.get("queryParameters")));
            normalized.put("responseSelector", stringOrDefault(definition.get("responseSelector"), "$"));
            normalized.put("limit", validatedConnectionLimit(definition));
            normalized.put("timeoutMillis", validatedTimeout(definition));
            return normalized;
        }
        if ("DATABASE_CONNECTION".equals(sourceType)) {
            requireSystemSourceContext(context, "数据库连接");
            rejectSensitiveConnectionMaterial(definition);
            requireOnlyKeys(definition, Set.of("connectionReference", "queryCode", "parameters",
                    "outputFields", "limit", "timeoutMillis"));
            String connectionReference = string(definition.get("connectionReference"));
            String queryCode = string(definition.get("queryCode"));
            if (!CONNECTION_REFERENCE.matcher(connectionReference).matches()
                    || !APPROVED_OPERATION.matcher(queryCode).matches()) {
                throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID",
                        "数据库数据源必须选择连接引用和已批准查询编码");
            }
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(definition);
            normalized.put("parameters", readMapValue(definition.get("parameters")));
            Object outputFields = definition.get("outputFields");
            normalized.put("outputFields", outputFields instanceof List<?> values
                    ? values.stream().map(this::string).distinct().toList() : List.of());
            normalized.put("limit", validatedConnectionLimit(definition));
            normalized.put("timeoutMillis", validatedTimeout(definition));
            return normalized;
        }
        int limit = intValue(definition.get("limit"), 5);
        if (limit < 1 || limit > 20) {
            throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID", "组件列表数量必须在 1 到 20 之间");
        }
        return definition;
    }

    private void requireSystemSourceContext(AuthenticatedContext context, String label) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw invalid("DASHBOARD_SOURCE_CONTEXT_MISMATCH", label + "数据源只能配置在系统上下文");
        }
    }

    private void rejectSensitiveConnectionMaterial(Map<String, Object> definition) {
        Set<String> forbidden = Set.of("url", "baseurl", "endpointurl", "jdbcurl", "host", "password",
                "secret", "token", "apikey", "authorization", "sql", "query");
        List<String> exposed = definition.keySet().stream()
                .filter(key -> forbidden.contains(key.replace("_", "").toLowerCase(java.util.Locale.ROOT))).toList();
        if (!exposed.isEmpty()) {
            throw invalid("DASHBOARD_CONNECTION_SECRET_FORBIDDEN",
                    "数据源不能保存地址、凭据或任意查询，只能引用受管连接和已批准操作");
        }
    }

    private void requireOnlyKeys(Map<String, Object> definition, Set<String> allowed) {
        List<String> unknown = definition.keySet().stream().filter(key -> !allowed.contains(key)).toList();
        if (!unknown.isEmpty()) {
            throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID", "数据源包含不受支持的配置项：" + String.join("、", unknown));
        }
    }

    private int validatedConnectionLimit(Map<String, Object> definition) {
        int limit = intValue(definition.get("limit"), 100);
        if (limit < 1 || limit > 1000) {
            throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID", "外部连接单次返回数量必须在 1 到 1000 之间");
        }
        return limit;
    }

    private int validatedTimeout(Map<String, Object> definition) {
        int timeout = intValue(definition.get("timeoutMillis"), 5000);
        if (timeout < 500 || timeout > 30000) {
            throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID", "外部连接超时时间必须在 500 到 30000 毫秒之间");
        }
        return timeout;
    }

    private List<AnaDataSource> ownedSources(AuthenticatedContext context) {
        var query = Wrappers.<AnaDataSource>lambdaQuery()
                .eq(AnaDataSource::getContextType, contextType(context))
                .eq(AnaDataSource::getPlatformId, context.platformId());
        if (context.systemId() == null) query.isNull(AnaDataSource::getSystemId).isNull(AnaDataSource::getOwnerTenantId);
        else query.eq(AnaDataSource::getSystemId, context.systemId())
                .eq(AnaDataSource::getOwnerTenantId, context.tenantId());
        return sourceService.selectList(query);
    }

    private List<AnaDashboard> ownedDashboards(AuthenticatedContext context) {
        var query = Wrappers.<AnaDashboard>lambdaQuery()
                .eq(AnaDashboard::getContextType, contextType(context))
                .eq(AnaDashboard::getPlatformId, context.platformId());
        if (context.systemId() == null) query.isNull(AnaDashboard::getSystemId).isNull(AnaDashboard::getOwnerTenantId);
        else query.eq(AnaDashboard::getSystemId, context.systemId())
                .eq(AnaDashboard::getOwnerTenantId, context.tenantId());
        return dashboardService.selectList(query);
    }

    private AnaDataSource findSource(AuthenticatedContext context, String code) {
        return ownedSources(context).stream().filter(item -> code.equals(item.getCode())).findFirst().orElse(null);
    }

    private AnaDashboard findDashboard(AuthenticatedContext context, String code) {
        return ownedDashboards(context).stream().filter(item -> code.equals(item.getCode())).findFirst().orElse(null);
    }

    private AnaDataSource requireSource(AuthenticatedContext context, Long sourceId) {
        AnaDataSource source = sourceService.selectById(sourceId);
        if (source == null || !owned(context, source)) {
            throw new DomainException("DASHBOARD_DATA_SOURCE_NOT_FOUND", "数据源不存在或不属于当前上下文",
                    HttpStatus.NOT_FOUND);
        }
        return source;
    }

    private AnaDashboard requireDashboard(AuthenticatedContext context, Long dashboardId) {
        AnaDashboard dashboard = dashboardService.selectById(dashboardId);
        if (dashboard == null || !owned(context, dashboard)) {
            throw new DomainException("DASHBOARD_NOT_FOUND", "仪表盘不存在或不属于当前上下文",
                    HttpStatus.NOT_FOUND);
        }
        return dashboard;
    }

    private boolean owned(AuthenticatedContext context, AnaDataSource source) {
        return contextType(context).equals(source.getContextType())
                && Objects.equals(context.platformId(), source.getPlatformId())
                && Objects.equals(context.systemId(), source.getSystemId())
                && Objects.equals(context.systemId() == null ? null : context.tenantId(), source.getOwnerTenantId());
    }

    private boolean owned(AuthenticatedContext context, AnaDashboard dashboard) {
        return contextType(context).equals(dashboard.getContextType())
                && Objects.equals(context.platformId(), dashboard.getPlatformId())
                && Objects.equals(context.systemId(), dashboard.getSystemId())
                && Objects.equals(context.systemId() == null ? null : context.tenantId(), dashboard.getOwnerTenantId());
    }

    private void bindContext(AnaDataSource source, AuthenticatedContext context) {
        source.setContextType(contextType(context));
        source.setPlatformId(context.platformId());
        source.setSystemId(context.systemId());
        source.setOwnerTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private void bindContext(AnaDashboard dashboard, AuthenticatedContext context) {
        dashboard.setContextType(contextType(context));
        dashboard.setPlatformId(context.platformId());
        dashboard.setSystemId(context.systemId());
        dashboard.setOwnerTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private List<AnaDashboardComponent> components(Long dashboardId) {
        return componentService.selectList(Wrappers.<AnaDashboardComponent>lambdaQuery()
                .eq(AnaDashboardComponent::getDashboardId, dashboardId)
                .orderByAsc(AnaDashboardComponent::getSortOrder));
    }

    private AnaDashboardPublication publication(Long dashboardId) {
        return publicationService.selectList(Wrappers.<AnaDashboardPublication>lambdaQuery()
                        .eq(AnaDashboardPublication::getDashboardId, dashboardId))
                .stream().findFirst().orElse(null);
    }

    private AnaDataSourceVersion latestSourceVersion(Long sourceId) {
        return sourceVersionService.selectList(Wrappers.<AnaDataSourceVersion>lambdaQuery()
                        .eq(AnaDataSourceVersion::getDataSourceId, sourceId)
                        .orderByDesc(AnaDataSourceVersion::getVersionNumber)).stream().findFirst().orElse(null);
    }

    private AnaDashboardVersion latestDashboardVersion(Long dashboardId) {
        return dashboardVersionService.selectList(Wrappers.<AnaDashboardVersion>lambdaQuery()
                        .eq(AnaDashboardVersion::getDashboardId, dashboardId)
                        .orderByDesc(AnaDashboardVersion::getVersionNumber)).stream().findFirst().orElse(null);
    }

    private DashboardModels.DataSourceView sourceView(AnaDataSource source) {
        List<DashboardModels.DataSourceVersionView> versions = sourceVersionService.selectList(
                        Wrappers.<AnaDataSourceVersion>lambdaQuery()
                                .eq(AnaDataSourceVersion::getDataSourceId, source.getId())
                                .orderByDesc(AnaDataSourceVersion::getVersionNumber)).stream()
                .map(version -> new DashboardModels.DataSourceVersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getDefinitionHash(), version.getPublishedAt())).toList();
        return new DashboardModels.DataSourceView(source.getId(), source.getContextType(), source.getCode(),
                source.getName(), source.getSourceType(), source.getDraftRevision(),
                readMap(source.getDefinitionJson()), readMap(source.getPermissionPolicyJson()), source.getStatus(),
                source.getVersion(), source.getUpdatedAt(), sourceBoundary(source), versions);
    }

    private DashboardModels.DataSourceBoundary sourceBoundary(AnaDataSource source) {
        return switch (source.getSourceType()) {
            case "MODULE_RECORDS", "MODULE_REPORT" -> new DashboardModels.DataSourceBoundary(
                    "SYSTEM_MODULE", "CURRENT_SYSTEM", "PUBLISHED_BUSINESS_SCHEMA",
                    "CURRENT_USER_DATA_SCOPE", true,
                    "查询使用已发布模块字段，并在每次执行时重新应用当前用户权限和数据范围。");
            case "EXTERNAL_API" -> new DashboardModels.DataSourceBoundary(
                    "EXTERNAL_API", "MANAGED_REFERENCE", "APPROVED_REQUEST_CODE",
                    "SOURCE_POLICY_AND_CONNECTION_POLICY", false,
                    "草稿和发布版本不保存地址或凭据；部署绑定受管连接后才允许执行。");
            case "DATABASE_CONNECTION" -> new DashboardModels.DataSourceBoundary(
                    "DATABASE", "MANAGED_READ_ONLY_REFERENCE", "APPROVED_QUERY_CODE",
                    "SOURCE_POLICY_AND_ROW_SCOPE", false,
                    "禁止任意 SQL 和直存凭据；部署绑定只读连接与批准查询后才允许执行。");
            default -> new DashboardModels.DataSourceBoundary(
                    "SYSTEM_CAPABILITY", "CURRENT_CONTEXT", "BUILT_IN_QUERY",
                    "CURRENT_USER_PERMISSION", true,
                    "查询使用当前上下文中的业务服务，并执行当前用户权限。");
        };
    }

    private DashboardModels.DashboardView dashboardView(AnaDashboard dashboard) {
        AnaDashboardPublication publication = publication(dashboard.getId());
        List<DashboardModels.DashboardVersionView> versions = dashboardVersionService.selectList(
                        Wrappers.<AnaDashboardVersion>lambdaQuery()
                                .eq(AnaDashboardVersion::getDashboardId, dashboard.getId())
                                .orderByDesc(AnaDashboardVersion::getVersionNumber)).stream()
                .map(version -> new DashboardModels.DashboardVersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getSnapshotHash(), version.getPublishedAt(),
                        publication != null && version.getId().equals(publication.getCurrentVersionId()))).toList();
        List<DashboardModels.ComponentView> components = components(dashboard.getId()).stream()
                .map(component -> new DashboardModels.ComponentView(component.getId(), component.getComponentKey(),
                        component.getComponentType(), component.getTitle(), component.getDataSourceId(),
                        readMap(component.getLayoutJson()), readMap(component.getQueryParameterJson()),
                        readMap(component.getDisplayConfigJson()), readMap(component.getDrillTargetJson()),
                        component.getSortOrder(), component.getVersion())).toList();
        return new DashboardModels.DashboardView(dashboard.getId(), dashboard.getContextType(), dashboard.getCode(),
                dashboard.getName(), dashboard.getDescription(), dashboard.getDraftRevision(), dashboard.getStatus(),
                dashboard.getVersion(), dashboard.getUpdatedAt(), components, versions);
    }

    private void requireAdmin(AuthenticatedContext context) {
        boolean allowed = context.systemId() == null
                ? permissionChecker.allows(context, "PLATFORM", "CONFIGURATION", "MANAGE")
                : permissionChecker.allows(context, "CONFIG", "SYSTEM", "MANAGE");
        if (!allowed) {
            throw new DomainException("DASHBOARD_ADMIN_DENIED", "当前上下文没有仪表盘配置权限",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void requireVersion(Integer actual, Integer expected, String code) {
        if (expected == null || !Objects.equals(actual, expected)) {
            throw conflict(code, "版本已变化，请刷新后重试");
        }
    }

    private void audit(AuthenticatedContext context, String traceId, String event, String objectType,
                       Long objectId, Map<String, Object> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), event, objectType, objectId.toString(), "SUCCESS", detail);
    }

    private String contextType(AuthenticatedContext context) {
        return context.systemId() == null ? "PLATFORM" : "SYSTEM";
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read analytics JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapValue(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return objectMapper.convertValue(map, MAP_TYPE);
        if (value instanceof String string) return readMap(string);
        return Map.of();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write analytics JSON", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOrNull(Object value) {
        String result = string(value).strip();
        return result.isBlank() ? null : result;
    }

    private String stringOrDefault(Object value, String defaultValue) {
        String result = stringOrNull(value);
        return result == null ? defaultValue : result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) return number.intValue();
        if (value == null || String.valueOf(value).isBlank()) return defaultValue;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException exception) { return defaultValue; }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record SourceResult(Object value, List<Map<String, Object>> items, String metricDefinition) {
    }

    private record DrillResult(Map<String, Object> target, boolean available) {
    }
}
