package com.unique.examine.module.report.adapter.datasource;

import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.activeRoot;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.repository;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.version;

class NativeReportSourceCatalogTest {
    @Test
    void exposesPinnedJoinCapabilitiesWithoutCurrentModuleLookup() {
        var version = com.unique.examine.module.datasource
                .MultiModuleJoinTestFixtures.version();
        var catalog = new NativeReportSourceCatalog(
                com.unique.examine.module.datasource.MultiModuleJoinTestFixtures
                        .repository(com.unique.examine.module.datasource
                                .MultiModuleJoinTestFixtures.activeRoot(version),
                                version),
                (systemId, tenantId, moduleId) -> {
                    throw new AssertionError("Join pins must be self-contained");
                });

        assertThat(catalog.version(10, 20, 900, 9001)).get()
                .satisfies(source -> assertThat(source.fields())
                        .extracting(value -> value.code())
                        .containsExactly("customers__id", "orders__amount"));
    }

    @Test
    void exposesActiveAndPinnedHttpVersionsThroughAnchorSchema() {
        var version = version();
        var module = new DataSourceModuleCatalog.PublishedModule(
                91, "orders_module", "Orders", "schema-1",
                List.of(field(11, "amount", "Amount", "NUMBER",
                        "NUMBER", true)));
        var catalog = new NativeReportSourceCatalog(
                repository(activeRoot(version), version),
                (systemId, tenantId, moduleId) -> java.util.Optional.of(module));

        assertThat(catalog.active(10, 20, 100)).get()
                .satisfies(source -> assertThat(source.fields())
                        .extracting(value -> value.code())
                        .containsExactly("amount"));
        assertThat(catalog.version(10, 20, 100, version.id())).isPresent();
    }

    @Test
    void preservesPublishedOutputOrderAndCurrentReadableCapability() {
        var module = new DataSourceModuleCatalog.PublishedModule(
                11L, "work_order", "Work order", "schema-7", List.of(
                field(101L, "amount", "Amount", "MONEY", "NUMBER", true),
                field(102L, "secret", "Secret", "SECRET", "STRING", false),
                field(103L, "status", "Status", "STATUS", "STATUS", true)));

        var fields = NativeReportSourceCatalog.sourceFields(List.of(
                new DataSourceDraft.OutputField("status"),
                new DataSourceDraft.OutputField("secret"),
                new DataSourceDraft.OutputField("amount")), module);

        assertThat(fields).extracting(value -> value.code())
                .containsExactly("status", "secret", "amount");
        assertThat(fields).extracting(value -> value.logicalFieldId())
                .containsExactly(103L, 102L, 101L);
        assertThat(fields).extracting(value -> value.queryType())
                .containsExactly("STATUS", "STRING", "NUMBER");
        assertThat(fields).extracting(value -> value.readable())
                .containsExactly(true, false, true);
    }

    @Test
    void ignoresFieldsThatAreNotPartOfThePinnedModuleSchema() {
        var module = new DataSourceModuleCatalog.PublishedModule(
                11L, "work_order", "Work order", "schema-7",
                List.of(field(101L, "amount", "Amount", "NUMBER", "NUMBER", true)));

        assertThat(NativeReportSourceCatalog.sourceFields(List.of(
                new DataSourceDraft.OutputField("removed"),
                new DataSourceDraft.OutputField("amount")), module))
                .extracting(value -> value.code())
                .containsExactly("amount");
    }

    private static DataSourceModuleCatalog.FieldCapability field(
            long id,
            String code,
            String name,
            String type,
            String queryType,
            boolean available
    ) {
        return new DataSourceModuleCatalog.FieldCapability(
                id, code, name, type, queryType, Set.of("EQ"),
                true, false, available);
    }

}
