package com.unique.examine.module.datasource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.datasource.api.DataSourceMapping;
import com.unique.examine.module.datasource.api.DataSourceRequests;
import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.service.DataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.manage.security.ConfigSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/data-sources")
public class DataSourceAdminController {
    private final DataSourceService service;
    private final DataSourceConnectionCheckUseCase connectionChecks;
    private final DataSourceSchemaDiscoveryUseCase schemaDiscoveries;
    private final DataSourceDraftRowsPreviewUseCase draftRowsPreviews;
    private final JdbcTableDataSourceConnectionCheckUseCase
            jdbcConnectionChecks;
    private final JdbcTableDataSourceSchemaDiscoveryUseCase
            jdbcSchemaDiscoveries;
    private final JdbcTableDataSourceDraftRowsPreviewUseCase
            jdbcDraftRowsPreviews;
    private final ObjectMapper mapper;

    @Autowired
    public DataSourceAdminController(
            DataSourceService service,
            DataSourceConnectionCheckUseCase connectionChecks,
            DataSourceSchemaDiscoveryUseCase schemaDiscoveries,
            DataSourceDraftRowsPreviewUseCase draftRowsPreviews,
            ObjectProvider<JdbcTableDataSourceConnectionCheckUseCase>
                    jdbcConnectionChecks,
            ObjectProvider<JdbcTableDataSourceSchemaDiscoveryUseCase>
                    jdbcSchemaDiscoveries,
            ObjectProvider<JdbcTableDataSourceDraftRowsPreviewUseCase>
                    jdbcDraftRowsPreviews,
            ObjectMapper mapper
    ) {
        this(service, connectionChecks, schemaDiscoveries, draftRowsPreviews,
                jdbcConnectionChecks.getIfUnique(
                        DataSourceAdminController::unavailableJdbcConnectionCheck),
                jdbcSchemaDiscoveries.getIfUnique(
                        DataSourceAdminController::unavailableJdbcSchemaDiscovery),
                jdbcDraftRowsPreviews.getIfUnique(
                        DataSourceAdminController::unavailableJdbcRowsPreview),
                mapper);
    }

    public DataSourceAdminController(
            DataSourceService service,
            DataSourceConnectionCheckUseCase connectionChecks,
            DataSourceSchemaDiscoveryUseCase schemaDiscoveries,
            DataSourceDraftRowsPreviewUseCase draftRowsPreviews,
            JdbcTableDataSourceConnectionCheckUseCase jdbcConnectionChecks,
            JdbcTableDataSourceSchemaDiscoveryUseCase jdbcSchemaDiscoveries,
            JdbcTableDataSourceDraftRowsPreviewUseCase jdbcDraftRowsPreviews,
            ObjectMapper mapper
    ) {
        this.service = service;
        this.connectionChecks = connectionChecks;
        this.schemaDiscoveries = schemaDiscoveries;
        this.draftRowsPreviews = draftRowsPreviews;
        this.jdbcConnectionChecks = jdbcConnectionChecks;
        this.jdbcSchemaDiscoveries = jdbcSchemaDiscoveries;
        this.jdbcDraftRowsPreviews = jdbcDraftRowsPreviews;
        this.mapper = mapper;
    }

    /** Compatibility constructor for B105 direct controller fixtures. */
    public DataSourceAdminController(
            DataSourceService service,
            DataSourceConnectionCheckUseCase connectionChecks,
            DataSourceSchemaDiscoveryUseCase schemaDiscoveries,
            DataSourceDraftRowsPreviewUseCase draftRowsPreviews,
            ObjectMapper mapper
    ) {
        this(service, connectionChecks, schemaDiscoveries, draftRowsPreviews,
                unavailableJdbcConnectionCheck(),
                unavailableJdbcSchemaDiscovery(),
                unavailableJdbcRowsPreview(), mapper);
    }

