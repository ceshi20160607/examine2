package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MultiModuleJoinPublicationTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 7);
    private static final Instant NOW =
            Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void negotiatesExactChildCapabilitiesAndPinsPublishedProjectionMetadata() {
        var repository = new MemoryRepository();
        repository.add(child(
                101, 1001, "customers", 11, "schema-11",
                "id", "ownerMemberId"));
        repository.add(child(
                202, 2002, "orders", 22, "schema-22",
                "customerId", "amount"));
        var service = new DataSourceService(
                repository, catalog(),
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        var root = service.create(
                ACTOR, "customer_orders", 11, "Customer orders", null,
                joinDraft());

        var check = service.check(ACTOR, root.id());
        var published = service.publish(ACTOR, root.id(), root.draftVersion());

        assertThat(check.publishable()).isTrue();
        assertThat(published.snapshot().multiModuleJoin().inputs())
                .extracting(DataSourceDraft.JoinInput::dataSourceVersionId)
                .containsExactly(1001L, 2002L);
        assertThat(published.snapshot().multiModuleJoin().projections())
                .allSatisfy(field -> assertThat(field.logicalFieldId())
                        .isPositive());
        assertThat(published.snapshot().multiModuleJoin().projections())
                .extracting(DataSourceDraft.JoinProjection::fieldCode)
                .containsExactly("customers__id", "orders__amount");
    }

    private static DataSourceDraft joinDraft() {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.MULTI_MODULE_JOIN,
                null, List.of(), null, List.of(),
                new DataSourceDraft.MultiModuleJoin(
                        List.of(
                                new DataSourceDraft.JoinInput(
                                        "customers", 101, 1001),
                                new DataSourceDraft.JoinInput(
                                        "orders", 202, 2002)),
                        List.of(new DataSourceDraft.JoinEdge(
                                "customers", "id", "orders", "customerId",
                                DataSourceDraft.JoinType.LEFT,
                                DataSourceDraft.JoinCardinality.ONE_TO_MANY)),
                        List.of(
                                new DataSourceDraft.JoinProjection(
                                        "customers", "id", "customers__id"),
                                new DataSourceDraft.JoinProjection(
                                        "orders", "amount", "orders__amount")),
                        DataSourceDraft.JoinFailureMode.ALLOW_PARTIAL_LEFT,
                        3, 50));
    }

    private static DataSourceModuleCatalog catalog() {
        var modules = Map.of(
                11L, module(11, "customers_module", "schema-11",
                        field(111, "id", "NUMBER"),
                        field(112, "ownerMemberId", "MEMBER")),
                22L, module(22, "orders_module", "schema-22",
                        field(221, "customerId", "NUMBER"),
                        field(222, "amount", "NUMBER")));
        return new DataSourceModuleCatalog() {
            @Override
            public Optional<PublishedModule> publishedModule(
                    long systemId, long tenantId, long moduleId
            ) {
                return systemId == 10 && tenantId == 20
                        ? Optional.ofNullable(modules.get(moduleId))
                        : Optional.empty();
            }
        };
    }

    private static DataSourceModuleCatalog.PublishedModule module(
            long id, String code, String schema,
            DataSourceModuleCatalog.FieldCapability... fields
    ) {
        return new DataSourceModuleCatalog.PublishedModule(
                id, code, code, schema, List.of(fields));
    }

    private static DataSourceModuleCatalog.FieldCapability field(
            long id, String code, String queryType
    ) {
        return new DataSourceModuleCatalog.FieldCapability(
                id, code, code, queryType, queryType,
                Set.of("EQ"), true, false, true);
    }

    private static Publication child(
            long id, long versionId, String code, long moduleId,
            String schemaVersionId, String... fields
    ) {
        var draft = new DataSourceDraft(
                java.util.Arrays.stream(fields)
                        .map(DataSourceDraft.OutputField::new).toList(),
                List.of(), null, null);
        var root = new ModuleDataSource(
                id, 10, 20, code, moduleId, code, null, draft, 1,
                versionId, 1, NOW, NOW, 2);
        var version = new DataSourceVersion(
                versionId, id, 10, 20, 1, code, moduleId,
                code + "_module", schemaVersionId, code, null, draft,
                "a".repeat(64), 7, NOW);
        return new Publication(root, version);
    }

    private record Publication(
            ModuleDataSource root,
            DataSourceVersion version
    ) {
    }

    private static final class MemoryRepository
            implements DataSourceRepository {
        private final Map<Long, ModuleDataSource> roots =
                new LinkedHashMap<>();
        private final Map<Long, DataSourceVersion> versions =
                new LinkedHashMap<>();
        private long nextRoot = 900;
        private long nextVersion = 9000;

        void add(Publication publication) {
            roots.put(publication.root().id(), publication.root());
            versions.put(publication.version().id(), publication.version());
        }

        @Override public long nextDataSourceId() { return nextRoot++; }
        @Override public long nextVersionId() { return nextVersion++; }
        @Override public Optional<ModuleDataSource> findById(
                long systemId, long tenantId, long dataSourceId) {
            return Optional.ofNullable(roots.get(dataSourceId)).filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId);
        }
        @Override public Optional<ModuleDataSource> findByCode(
                long systemId, long tenantId, String code) {
            return roots.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.code().equals(code)).findFirst();
        }
        @Override public List<ModuleDataSource> findAll(
                long systemId, long tenantId) {
            return roots.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId).toList();
        }
        @Override public ModuleDataSource insert(ModuleDataSource root) {
            roots.put(root.id(), root); return root;
        }
        @Override public ModuleDataSource saveDraft(
                ModuleDataSource expected, ModuleDataSource revised) {
            roots.put(revised.id(), revised); return revised;
        }
        @Override public DataSourceVersion publish(
                ModuleDataSource expected, ModuleDataSource activated,
                DataSourceVersion version) {
            roots.put(activated.id(), activated);
            versions.put(version.id(), version);
            return version;
        }
        @Override public Optional<DataSourceVersion> findActiveVersion(
                long systemId, long tenantId, long dataSourceId) {
            return findById(systemId, tenantId, dataSourceId)
                    .map(ModuleDataSource::activeVersionId).map(versions::get);
        }
        @Override public Optional<DataSourceVersion> findVersion(
                long systemId, long tenantId, long dataSourceId,
                int versionNumber) {
            return versions.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId
                            && value.versionNumber() == versionNumber)
                    .findFirst();
        }
        @Override public Optional<DataSourceVersion> findVersionById(
                long systemId, long tenantId, long dataSourceId,
                long versionId) {
            return Optional.ofNullable(versions.get(versionId)).filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId);
        }
        @Override public List<DataSourceVersion> findVersions(
                long systemId, long tenantId, long dataSourceId) {
            return versions.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId).toList();
        }
    }
}
