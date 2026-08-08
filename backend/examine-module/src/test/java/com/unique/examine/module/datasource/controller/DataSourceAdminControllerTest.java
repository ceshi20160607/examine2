package com.unique.examine.module.datasource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.datasource.api.DataSourceRequests;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceSchemaDiscoveryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceAdminControllerTest {
    @Test
    void schemaDiscoveryDelegatesOnlyTheScopedPersistedDraftIdentity() {
        var observed = new AtomicReference<Observed>();
        DataSourceSchemaDiscoveryUseCase discoveries =
                (actor, dataSourceId, expectedVersion) -> {
                    observed.set(new Observed(
                            actor.systemId(), actor.tenantId(),
                            actor.memberId(), dataSourceId, expectedVersion));
                    return new DataSourceSchemaDiscoveryUseCase.Result(
                            true, true, 200, 9, "SUCCESS",
                            "Schema discovery succeeded", expectedVersion,
                            List.of(new DataSourceSchemaDiscoveryUseCase.Field(
                                    "external_title", "external_title",
                                    "STRING", false, true, null)));
                };
        DataSourceConnectionCheckUseCase connections =
                (actor, dataSourceId, expectedVersion) -> {
                    throw new AssertionError(
                            "Connection check must not be called");
                };
        var controller = new DataSourceAdminController(
                null, connections, discoveries, new ObjectMapper());
        var request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-1");

        var response = controller.discoverSchema(
                10, 41, new DataSourceRequests.DiscoverSchema(7L),
                new TestSession(), request);

        assertThat(observed.get()).isEqualTo(new Observed(10, 20, 30, 41, 7));
        assertThat(response.data().checkedDraftVersion()).isEqualTo(7);
        assertThat(response.data().fields()).hasSize(1);
        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.traceId()).isEqualTo("trace-1");
    }

    @Test
    void draftRowsPreviewDelegatesOnlyTheScopedPersistedDraftIdentity() {
        var observed = new AtomicReference<Observed>();
        DataSourceDraftRowsPreviewUseCase previews =
                (actor, dataSourceId, expectedVersion) -> {
                    observed.set(new Observed(
                            actor.systemId(), actor.tenantId(),
                            actor.memberId(), dataSourceId, expectedVersion));
                    var values = new LinkedHashMap<String, Object>();
                    values.put("title", "External order");
                    values.put("note", null);
                    return new DataSourceDraftRowsPreviewUseCase.Result(
                            true, true, 200, 11, "SUCCESS",
                            "Draft preview succeeded", expectedVersion,
                            List.of(
                                    new DataSourceDraftRowsPreviewUseCase.Field(
                                            "title", "STRING"),
                                    new DataSourceDraftRowsPreviewUseCase.Field(
                                            "note", "STRING")),
                            List.of(
                                    new DataSourceDraftRowsPreviewUseCase.Row(
                                            1, values)));
                };
        DataSourceSchemaDiscoveryUseCase discoveries =
                (actor, dataSourceId, expectedVersion) -> {
                    throw new AssertionError(
                            "Schema discovery must not be called");
                };
        DataSourceConnectionCheckUseCase connections =
                (actor, dataSourceId, expectedVersion) -> {
                    throw new AssertionError(
                            "Connection check must not be called");
                };
        var controller = new DataSourceAdminController(
                null, connections, discoveries, previews,
                new ObjectMapper());
        var request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-2");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-2");

        var response = controller.previewDraftRows(
                10, 42, new DataSourceRequests.PreviewRows(8L),
                new TestSession(), request);

        assertThat(observed.get()).isEqualTo(new Observed(10, 20, 30, 42, 8));
        assertThat(response.data().checkedDraftVersion()).isEqualTo(8);
        assertThat(response.data().fields())
                .extracting(field -> field.fieldCode())
                .containsExactly("title", "note");
        assertThat(response.data().rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.rowIndex()).isEqualTo(1);
                    assertThat(row.values().keySet()).containsExactly(
                            "title", "note");
                    assertThat(row.values())
                            .containsEntry("title", "External order")
                            .containsEntry("note", null);
                });
        assertThat(response.requestId()).isEqualTo("request-2");
        assertThat(response.traceId()).isEqualTo("trace-2");
    }

    @Test
    void jdbcDraftRoutesSchemaDiscoveryOnlyToTheProtocolSpecificPort() {
        var root = ModuleDataSource.create(
                41, 10, 20, "jdbc_orders", 91, "JDBC orders", null,
                jdbcDraft(), Instant.parse("2026-08-05T00:00:00Z"));
        var repository = (DataSourceRepository) Proxy.newProxyInstance(
                DataSourceRepository.class.getClassLoader(),
                new Class<?>[]{DataSourceRepository.class},
                (proxy, method, arguments) -> {
                    if ("findById".equals(method.getName())) {
                        return (long) arguments[0] == root.systemId()
                                && (long) arguments[1] == root.tenantId()
                                && (long) arguments[2] == root.id()
                                ? Optional.of(root) : Optional.empty();
                    }
                    throw new AssertionError(
                            "Unexpected repository call: " + method.getName());
                });
        DataSourceModuleCatalog catalog =
                (systemId, tenantId, moduleId) -> Optional.empty();
        var sources = new DataSourceService(
                repository, catalog, Clock.systemUTC());
        DataSourceSchemaDiscoveryUseCase httpDiscoveries =
                (actor, dataSourceId, expectedVersion) -> {
                    throw new AssertionError("HTTP discovery must not run");
                };
        var observed = new AtomicReference<Observed>();
        JdbcTableDataSourceSchemaDiscoveryUseCase jdbcDiscoveries =
                (actor, dataSourceId, expectedVersion) -> {
                    observed.set(new Observed(
                            actor.systemId(), actor.tenantId(),
                            actor.memberId(), dataSourceId, expectedVersion));
                    return new JdbcTableDataSourceSchemaDiscoveryUseCase.Result(
                            true, true, 7, "SUCCESS", "Schema discovered",
                            expectedVersion,
                            List.of(new JdbcTableDataSourceSchemaDiscoveryUseCase.Field(
                                    "external_title", "externalTitle",
                                    "STRING", false, true, null)));
                };
        DataSourceConnectionCheckUseCase httpConnections =
                (actor, dataSourceId, expectedVersion) -> {
                    throw new AssertionError("HTTP check must not run");
                };
        DataSourceDraftRowsPreviewUseCase httpPreviews =
                (actor, dataSourceId, expectedVersion) -> {
                    throw new AssertionError("HTTP preview must not run");
                };
        JdbcTableDataSourceConnectionCheckUseCase jdbcConnections =
                (actor, dataSourceId, expectedVersion) -> null;
        JdbcTableDataSourceDraftRowsPreviewUseCase jdbcPreviews =
                (actor, dataSourceId, expectedVersion) -> null;
        var controller = new DataSourceAdminController(
                sources, httpConnections, httpDiscoveries, httpPreviews,
                jdbcConnections, jdbcDiscoveries, jdbcPreviews,
                new ObjectMapper());
        var request = new MockHttpServletRequest();

        var response = controller.discoverSchema(
                10, root.id(), new DataSourceRequests.DiscoverSchema(1L),
                new TestSession(), request);

        assertThat(observed.get())
                .isEqualTo(new Observed(10, 20, 30, 41, 1));
        assertThat(response.data().httpStatus()).isNull();
        assertThat(response.data().fields()).singleElement()
                .satisfies(field -> {
                    assertThat(field.sourceField()).isNull();
                    assertThat(field.sourceColumn())
                            .isEqualTo("external_title");
                });
    }

    @Test
    void exposesExplicitActiveAndPinnedJdbcRuntimeRoutes()
            throws Exception {
        var active = DataSourceRuntimeController.class.getMethod(
                "jdbcRows", long.class, String.class, Object.class,
                jakarta.servlet.http.HttpServletRequest.class);
        var pinned = DataSourceRuntimeController.class.getMethod(
                "pinnedJdbcRows", long.class, long.class, long.class,
                Object.class, jakarta.servlet.http.HttpServletRequest.class);

        assertThat(active.getAnnotation(GetMapping.class).value())
                .containsExactly("/{code}/jdbc-rows");
        assertThat(pinned.getAnnotation(GetMapping.class).value())
                .containsExactly(
                        "/{dataSourceId}/versions/{versionId}/jdbc-rows");
    }

    private static DataSourceDraft jdbcDraft() {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                new DataSourceDraft.JdbcTableConnection(
                        "mysql.internal", 3306, "operations", "orders",
                        "secret://mysql-user", "secret://mysql-password",
                        3, 5),
                List.of(new DataSourceDraft.JdbcTableFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.JdbcTableSourceType.STRING)));
    }

    private record Observed(
            long systemId,
            long tenantId,
            long memberId,
            long dataSourceId,
            long expectedVersion
    ) {
    }

    private record TestSession() implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return 2; }
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
        @Override public Long systemId() { return 10L; }
        @Override public Long tenantId() { return 20L; }
        @Override public Long memberId() { return 30L; }
        @Override public long permissionVersion() { return 1; }
        @Override public Set<String> permissions() {
            return Set.of("system.admin.access", "module.config.manage");
        }
    }
}