    /** Compatibility constructor for B104 direct controller fixtures. */
    public DataSourceAdminController(
            DataSourceService service,
            DataSourceConnectionCheckUseCase connectionChecks,
            DataSourceSchemaDiscoveryUseCase schemaDiscoveries,
            ObjectMapper mapper
    ) {
        this(service, connectionChecks, schemaDiscoveries,
                unavailableDraftRowsPreview(), mapper);
    }

    /** Compatibility constructor for B103 direct controller fixtures. */
    public DataSourceAdminController(
            DataSourceService service,
            DataSourceConnectionCheckUseCase connectionChecks,
            ObjectMapper mapper
    ) {
        this(service, connectionChecks,
                (actor, dataSourceId, expectedVersion) -> {
                    throw new DataSourceException(
                            "DATA_SOURCE_SCHEMA_DISCOVERY_UNAVAILABLE",
                            "Data source schema discovery is unavailable");
                }, mapper);
    }

    /** Compatibility constructor for direct controller fixtures. */
    public DataSourceAdminController(
            DataSourceService service,
            ObjectMapper mapper
    ) {
        this(service, (actor, dataSourceId, expectedVersion) -> {
                    throw new DataSourceException(
                            "DATA_SOURCE_CONNECTION_CHECK_UNAVAILABLE",
                            "Data source connection check is unavailable");
                }, mapper);
    }

