package com.unique.examine.module.dashboard.service;

import com.unique.examine.module.dashboard.api.DashboardMapping;
import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardScopeBinding;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.port.DashboardScopeBindingStore;
import com.unique.examine.module.dashboard.port.DashboardRepository;
import com.unique.examine.module.dashboard.port.DashboardSourceCatalog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardScopeServiceTest {
    private static final DashboardActor OWNER = new DashboardActor(10, 20, 7);
    private static final DashboardActor OTHER = new DashboardActor(10, 20, 8);
    private static final DashboardActor OTHER_TENANT =
            new DashboardActor(10, 21, 7);

    @Test
    void personalSavedViewIsOwnerIsolatedWhileModuleScopeIsShared() {
        var repository = new MemoryDashboardRepository();
        var dashboards = new DashboardService(
                repository, new DashboardSourceCatalog() {
                    @Override
                    public Optional<SourceRoot> source(
                            long systemId, long tenantId, long dataSourceId
                    ) { return Optional.empty(); }

                    @Override
                    public Optional<SourceVersion> version(
                            long systemId, long tenantId, long dataSourceId,
                            long dataSourceVersionId
                    ) { return Optional.empty(); }
                },
                Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"),
                        ZoneOffset.UTC));
        var store = new MemoryStore();
        var service = new DashboardScopeService(dashboards, store);
        var personal = service.create(
                OWNER, DashboardPlacement.PERSONAL_HOME,
                "saved-1", "myPipeline", "My pipeline", null);
        var module = service.create(OWNER, DashboardPlacement.MODULE_HOME,
                "orders", "ordersHome", "Orders", null);

        assertThat(service.personal(OWNER)).containsExactly(personal);
        assertThat(service.detail(OTHER, DashboardPlacement.MODULE_HOME,
                "orders")).isSameAs(module);
        assertThatThrownBy(() -> service.detail(
                OTHER, DashboardPlacement.PERSONAL_HOME, "saved-1"))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(((DashboardException) error)
                        .code()).isEqualTo("DASHBOARD_NOT_FOUND"));
        assertThatThrownBy(() -> service.owned(OTHER, personal.id()))
                .isInstanceOf(DashboardException.class);
    }

    @Test
    void publicDirectoryIsTenantIsolatedFilterableAndRecoversScopeKey() {
        var repository = new MemoryDashboardRepository();
        var dashboards = dashboards(repository);
        var store = new MemoryStore();
        var service = new DashboardScopeService(dashboards, store);
        var application = service.create(OWNER,
                DashboardPlacement.APPLICATION_HOME, "sales-app",
                "salesAppHome", "Sales app", null);
        var module = service.create(OWNER, DashboardPlacement.MODULE_HOME,
                "orders", "ordersModuleHome", "Orders", null);
        service.create(OTHER_TENANT, DashboardPlacement.MODULE_HOME,
                "private-module", "otherTenantModule", "Private", null);

        assertThat(service.publicDashboards(OWNER, null))
                .extracting(DashboardScopeService.ScopedDashboard::scopeKey)
                .containsExactly("sales-app", "orders");
        assertThat(service.publicDashboards(
                OWNER, DashboardPlacement.APPLICATION_HOME))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.scopeKey()).isEqualTo("sales-app");
                    assertThat(entry.dashboard()).isSameAs(application);
                    assertThat(DashboardMapping.dashboard(
                            entry.dashboard(), entry.scopeKey()).scopeKey())
                            .isEqualTo("sales-app");
                });
        assertThat(service.publicDashboards(
                OWNER, DashboardPlacement.MODULE_HOME))
                .singleElement().satisfies(entry ->
                        assertThat(entry.dashboard()).isSameAs(module));
        assertThat(service.publicDashboards(OTHER_TENANT, null))
                .extracting(DashboardScopeService.ScopedDashboard::scopeKey)
                .containsExactly("private-module");
        assertThatThrownBy(() -> service.publicDashboards(
                OWNER, DashboardPlacement.PERSONAL_HOME))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(((DashboardException) error)
                        .code()).isEqualTo("DASHBOARD_SCOPE_INVALID"));
    }

    private static DashboardService dashboards(
            MemoryDashboardRepository repository
    ) {
        return new DashboardService(repository, new DashboardSourceCatalog() {
            @Override
            public Optional<SourceRoot> source(
                    long systemId, long tenantId, long dataSourceId
            ) { return Optional.empty(); }

            @Override
            public Optional<SourceVersion> version(
                    long systemId, long tenantId, long dataSourceId,
                    long dataSourceVersionId
            ) { return Optional.empty(); }
        }, Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"),
                ZoneOffset.UTC));
    }

    @Test
    void transactionalServiceRemainsCglibProxyable() throws Exception {
        assertThat(Modifier.isFinal(
                DashboardScopeService.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(DashboardScopeService.class
                .getMethod("create", DashboardActor.class,
                        DashboardPlacement.class, String.class, String.class,
                        String.class, String.class).getModifiers())).isFalse();
    }

    private static final class MemoryStore
            implements DashboardScopeBindingStore {
        private final List<DashboardScopeBinding> values = new ArrayList<>();

        @Override
        public void bind(DashboardScopeBinding binding) {
            values.add(binding);
        }

        @Override
        public Optional<DashboardScopeBinding> find(
                long systemId, long tenantId, DashboardPlacement placement,
                String scopeKey, long ownerMemberId
        ) {
            return values.stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.placement() == placement
                            && value.scopeKey().equals(scopeKey)
                            && value.ownerMemberId() == ownerMemberId)
                    .findFirst();
        }

        @Override
        public Optional<DashboardScopeBinding> findByDashboard(
                long systemId, long tenantId, long dashboardId
        ) {
            return values.stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dashboardId() == dashboardId)
                    .findFirst();
        }

        @Override
        public List<DashboardScopeBinding> findPublic(
                long systemId, long tenantId
        ) {
            return values.stream().filter(value ->
                            value.systemId() == systemId
                                    && value.tenantId() == tenantId
                                    && (value.placement()
                                    == DashboardPlacement.APPLICATION_HOME
                                    || value.placement()
                                    == DashboardPlacement.MODULE_HOME)
                                    && value.ownerMemberId() == 0)
                    .toList();
        }

        @Override
        public List<DashboardScopeBinding> findPersonal(
                long systemId, long tenantId, long ownerMemberId
        ) {
            return values.stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.placement()
                            == DashboardPlacement.PERSONAL_HOME
                            && value.ownerMemberId() == ownerMemberId)
                    .toList();
        }
    }

    private static final class MemoryDashboardRepository
            implements DashboardRepository {
        private final Map<Long, SystemDashboard> roots = new LinkedHashMap<>();
        private long nextId = 100;

        @Override public long nextDashboardId() { return ++nextId; }
        @Override public long nextVersionId() { return ++nextId; }
        @Override public long nextVersionWidgetId() { return ++nextId; }

        @Override
        public Optional<SystemDashboard> findById(
                long systemId, long tenantId, long dashboardId
        ) {
            return Optional.ofNullable(roots.get(dashboardId)).filter(value ->
                    value.systemId() == systemId && value.tenantId() == tenantId);
        }

        @Override
        public Optional<SystemDashboard> findByCode(
                long systemId, long tenantId, String code
        ) {
            return roots.values().stream().filter(value ->
                    value.systemId() == systemId && value.tenantId() == tenantId
                            && value.code().equals(code)).findFirst();
        }

        @Override
        public Optional<SystemDashboard> findByPlacement(
                long systemId, long tenantId, DashboardPlacement placement
        ) {
            return roots.values().stream().filter(value ->
                    value.systemId() == systemId && value.tenantId() == tenantId
                            && value.placement() == placement).findFirst();
        }

        @Override
        public List<SystemDashboard> findAll(long systemId, long tenantId) {
            return roots.values().stream().filter(value ->
                    value.systemId() == systemId && value.tenantId() == tenantId)
                    .toList();
        }

        @Override
        public SystemDashboard insert(SystemDashboard root) {
            roots.put(root.id(), root);
            return root;
        }

        @Override
        public SystemDashboard saveDraft(
                SystemDashboard expected, SystemDashboard revised
        ) { throw unsupported(); }

        @Override
        public com.unique.examine.module.dashboard.domain.DashboardVersion publish(
                SystemDashboard expected, SystemDashboard activated,
                com.unique.examine.module.dashboard.domain.DashboardVersion version
        ) { throw unsupported(); }

        @Override
        public Optional<com.unique.examine.module.dashboard.domain.DashboardVersion>
        findActiveVersion(long systemId, long tenantId, long dashboardId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.unique.examine.module.dashboard.domain.DashboardVersion>
        findVersion(long systemId, long tenantId, long dashboardId,
                    int versionNumber) { return Optional.empty(); }

        @Override
        public List<com.unique.examine.module.dashboard.domain.DashboardVersion>
        findVersions(long systemId, long tenantId, long dashboardId) {
            return List.of();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }
}
