package com.unique.examine.module.dashboard.adapter.datasource;

import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.activeRoot;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.repository;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.version;

class NativeDashboardSourceCatalogTest {
    @Test
    void exposesPinnedMultiModuleJoinCapabilitiesWithoutAnchorLookup() {
        var version = com.unique.examine.module.datasource
                .MultiModuleJoinTestFixtures.version();
        var catalog = new NativeDashboardSourceCatalog(
                com.unique.examine.module.datasource.MultiModuleJoinTestFixtures
                        .repository(com.unique.examine.module.datasource
                                .MultiModuleJoinTestFixtures.activeRoot(version),
                                version),
                (systemId, tenantId, moduleId) -> {
                    throw new AssertionError("Join pins must be self-contained");
                });

        assertThat(catalog.version(10, 20, 900, 9001)).get()
                .satisfies(source -> {
                    assertThat(source.moduleAvailable()).isTrue();
                    assertThat(source.readableOutputCount()).isEqualTo(2);
                    assertThat(source.fields())
                            .extracting(value -> value.code())
                            .containsExactly(
                                    "customers__id", "orders__amount");
                });
    }

    @Test
    void exposesActiveAndPinnedHttpVersionsThroughAnchorSchema() {
        var version = version();
        var module = new DataSourceModuleCatalog.PublishedModule(
                91, "orders_module", "Orders", "schema-1",
                List.of(field(11, "amount", "Amount", "NUMBER",
                        "NUMBER", true)));
        var catalog = new NativeDashboardSourceCatalog(
                repository(activeRoot(version), version),
                (systemId, tenantId, moduleId) -> java.util.Optional.of(module));

        assertThat(catalog.source(10, 20, 100)).isPresent();
        assertThat(catalog.version(10, 20, 100, version.id()))
                .get().satisfies(source -> {
                    assertThat(source.fields()).extracting(value -> value.code())
                            .containsExactly("amount");
                    assertThat(source.readableOutputCount()).isEqualTo(1);
                });
    }

    @Test
    void pinsIdentityDisplayAndEffectiveQueryTypeForPublishedOutputs() {
        var module = new DataSourceModuleCatalog.PublishedModule(
                10, "orders", "Orders", "501", List.of(
                field(101, "customerNumber", "Customer number", "REFERENCE",
                        "NUMBER", true),
                field(102, "calculatedTotal", "Calculated total", "CALCULATED",
                        "NUMBER", true),
                field(103, "createdAt", "Created at", "CREATED_AT",
                        "DATETIME", true),
                field(104, "hidden", "Hidden", "NUMBER", "NUMBER", false),
                field(105, "price", "Price", "MONEY", "MONEY", true),
                field(106, "notes", "Notes", "TEXTAREA", "TEXTAREA", true)));

        var fields = NativeDashboardSourceCatalog.sourceFields(List.of(
                new DataSourceDraft.OutputField("customerNumber"),
                new DataSourceDraft.OutputField("calculatedTotal"),
                new DataSourceDraft.OutputField("createdAt"),
                new DataSourceDraft.OutputField("hidden"),
                new DataSourceDraft.OutputField("price"),
                new DataSourceDraft.OutputField("notes")), module);

        assertThat(fields)
                .extracting(field -> field.logicalFieldId() + ":" + field.code()
                        + ":" + field.name() + ":" + field.type()
                        + ":" + field.queryType())
                .containsExactly(
                        "101:customerNumber:Customer number:REFERENCE:NUMBER",
                        "102:calculatedTotal:Calculated total:CALCULATED:NUMBER",
                        "103:createdAt:Created at:CREATED_AT:DATETIME",
                        "104:hidden:Hidden:NUMBER:NUMBER",
                        "105:price:Price:MONEY:MONEY",
                        "106:notes:Notes:TEXTAREA:TEXTAREA");
        assertThat(fields.get(0).numeric()).isTrue();
        assertThat(fields.get(0).groupable()).isTrue();
        assertThat(fields.get(1).numeric()).isTrue();
        assertThat(fields.get(2).temporal()).isTrue();
        assertThat(fields.get(3).readable()).isFalse();
        assertThat(fields.get(3).numeric()).isFalse();
        assertThat(fields.get(4).numeric()).isFalse();
        assertThat(fields.get(4).groupable()).isFalse();
        assertThat(fields.get(5).groupable()).isFalse();
    }

    private static DataSourceModuleCatalog.FieldCapability field(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean available
    ) {
        return new DataSourceModuleCatalog.FieldCapability(
                logicalFieldId, code, name, type, queryType, Set.of(),
                false, "DATETIME".equals(queryType), available);
    }

}