    private static DataSourceDraftRowsPreviewUseCase
    unavailableDraftRowsPreview() {
        return (actor, dataSourceId, expectedVersion) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_DRAFT_PREVIEW_UNAVAILABLE",
                    "Data source draft preview is unavailable");
        };
    }

    private static JdbcTableDataSourceConnectionCheckUseCase
    unavailableJdbcConnectionCheck() {
        return (actor, dataSourceId, expectedVersion) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_CONNECTION_CHECK_UNAVAILABLE",
                    "JDBC table connection check is unavailable");
        };
    }

    private static JdbcTableDataSourceSchemaDiscoveryUseCase
    unavailableJdbcSchemaDiscovery() {
        return (actor, dataSourceId, expectedVersion) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_SCHEMA_DISCOVERY_UNAVAILABLE",
                    "JDBC table schema discovery is unavailable");
        };
    }

    private static JdbcTableDataSourceDraftRowsPreviewUseCase
    unavailableJdbcRowsPreview() {
        return (actor, dataSourceId, expectedVersion) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_DRAFT_PREVIEW_UNAVAILABLE",
                    "JDBC table draft preview is unavailable");
        };
    }

    @GetMapping("/catalog")
    public ApiResponse<DataSourceViews.Catalog> catalog(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(DataSourceMapping.catalog(service.modules(actor)), request);
    }

    @GetMapping
    public ApiResponse<List<DataSourceViews.Source>> list(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(service.list(actor).stream()
                .map(source -> view(actor, source)).toList(), request);
    }

    @PostMapping
    public ApiResponse<DataSourceViews.Source> create(
            @PathVariable long systemId,
            @Valid @RequestBody DataSourceRequests.Create body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var source = service.create(
                actor, body.code(),
                DataSourceMapping.positiveId(body.moduleId(), "moduleId"),
                body.name(), body.description(),
                DataSourceMapping.draft(body));
        return ok(view(actor, source), request);
    }

    @GetMapping("/{dataSourceId}")
    public ApiResponse<DataSourceViews.Source> detail(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(view(actor, service.detail(actor, dataSourceId)), request);
    }

    @PutMapping("/{dataSourceId}/draft")
    public ApiResponse<DataSourceViews.Source> saveDraft(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @Valid @RequestBody DataSourceRequests.SaveDraft body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var current = service.detail(actor, dataSourceId);
        var source = service.saveDraft(
                actor, dataSourceId, body.expectedVersion(), body.name(),
                body.description(), DataSourceMapping.draft(
                        body, mapper, current.draft()));
        return ok(view(actor, source), request);
    }

    @PostMapping("/{dataSourceId}/draft:check")
    public ApiResponse<DataSourceViews.CheckResult> check(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(DataSourceMapping.check(
                service.check(actor(value, systemId), dataSourceId)), request);
    }

    @PostMapping("/{dataSourceId}/draft:connection-check")
    public ApiResponse<DataSourceViews.ConnectionCheckResult> checkConnection(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @Valid @RequestBody DataSourceRequests.CheckConnection body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        if (isJdbc(actor, dataSourceId)) {
            return ok(DataSourceMapping.connectionCheck(
                    jdbcConnectionChecks.check(
                            actor, dataSourceId, body.expectedVersion())),
                    request);
        }
        return ok(DataSourceMapping.connectionCheck(connectionChecks.check(
                actor, dataSourceId, body.expectedVersion())), request);
    }

    @PostMapping("/{dataSourceId}/draft:schema-discovery")
    public ApiResponse<DataSourceViews.SchemaDiscoveryResult> discoverSchema(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @Valid @RequestBody DataSourceRequests.DiscoverSchema body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        if (isJdbc(actor, dataSourceId)) {
            return ok(DataSourceMapping.schemaDiscovery(
                    jdbcSchemaDiscoveries.discover(
                            actor, dataSourceId, body.expectedVersion())),
                    request);
        }
        return ok(DataSourceMapping.schemaDiscovery(
                schemaDiscoveries.discover(
                        actor, dataSourceId, body.expectedVersion())),
                request);
    }

    @PostMapping("/{dataSourceId}/draft:rows-preview")
    public ApiResponse<DataSourceViews.DraftRowsPreviewResult> previewDraftRows(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @Valid @RequestBody DataSourceRequests.PreviewRows body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        if (isJdbc(actor, dataSourceId)) {
            return ok(DataSourceMapping.draftRowsPreview(
                    jdbcDraftRowsPreviews.preview(
                            actor, dataSourceId, body.expectedVersion())),
                    request);
        }
        return ok(DataSourceMapping.draftRowsPreview(
                draftRowsPreviews.preview(
                        actor, dataSourceId, body.expectedVersion())),
                request);
    }

    @PostMapping("/{dataSourceId}/draft:publish")
    public ApiResponse<DataSourceViews.PublishResult> publish(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @Valid @RequestBody DataSourceRequests.Publish body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var version = service.publish(
                actor, dataSourceId, body.expectedVersion());
        var source = service.detail(actor, dataSourceId);
        return ok(new DataSourceViews.PublishResult(
                DataSourceMapping.source(source, version.moduleCode(), mapper),
                DataSourceMapping.version(
                        version, source.activeVersionId(), mapper)), request);
    }

    @GetMapping("/{dataSourceId}/versions")
    public ApiResponse<List<DataSourceViews.Version>> versions(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var source = service.detail(actor, dataSourceId);
        return ok(service.versions(actor, dataSourceId).stream()
                .map(version -> DataSourceMapping.version(
                        version, source.activeVersionId(), mapper))
                .toList(), request);
    }

    @GetMapping("/{dataSourceId}/versions/{versionNumber}")
    public ApiResponse<DataSourceViews.Version> version(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @PathVariable int versionNumber,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var source = service.detail(actor, dataSourceId);
        return ok(DataSourceMapping.version(service.version(
                        actor, dataSourceId, versionNumber),
                source.activeVersionId(), mapper), request);
    }

    private DataSourceViews.Source view(
            DataSourceActor actor,
            ModuleDataSource source
    ) {
        String moduleCode = null;
        if (source.activeVersionId() != null) {
            moduleCode = service.active(actor, source.id()).version().moduleCode();
        }
        return DataSourceMapping.source(source, moduleCode, mapper);
    }

    private boolean isJdbc(DataSourceActor actor, long dataSourceId) {
        return service != null
                && service.detail(actor, dataSourceId).draft().sourceKind()
                == DataSourceDraft.SourceKind.JDBC_TABLE;
    }

    private static DataSourceActor actor(Object value, long systemId) {
        var session = ConfigSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DataSourceException(
                    "DATA_SOURCE_TENANT_REQUIRED",
                    "Select an active tenant before managing data sources");
        }
        return new DataSourceActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static <T> ApiResponse<T> ok(
            T data,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
