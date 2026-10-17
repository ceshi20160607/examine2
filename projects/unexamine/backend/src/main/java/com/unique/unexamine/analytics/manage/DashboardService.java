package com.unique.unexamine.analytics.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.manage.AccessibleSystem;
import com.unique.unexamine.system.manage.SystemEntryService;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.work.manage.WorkManagementModels;
import com.unique.unexamine.work.manage.WorkManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
            "PLATFORM_SYSTEMS", "MODULE_RECORDS", "MODULE_REPORT",
            "TODO_ITEMS", "MESSAGE_ITEMS", "WORK_PROJECTS");
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
    private final SystemTenantBaseService tenantService;
    private final ConfiguredModuleBaseService moduleService;
    private final ModulePublicationService modulePublicationService;
    private final LegacyModuleDashboardCompatibility legacyModuleDashboardCompatibility;

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
            KpiService kpiService,
            SystemTenantBaseService tenantService,
            ConfiguredModuleBaseService moduleService,
            ModulePublicationService modulePublicationService,
            LegacyModuleDashboardCompatibility legacyModuleDashboardCompatibility) {
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
        this.tenantService = tenantService;
        this.moduleService = moduleService;
        this.modulePublicationService = modulePublicationService;
        this.legacyModuleDashboardCompatibility = legacyModuleDashboardCompatibility;
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
        List<DashboardModels.DataSourceView> sources = availableSources(context).stream()
                .sorted(Comparator.comparing(AnaDataSource::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(source -> sourceView(context, source)).toList();
        List<DashboardModels.DashboardView> dashboards = ownedDashboards(context).stream()
                .filter(dashboard -> !"PERSONAL".equals(dashboard.getDashboardKind()))
                .sorted(Comparator.comparing(AnaDashboard::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::dashboardView).toList();
        return new DashboardModels.AdminOverview(contextType(context), sources, dashboards);
    }

    /** 个人首页目录只返回本人草稿和当前可用的已发布数据源，不暴露其他账号的页面。 */
    @Transactional(readOnly = true)
    public DashboardModels.PersonalOverview personalOverview(AuthenticatedContext context) {
        AnaDashboard dashboard = personalDashboard(context);
        List<DashboardModels.PublishedSourceOption> sources = availableSources(context).stream()
                .filter(source -> source.getCurrentVersionId() != null && personalSourceAllowed(context, source))
                .map(source -> new DashboardModels.PublishedSourceOption(
                        source.getId(), source.getName(), source.getSourceType())).toList();
        return new DashboardModels.PersonalOverview(dashboard == null ? null : dashboardView(dashboard), sources);
    }

    /** 个人页面由当前账号创建或更新；客户端不提供归属账号、作用域或内部编码。 */
    @Transactional
    public DashboardModels.DashboardView savePersonal(
            AuthenticatedContext context, DashboardModels.PersonalDashboardRequest input, String traceId) {
        validateComponents(input.components());
        for (DashboardModels.ComponentInput component : input.components()) {
            if (component.dataSourceId() == null) continue;
            AnaDataSource source = requireUsableSource(context, component.dataSourceId());
            if (source.getCurrentVersionId() == null || !personalSourceAllowed(context, source)) {
                throw new DomainException("DASHBOARD_PERSONAL_SOURCE_FORBIDDEN",
                        "只能使用当前账号有权访问的已发布数据源", HttpStatus.FORBIDDEN);
            }
        }
        AnaDashboard dashboard = personalDashboard(context);
        LocalDateTime now = LocalDateTime.now();
        if (dashboard == null) {
            if (input.expectedVersion() != null) {
                throw conflict("DASHBOARD_VERSION_CONFLICT", "个人首页尚未创建，请刷新后重试");
            }
            dashboard = new AnaDashboard();
            bindContext(dashboard, context);
            dashboard.setDashboardKind("PERSONAL");
            dashboard.setOwnerAccountId(context.accountId());
            // 同一租户内多账号共享物理唯一键，编码必须包含真实账号主键。
            dashboard.setCode("personal_home_" + context.accountId());
            dashboard.setDraftRevision(1);
            dashboard.setCreatedByAccountId(context.accountId());
            dashboard.setCreateTime(now);
            dashboard.setVersion(0);
            dashboard.setName(input.name().strip());
            dashboard.setDescription(blankToNull(input.description()));
            dashboard.setStatus("DRAFT");
            dashboard.setUpdateTime(now);
            dashboardService.insert(dashboard);
        } else {
            requireVersion(dashboard.getVersion(), input.expectedVersion(), "DASHBOARD_VERSION_CONFLICT");
            dashboard.setName(input.name().strip());
            dashboard.setDescription(blankToNull(input.description()));
            dashboard.setDraftRevision(dashboard.getDraftRevision() + 1);
            dashboard.setStatus("DRAFT");
            dashboard.setUpdateTime(now);
            if (dashboardService.updateById(dashboard) != 1) {
                throw conflict("DASHBOARD_VERSION_CONFLICT", "个人首页已被修改，请刷新后重试");
            }
        }
        replaceComponents(context, dashboard, input.components());
        audit(context, traceId, "DASHBOARD_PERSONAL_SAVED", "ANA_DASHBOARD", dashboard.getId(),
                Map.of("componentCount", input.components().size(), "draftRevision", dashboard.getDraftRevision()));
        return dashboardView(dashboardService.selectById(dashboard.getId()));
    }

    @Transactional(readOnly = true)
    public DashboardModels.Preview personalPreview(AuthenticatedContext context, String traceId) {
        AnaDashboard dashboard = personalDashboard(context);
        if (dashboard == null) {
            throw new DomainException("DASHBOARD_NOT_FOUND", "个人首页尚未保存", HttpStatus.NOT_FOUND);
        }
        List<Map<String, Object>> snapshots = components(dashboard.getId()).stream()
                .map(component -> componentSnapshot(context, component)).toList();
        return previewSnapshot(context, dashboard.getId(), dashboard.getDraftRevision(), snapshots, traceId);
    }

    @Transactional(readOnly = true)
    public DashboardModels.RuntimeDashboard personalRuntime(AuthenticatedContext context, String traceId) {
        AnaDashboard dashboard = personalDashboard(context);
        if (dashboard == null) {
            return new DashboardModels.RuntimeDashboard(false, contextType(context), null, null, null,
                    null, null, null, List.of(), "个人首页尚未保存。");
        }
        List<Map<String, Object>> snapshots = components(dashboard.getId()).stream()
                .map(component -> componentSnapshot(context, component)).toList();
        List<DashboardModels.RuntimeComponent> visible = snapshots.stream()
                .filter(component -> visible(context, readMapValue(component.get("displayConfig"))))
                .map(component -> runtimeComponent(context, component, traceId)).toList();
        return new DashboardModels.RuntimeDashboard(true, contextType(context), dashboard.getId(), null, null,
                dashboard.getName(), dashboard.getDescription(), dashboard.getUpdateTime(), visible,
                "个人首页由本人保存，数据按当前账号权限重新执行。");
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
        source.setCreateTime(now);
        source.setUpdateTime(now);
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
        source.setUpdateTime(LocalDateTime.now());
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
        snapshot.put("status", "PUBLISHED");
        snapshot.put("versionNumber", versionNumber);
        snapshot.put("publishedAt", LocalDateTime.now());
        String json = writeJson(snapshot);
        AnaDataSourceVersion version = new AnaDataSourceVersion();
        version.setDataSourceId(sourceId);
        version.setScopeType(source.getScopeType());
        version.setScopeOwnerId(source.getScopeOwnerId());
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(source.getDraftRevision());
        version.setDefinitionHash(sha256(json));
        version.setSnapshotJson(json);
        version.setPublishedByAccountId(context.accountId());
        version.setPublishTime(LocalDateTime.now());
        sourceVersionService.insert(version);
        // 已发布数据只读取当前版本；继续编辑草稿时保留这个发布指针。
        source.setCurrentVersionId(version.getId());
        source.setStatus("PUBLISHED");
        source.setUpdateTime(LocalDateTime.now());
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
        String kind = publicDashboardKind(context, input.dashboardKind());
        dashboard.setDashboardKind(kind);
        dashboard.setModuleId("MODULE_HOME".equals(kind) ? requirePublishedModule(context, input.moduleId()).getId() : null);
        dashboard.setCode(input.code());
        dashboard.setName(input.name().strip());
        dashboard.setDescription(blankToNull(input.description()));
        dashboard.setDraftRevision(1);
        dashboard.setStatus("DRAFT");
        dashboard.setCreatedByAccountId(context.accountId());
        dashboard.setCreateTime(now);
        dashboard.setUpdateTime(now);
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
        String kind = publicDashboardKind(context, input.dashboardKind() == null
                ? dashboard.getDashboardKind() : input.dashboardKind());
        if (!kind.equals(dashboard.getDashboardKind())
                || ("MODULE_HOME".equals(kind) && !Objects.equals(dashboard.getModuleId(), input.moduleId()))) {
            throw invalid("DASHBOARD_HOME_BINDING_IMMUTABLE", "首页类型和所属模块不能通过编辑草稿变更");
        }
        if ("MODULE_HOME".equals(kind)) requirePublishedModule(context, dashboard.getModuleId());
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
        dashboard.setUpdateTime(LocalDateTime.now());
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
        snapshot.put("dashboardKind", dashboard.getDashboardKind());
        snapshot.put("moduleId", dashboard.getModuleId());
        snapshot.put("code", dashboard.getCode());
        snapshot.put("name", dashboard.getName());
        snapshot.put("description", dashboard.getDescription());
        snapshot.put("draftRevision", dashboard.getDraftRevision());
        snapshot.put("versionNumber", versionNumber);
        snapshot.put("components", componentSnapshots);
        String snapshotJson = writeJson(snapshot);
        AnaDashboardVersion version = new AnaDashboardVersion();
        version.setDashboardId(dashboardId);
        version.setScopeType(dashboard.getScopeType());
        version.setScopeOwnerId(dashboard.getScopeOwnerId());
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(dashboard.getDraftRevision());
        version.setSnapshotHash(sha256(snapshotJson));
        version.setSnapshotJson(snapshotJson);
        version.setPublishedByAccountId(context.accountId());
        version.setPublishTime(LocalDateTime.now());
        dashboardVersionService.insert(version);

        AnaDashboardPublication publication = publication(dashboardId);
        if (publication == null) {
            publication = new AnaDashboardPublication();
            publication.setDashboardId(dashboardId);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setUpdateTime(LocalDateTime.now());
            publication.setVersion(0);
            publicationService.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setUpdateTime(LocalDateTime.now());
            if (publicationService.updateById(publication) != 1) {
                throw conflict("DASHBOARD_PUBLICATION_CONFLICT", "仪表盘发布状态已变化，请刷新后重试");
            }
        }
        dashboard.setStatus("PUBLISHED");
        dashboard.setUpdateTime(LocalDateTime.now());
        if (dashboardService.updateById(dashboard) != 1) {
            throw conflict("DASHBOARD_VERSION_CONFLICT", "仪表盘已被其他用户修改，请刷新后重试");
        }
        audit(context, traceId, "DASHBOARD_PUBLISHED", "ANA_DASHBOARD_VERSION", version.getId(),
                Map.of("dashboardId", dashboardId, "versionNumber", versionNumber,
                        "draftRevision", dashboard.getDraftRevision(), "snapshotHash", version.getSnapshotHash(),
                        "componentCount", componentSnapshots.size()));
        return new DashboardModels.PublishResult(dashboardId, version.getId(), versionNumber,
                dashboard.getDraftRevision(), version.getSnapshotHash(), preview);
    }

    public DashboardModels.RuntimeDashboard runtime(AuthenticatedContext context, String traceId) {
        return runtime(context, null, traceId);
    }

    /** 模块首页绑定当前有效模块；系统首页只读取当前租户自己的发布版本。 */
    public DashboardModels.RuntimeDashboard runtime(
            AuthenticatedContext context, String moduleCode, String traceId) {
        AnaDashboard dashboard = null;
        AnaDashboardPublication publication = null;
        String homeKind = context.systemId() == null ? "PLATFORM_HOME" : "SYSTEM_HOME";
        Long moduleId = null;
        boolean legacyOnly = false;
        if (moduleCode != null && !moduleCode.isBlank()) {
            if (context.systemId() == null || !MODULE_CODE.matcher(moduleCode).matches()) {
                throw invalid("DASHBOARD_MODULE_INVALID", "请在系统内选择有效模块");
            }
            if (!permissionChecker.allows(context, "MODULE", moduleCode, "LIST")) {
                throw new DomainException("DASHBOARD_MODULE_FORBIDDEN", "当前账号没有模块列表权限", HttpStatus.FORBIDDEN);
            }
            RuntimeModuleConfiguration module = modulePublicationService.published(context, moduleCode);
            // 正式动作需 DASHBOARD 权限；V23 清单中的旧首页延续原 LIST 可见范围。
            boolean dashboardPermission = permissionChecker.allows(context, "MODULE", moduleCode, "DASHBOARD");
            JsonNode activeAction = null;
            for (JsonNode action : module.configuration().path("actions")) {
                if ("DASHBOARD".equals(action.path("code").asText())
                        && "ACTIVE".equals(action.path("status").asText())
                        && "MODULE_ENTRY".equals(action.path("location").asText())) {
                    activeAction = action;
                    break;
                }
            }
            if (activeAction == null) {
                throw new DomainException("DASHBOARD_MODULE_ACTION_NOT_PUBLISHED", "模块仪表盘入口未发布或已停用",
                        HttpStatus.CONFLICT);
            }
            if (!dashboardPermission && !activeAction.path("legacyDashboardCompatibility").asBoolean(false)) {
                throw new DomainException("DASHBOARD_MODULE_FORBIDDEN", "当前账号没有模块仪表盘权限", HttpStatus.FORBIDDEN);
            }
            moduleId = module.moduleId();
            legacyOnly = !dashboardPermission || activeAction.path("legacyDashboardSynthetic").asBoolean(false);
            homeKind = "MODULE_HOME";
        }
        for (AnaDashboard candidate : ownedDashboards(context)) {
            if (!homeKind.equals(candidate.getDashboardKind())) continue;
            if ("MODULE_HOME".equals(homeKind) && !Objects.equals(moduleId, candidate.getModuleId())) continue;
            if (legacyOnly && !legacyModuleDashboardCompatibility.permitsDashboard(
                    context.systemId(), context.tenantId(), moduleId, candidate.getId())) continue;
            AnaDashboardPublication current = publication(candidate.getId());
            if (current != null && (publication == null
                    || current.getUpdateTime().isAfter(publication.getUpdateTime()))) {
                dashboard = candidate;
                publication = current;
            }
        }
        if (dashboard == null) {
            return new DashboardModels.RuntimeDashboard(false, contextType(context), null, null, null,
                    null, null, null, List.of(),
                    "当前租户尚未发布此仪表盘，不显示其他租户的页面。");
        }
        AnaDashboardVersion version = dashboardVersionService.selectById(publication.getCurrentVersionId());
        if (version == null || !dashboard.getId().equals(version.getDashboardId())) {
            return new DashboardModels.RuntimeDashboard(false, contextType(context), dashboard.getId(), null, null,
                    dashboard.getName(), dashboard.getDescription(), null, List.of(),
                    "仪表盘发布指针无效，请联系管理员重新发布；页面未伪造任何统计值。");
        }
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        List<DashboardModels.RuntimeComponent> visible = publishedComponents(version).stream()
                .filter(component -> visible(context, readMapValue(component.get("displayConfig"))))
                .map(component -> runtimeComponent(context, component, traceId)).toList();
        return new DashboardModels.RuntimeDashboard(true, contextType(context), dashboard.getId(), version.getId(),
                version.getVersionNumber(), string(snapshot.get("name")), stringOrNull(snapshot.get("description")),
                version.getPublishTime(), visible,
                "仪表盘内容来自当前租户的不可变发布版本，并按当前上下文重新执行权限。");
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
        if (current == null || !canUseSource(context, current)) {
            LocalDateTime changedAt = current == null ? now : current.getUpdateTime();
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
            List<Map<String, Object>> items = new ArrayList<>(source.items());
            if ("RANKING".equals(type)) {
                // 排名按聚合值降序截取 Top，不能沿用明细读取顺序。
                items.sort(Comparator.comparing((Map<String, Object> item) -> numericValue(item.get("value")),
                        Comparator.nullsLast(Comparator.reverseOrder())));
                int topLimit = Math.max(1, Math.min(20,
                        intValue(readMapValue(component.get("queryParameters")).get("topLimit"), 10)));
                items = items.subList(0, Math.min(topLimit, items.size()));
            } else if ("CHART".equals(type)) {
                Map<String, Object> dimension = readMapValue(readMapValue(sourceSnapshot.get("definition"))
                        .get("dimension"));
                if ("TIME".equals(string(dimension.get("type")))) {
                    // 时间趋势按桶的自然时间顺序展示，避免受记录更新时间影响。
                    items.sort(Comparator.comparing(item -> string(item.get("id"))));
                }
            }
            return new DashboardModels.RuntimeComponent(key, type, title, "READY", source.value(),
                    items, source.metricDefinition(), layout, display, target.target(), target.available(),
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
        Map<String, Object> publishedDefinition = readMapValue(sourceSnapshot.get("definition"));
        LinkedHashMap<String, Object> definition = new LinkedHashMap<>(publishedDefinition);
        definition.putAll(queryParameters);
        // 组件可调整展示查询参数，但不能改写已发布数据源绑定的业务模块。
        if ("MODULE_RECORDS".equals(sourceType)) {
            definition.put("moduleCode", publishedDefinition.get("moduleCode"));
        }
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
            String dimensionType = stringOrNull(dimension.get("type"));
            String dimensionFieldType = stringOrNull(readMapValue(
                    readMapValue(definition.get("queryExplanation")).get("dimension")).get("fieldType"));
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
                        // 主模块分组下钻仍走普通记录列表的当前权限；关联模块分组不伪造主模块条件。
                        if (dimensionFieldCode != null
                                && (dimensionAlias == null || Objects.equals(primaryAlias, dimensionAlias))) {
                            if (group.key() == null) {
                                item.put("filter", Map.of("fieldCode", dimensionFieldCode, "operator", "EMPTY"));
                            } else if ("TIME".equals(dimensionType)) {
                                item.put("filters", timeGroupFilters(dimensionFieldCode, dimensionFieldType,
                                        string(group.key()), stringOrDefault(dimension.get("interval"), "DAY")));
                            } else {
                                item.put("filter", Map.of("fieldCode", dimensionFieldCode, "operator", "EQ",
                                        "value", group.key()));
                            }
                        }
                        return (Map<String, Object>) item;
                    }).toList();
            return new SourceResult(report.value(), items, report.metricDefinition());
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
        // 配置了权限规则却缺少任一要素时按不可见处理，避免错误配置放开组件。
        return resourceType != null && resourceCode != null && actionCode != null
                && permissionChecker.allows(context, resourceType, resourceCode, actionCode);
    }

    private DrillResult drill(AuthenticatedContext context, Map<String, Object> target) {
        if (target.isEmpty()) return new DrillResult(Map.of(), false);
        Map<String, Object> policy = readMapValue(target.get("permission"));
        if (!allowsPolicy(context, policy)) return new DrillResult(Map.of(), false);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(target);
        result.remove("permission");
        String moduleCode = stringOrNull(result.get("moduleCode"));
        if (moduleCode != null && context.systemId() != null) {
            if (!permissionChecker.allows(context, "MODULE", moduleCode, "LIST")) {
                return new DrillResult(Map.of(), false);
            }
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
            AnaDataSource source = requireUsableSource(context, component.getDataSourceId());
            AnaDataSourceVersion version = component.getDataSourceVersionId() == null ? null
                    : sourceVersionService.selectById(component.getDataSourceVersionId());
            if (version == null || !source.getId().equals(version.getDataSourceId())
                    || !Objects.equals(component.getSourceScopeType(), version.getScopeType())
                    || !Objects.equals(component.getSourceScopeOwnerId(), version.getScopeOwnerId())) {
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
            AnaDataSource source = input.dataSourceId() == null
                    ? null : requireUsableSource(context, input.dataSourceId());
            if (!"QUICK_ENTRY".equals(input.componentType()) && input.dataSourceId() == null) {
                throw invalid("DASHBOARD_COMPONENT_SOURCE_REQUIRED", "除快捷入口外的组件必须绑定数据源");
            }
            AnaDashboardComponent component = new AnaDashboardComponent();
            component.setDashboardId(dashboard.getId());
            component.setScopeType(dashboard.getScopeType());
            component.setScopeOwnerId(dashboard.getScopeOwnerId());
            component.setComponentKey(input.componentKey());
            component.setComponentType(input.componentType());
            component.setTitle(input.title().strip());
            component.setDataSourceId(input.dataSourceId());
            if (source != null) {
                if (source.getCurrentVersionId() == null) {
                    throw invalid("DASHBOARD_COMPONENT_SOURCE_UNPUBLISHED", "请先发布组件使用的数据源");
                }
                // 组件草稿固定所选数据源的当前发布版本，避免草稿改动影响已发布看板。
                component.setDataSourceVersionId(source.getCurrentVersionId());
                component.setSourceScopeType(source.getScopeType());
                component.setSourceScopeOwnerId(source.getScopeOwnerId());
            }
            component.setLayoutJson(writeJson(input.layout()));
            component.setQueryParameterJson(writeJson(input.queryParameters()));
            component.setDisplayConfigJson(writeJson(input.displayConfig()));
            component.setDrillTargetJson(input.drillTarget() == null ? null : writeJson(input.drillTarget()));
            component.setSortOrder(input.sortOrder());
            component.setCreateTime(LocalDateTime.now());
            component.setUpdateTime(LocalDateTime.now());
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
        int limit = intValue(definition.get("limit"), 5);
        if (limit < 1 || limit > 20) {
            throw invalid("DASHBOARD_SOURCE_DEFINITION_INVALID", "组件列表数量必须在 1 到 20 之间");
        }
        return definition;
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

    /** 非主租户可选主租户已发布的数据源，实际查询仍使用当前租户和当前查看人的权限。 */
    private List<AnaDataSource> availableSources(AuthenticatedContext context) {
        List<AnaDataSource> sources = new ArrayList<>(ownedSources(context));
        Long mainTenantId = mainTenantId(context);
        if (mainTenantId != null && !mainTenantId.equals(context.tenantId())) {
            sources.addAll(sourceService.selectList(Wrappers.<AnaDataSource>lambdaQuery()
                            .eq(AnaDataSource::getContextType, "SYSTEM")
                            .eq(AnaDataSource::getPlatformId, context.platformId())
                            .eq(AnaDataSource::getSystemId, context.systemId())
                            .eq(AnaDataSource::getOwnerTenantId, mainTenantId)
                            .isNotNull(AnaDataSource::getCurrentVersionId)).stream()
                    .filter(source -> personalSourceAllowed(context, source)).toList());
        }
        return sources;
    }

    private Long mainTenantId(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null) return null;
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId())
                        .eq(SystemTenant::getMain, true)
                        .eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().map(SystemTenant::getId).findFirst().orElse(null);
    }

    private List<Map<String, Object>> publishedComponents(AnaDashboardVersion version) {
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        return objectMapper.convertValue(snapshot.getOrDefault("components", List.of()), MAP_LIST_TYPE);
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

    private AnaDashboard personalDashboard(AuthenticatedContext context) {
        return ownedDashboards(context).stream()
                .filter(item -> "PERSONAL".equals(item.getDashboardKind()))
                .filter(item -> Objects.equals(context.accountId(), item.getOwnerAccountId()))
                .findFirst().orElse(null);
    }

    /** 个人首页只使用当前已发布版本，并在保存及目录中复核当前账号的来源权限。 */
    private boolean personalSourceAllowed(AuthenticatedContext context, AnaDataSource source) {
        if (source.getCurrentVersionId() == null) return false;
        AnaDataSourceVersion version = sourceVersionService.selectById(source.getCurrentVersionId());
        if (version == null || !Objects.equals(source.getId(), version.getDataSourceId())
                || !Objects.equals(source.getScopeType(), version.getScopeType())
                || !Objects.equals(source.getScopeOwnerId(), version.getScopeOwnerId())) return false;
        Map<String, Object> published = readMap(version.getSnapshotJson());
        if (!allowsPolicy(context, readMapValue(published.get("permissionPolicy")))) return false;
        String sourceType = string(published.get("sourceType"));
        if (!OPEN_SOURCE_TYPES.contains(sourceType)) return false;
        Map<String, Object> definition = readMapValue(published.get("definition"));
        if ("MODULE_RECORDS".equals(sourceType)) {
            String moduleCode = stringOrNull(definition.get("moduleCode"));
            return context.systemId() != null && moduleCode != null
                    && permissionChecker.allows(context, "MODULE", moduleCode, "LIST");
        }
        if ("MODULE_REPORT".equals(sourceType)) {
            Object modules = definition.get("modules");
            if (context.systemId() == null || !(modules instanceof List<?> list) || list.isEmpty()) return false;
            for (Object item : list) {
                String moduleCode = stringOrNull(readMapValue(item).get("moduleCode"));
                if (moduleCode == null || !permissionChecker.allows(context, "MODULE", moduleCode, "LIST")) {
                    return false;
                }
            }
        }
        return true;
    }

    private String publicDashboardKind(AuthenticatedContext context, String requested) {
        String kind = requested == null || requested.isBlank()
                ? context.systemId() == null ? "PLATFORM_HOME" : "SYSTEM_HOME" : requested;
        if (context.systemId() == null && "PLATFORM_HOME".equals(kind)) return kind;
        if (context.systemId() != null && ("SYSTEM_HOME".equals(kind) || "MODULE_HOME".equals(kind))) return kind;
        throw invalid("DASHBOARD_HOME_KIND_INVALID", "当前上下文不支持该公共首页类型");
    }

    /** 模块首页只绑定当前系统已发布的真实模块，禁止借用跨模块 ID。 */
    private ConfiguredModule requirePublishedModule(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = moduleId == null ? null : moduleService.selectById(moduleId);
        if (module == null || context.systemId() == null || !context.systemId().equals(module.getSystemId())
                || !"ACTIVE".equals(module.getStatus())) {
            throw invalid("DASHBOARD_MODULE_INVALID", "模块首页必须选择当前系统的有效模块");
        }
        RuntimeModuleConfiguration published = modulePublicationService.published(context, module.getCode());
        if (!module.getId().equals(published.moduleId())) {
            throw invalid("DASHBOARD_MODULE_INVALID", "模块首页必须绑定当前租户实际使用的已发布模块");
        }
        return module;
    }

    private AnaDataSource requireSource(AuthenticatedContext context, Long sourceId) {
        AnaDataSource source = sourceService.selectById(sourceId);
        if (source == null || !owned(context, source)) {
            throw new DomainException("DASHBOARD_DATA_SOURCE_NOT_FOUND", "数据源不存在或不属于当前上下文",
                    HttpStatus.NOT_FOUND);
        }
        return source;
    }

    private AnaDataSource requireUsableSource(AuthenticatedContext context, Long sourceId) {
        AnaDataSource source = sourceService.selectById(sourceId);
        if (source == null || !canUseSource(context, source)) {
            throw new DomainException("DASHBOARD_DATA_SOURCE_NOT_FOUND", "数据源不存在或当前租户无权使用",
                    HttpStatus.NOT_FOUND);
        }
        return source;
    }

    private boolean canUseSource(AuthenticatedContext context, AnaDataSource source) {
        if (owned(context, source)) return true;
        Long mainTenantId = mainTenantId(context);
        return mainTenantId != null && !mainTenantId.equals(context.tenantId())
                && "SYSTEM".equals(source.getContextType())
                && Objects.equals(context.platformId(), source.getPlatformId())
                && Objects.equals(context.systemId(), source.getSystemId())
                && Objects.equals(mainTenantId, source.getOwnerTenantId())
                && personalSourceAllowed(context, source);
    }

    private AnaDashboard requireDashboard(AuthenticatedContext context, Long dashboardId) {
        AnaDashboard dashboard = dashboardService.selectById(dashboardId);
        if (dashboard == null || !owned(context, dashboard)
                || "PERSONAL".equals(dashboard.getDashboardKind())) {
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
        // 归属层级与运行上下文分开保存，编码唯一性和数据范围按归属层级判断。
        source.setScopeType(context.systemId() == null ? "PLATFORM"
                : context.tenantId() == null ? "SYSTEM" : "TENANT");
        source.setScopeOwnerId(context.systemId() == null ? context.platformId()
                : context.tenantId() == null ? context.systemId() : context.tenantId());
    }

    private void bindContext(AnaDashboard dashboard, AuthenticatedContext context) {
        dashboard.setContextType(contextType(context));
        dashboard.setPlatformId(context.platformId());
        dashboard.setSystemId(context.systemId());
        dashboard.setOwnerTenantId(context.systemId() == null ? null : context.tenantId());
        dashboard.setScopeType(context.systemId() == null ? "PLATFORM"
                : context.tenantId() == null ? "SYSTEM" : "TENANT");
        dashboard.setScopeOwnerId(context.systemId() == null ? context.platformId()
                : context.tenantId() == null ? context.systemId() : context.tenantId());
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

    private DashboardModels.DataSourceView sourceView(AuthenticatedContext context, AnaDataSource source) {
        if (owned(context, source)) return sourceView(source);
        AnaDataSourceVersion version = sourceVersionService.selectById(source.getCurrentVersionId());
        Map<String, Object> published = readMap(version.getSnapshotJson());
        String sourceType = string(published.get("sourceType"));
        // 继承目录只暴露不可变发布版，不能把主租户正在修改的草稿当作可运行配置。
        return new DashboardModels.DataSourceView(source.getId(), source.getContextType(), source.getCode(),
                string(published.get("name")), sourceType, version.getDraftRevision(),
                readMapValue(published.get("definition")), readMapValue(published.get("permissionPolicy")),
                "PUBLISHED", source.getVersion(), version.getPublishTime(), true, sourceBoundary(sourceType),
                List.of(new DashboardModels.DataSourceVersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getDefinitionHash(), version.getPublishTime())));
    }

    private DashboardModels.DataSourceView sourceView(AnaDataSource source) {
        List<DashboardModels.DataSourceVersionView> versions = sourceVersionService.selectList(
                        Wrappers.<AnaDataSourceVersion>lambdaQuery()
                                .eq(AnaDataSourceVersion::getDataSourceId, source.getId())
                                .orderByDesc(AnaDataSourceVersion::getVersionNumber)).stream()
                .map(version -> new DashboardModels.DataSourceVersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getDefinitionHash(), version.getPublishTime())).toList();
        return new DashboardModels.DataSourceView(source.getId(), source.getContextType(), source.getCode(),
                source.getName(), source.getSourceType(), source.getDraftRevision(),
                readMap(source.getDefinitionJson()), readMap(source.getPermissionPolicyJson()), source.getStatus(),
                source.getVersion(), source.getUpdateTime(), false, sourceBoundary(source.getSourceType()), versions);
    }

    private DashboardModels.DataSourceBoundary sourceBoundary(String sourceType) {
        return switch (sourceType) {
            case "MODULE_RECORDS", "MODULE_REPORT" -> new DashboardModels.DataSourceBoundary(
                    "SYSTEM_MODULE", "CURRENT_SYSTEM", "PUBLISHED_BUSINESS_SCHEMA",
                    "CURRENT_USER_DATA_SCOPE", true,
                    "查询使用已发布模块字段，并在每次执行时重新应用当前用户权限和数据范围。");
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
                        version.getDraftRevision(), version.getSnapshotHash(), version.getPublishTime(),
                        publication != null && version.getId().equals(publication.getCurrentVersionId()))).toList();
        List<DashboardModels.ComponentView> components = components(dashboard.getId()).stream()
                .map(component -> new DashboardModels.ComponentView(component.getId(), component.getComponentKey(),
                        component.getComponentType(), component.getTitle(), component.getDataSourceId(),
                        readMap(component.getLayoutJson()), readMap(component.getQueryParameterJson()),
                        readMap(component.getDisplayConfigJson()), readMap(component.getDrillTargetJson()),
                        component.getSortOrder(), component.getVersion())).toList();
        return new DashboardModels.DashboardView(dashboard.getId(), dashboard.getContextType(), dashboard.getCode(),
                dashboard.getName(), dashboard.getDescription(), dashboard.getDraftRevision(), dashboard.getStatus(),
                dashboard.getVersion(), dashboard.getUpdateTime(), components, versions,
                dashboard.getDashboardKind(), dashboard.getModuleId());
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

    private BigDecimal numericValue(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(string(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 时间分组下钻以半开区间过滤业务记录，日期和日期时间沿用各自的列表字段类型。 */
    private List<Map<String, Object>> timeGroupFilters(
            String fieldCode, String fieldType, String key, String interval) {
        LocalDate start = switch (interval) {
            case "YEAR" -> LocalDate.of(Integer.parseInt(key), 1, 1);
            case "QUARTER" -> LocalDate.of(Integer.parseInt(key.substring(0, 4)),
                    (Integer.parseInt(key.substring(key.length() - 1)) - 1) * 3 + 1, 1);
            case "MONTH" -> YearMonth.parse(key).atDay(1);
            default -> LocalDate.parse(key);
        };
        LocalDate end = switch (interval) {
            case "YEAR" -> start.plusYears(1);
            case "QUARTER" -> start.plusMonths(3);
            case "MONTH" -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
        String from = "DATE".equals(fieldType) ? start.toString() : start.atStartOfDay().toString();
        String to = "DATE".equals(fieldType) ? end.toString() : end.atStartOfDay().toString();
        return List.of(Map.of("fieldCode", fieldCode, "operator", "GTE", "value", from),
                Map.of("fieldCode", fieldCode, "operator", "LT", "value", to));
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
